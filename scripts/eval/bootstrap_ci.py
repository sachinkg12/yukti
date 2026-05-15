"""Cluster-bootstrap confidence intervals for the explanation eval metrics.

Two sampling regimes are exposed:

  1. ``bootstrap_rate`` — resamples *instances* with replacement and recomputes
     the rate of interest on the resampled cluster. This is the correct unit of
     resampling for the two primary deployment metrics:
        - claim_survival_rate  = shipped_claims / generated_claims (claim-level
          density aggregated *within* the resampled instance set)
        - mean_shipped_claims_per_instance (instance-level mean)
     Resampling claims directly would treat claims as independent which they are
     not (they share a profile/goal/model and were emitted as one batch).

  2. ``bootstrap_paired_diff`` — for "repair vs no-repair" or "model A vs B"
     differences. Pairs are keyed by (profileId, goal, modelId or variant);
     missing pairs are dropped (reported). Resampling is at the pair level so
     the paired structure is preserved.

Both routines return point estimate + percentile CI + N effective. Differences
are usually the cleaner story (CI on Δ, not on each absolute).

Seed defaults to 42 (the same seed used by the bench profile generator) so any
reported table is reproducible.

Usage example:
    python -m scripts.eval.bootstrap_ci \\
        artifacts/bench/v2/explanation_eval_results.smoke-repair1-sonnet.2026-05-14.json \\
        --paired-against artifacts/bench/v2/explanation_eval_results.smoke-norepair.2026-05-14.json \\
        --variant GROUNDED
"""

from __future__ import annotations

import argparse
import json
import random
import sys
from dataclasses import dataclass
from typing import Callable


@dataclass
class CI:
    point: float
    lo: float
    hi: float
    n: int

    def fmt(self, pct: bool = False, dp: int = 3) -> str:
        scale = 100 if pct else 1
        suffix = "%" if pct else ""
        return f"{self.point*scale:.{dp}f}{suffix} [{self.lo*scale:.{dp}f}{suffix}, {self.hi*scale:.{dp}f}{suffix}]  (n={self.n})"


def _shipped(instance: dict) -> int:
    if instance.get("schemaFailed"):
        return 0
    vr = instance.get("verifierReport") or {}
    return max(0, vr.get("totalClaims", 0) - vr.get("failingClaims", 0))


def _generated(instance: dict) -> int:
    if instance.get("schemaFailed"):
        return 0
    vr = instance.get("verifierReport") or {}
    return vr.get("totalClaims", 0)


# Metric extractors. Each returns a per-instance pair (numerator, denominator)
# so the bootstrap can correctly aggregate across the resampled cluster.
METRIC_EXTRACTORS: dict[str, Callable[[dict], tuple[float, float]]] = {
    # Claim-level density: shipped / generated, aggregated across resampled
    # instances by summing num and denom (NOT mean of per-instance rates).
    "claim_survival_rate": lambda r: (_shipped(r), _generated(r)),
    # Instance-level mean: value per instance, denominator always 1.
    "mean_shipped_claims_per_instance": lambda r: (_shipped(r), 1.0),
    # Instance-level pass: parseable AND verifier passed.
    "verifier_pass_rate": lambda r: (
        1.0 if (r.get("verifierReport", {}).get("passed") and not r.get("schemaFailed")) else 0.0,
        1.0,
    ),
}


def _aggregate(pairs: list[tuple[float, float]]) -> float:
    num = sum(n for n, _ in pairs)
    den = sum(d for _, d in pairs)
    return num / den if den > 0 else 0.0


def bootstrap_rate(
    instances: list[dict],
    metric: str,
    iters: int = 10000,
    seed: int = 42,
    alpha: float = 0.05,
) -> CI:
    """Cluster bootstrap on instances. Returns CI at the (1-alpha) level."""
    if metric not in METRIC_EXTRACTORS:
        raise ValueError(f"unknown metric '{metric}'; choices: {sorted(METRIC_EXTRACTORS)}")
    extractor = METRIC_EXTRACTORS[metric]
    pairs = [extractor(r) for r in instances]
    n = len(pairs)
    if n == 0:
        return CI(0.0, 0.0, 0.0, 0)
    point = _aggregate(pairs)
    rng = random.Random(seed)
    samples: list[float] = []
    for _ in range(iters):
        resampled = [pairs[rng.randrange(n)] for _ in range(n)]
        samples.append(_aggregate(resampled))
    samples.sort()
    lo = samples[int(alpha / 2 * iters)]
    hi = samples[int((1 - alpha / 2) * iters) - 1]
    return CI(point, lo, hi, n)


PAIR_KEY_FIELDS = {
    "profileId": lambda r: r.get("profileId", ""),
    "goal":      lambda r: r.get("goal", ""),
    "modelId":   lambda r: r.get("modelId", ""),
    "variant":   lambda r: r.get("variant", ""),
}


def make_pair_key(fields: list[str]) -> Callable[[dict], tuple]:
    """Build a pair-key function from a list of instance field names.

    - For "repair=0 vs repair=1 on same model": ["profileId", "goal", "modelId"]
      (the default; ensures we only compare matched (profile, goal, model)
      instances across the two repair settings)
    - For "model A vs model B at same repair": ["profileId", "goal"]
      (model is the contrast, so drop it from the key)
    """
    if not fields:
        raise ValueError("at least one pair-key field required")
    extractors = [PAIR_KEY_FIELDS[f] for f in fields]
    return lambda r: tuple(fn(r) for fn in extractors)


def _pair_key(instance: dict) -> tuple[str, str, str]:
    return (instance.get("profileId", ""), instance.get("goal", ""),
            instance.get("modelId", ""))


def bootstrap_paired_diff(
    treatment: list[dict],
    control: list[dict],
    metric: str,
    iters: int = 10000,
    seed: int = 42,
    alpha: float = 0.05,
    key_fn: Callable[[dict], tuple] | None = None,
) -> tuple[CI, int]:
    """Paired bootstrap on (treatment - control) per matched instance.

    Returns (CI on the difference, number of matched pairs).
    """
    if metric not in METRIC_EXTRACTORS:
        raise ValueError(f"unknown metric '{metric}'; choices: {sorted(METRIC_EXTRACTORS)}")
    extractor = METRIC_EXTRACTORS[metric]
    keyer = key_fn or _pair_key
    by_key = {keyer(r): r for r in control}
    matched: list[tuple[tuple[float, float], tuple[float, float]]] = []
    for t in treatment:
        c = by_key.get(keyer(t))
        if c is None:
            continue
        matched.append((extractor(t), extractor(c)))
    n = len(matched)
    if n == 0:
        return CI(0.0, 0.0, 0.0, 0), 0
    t_point = _aggregate([m[0] for m in matched])
    c_point = _aggregate([m[1] for m in matched])
    point = t_point - c_point
    rng = random.Random(seed)
    samples: list[float] = []
    for _ in range(iters):
        idx = [rng.randrange(n) for _ in range(n)]
        t_pairs = [matched[i][0] for i in idx]
        c_pairs = [matched[i][1] for i in idx]
        samples.append(_aggregate(t_pairs) - _aggregate(c_pairs))
    samples.sort()
    lo = samples[int(alpha / 2 * iters)]
    hi = samples[int((1 - alpha / 2) * iters) - 1]
    return CI(point, lo, hi, n), n


def _filter(instances: list[dict], variant: str | None) -> list[dict]:
    if variant is None:
        return instances
    return [r for r in instances if r.get("variant") == variant]


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("results_file", help="primary explanation_eval_results.json")
    parser.add_argument("--paired-against", help="optional second results file; if set, report paired diff CIs")
    parser.add_argument("--variant", choices=["GROUNDED", "UNGROUNDED"], help="filter to one variant")
    parser.add_argument("--pair-by", default="profileId,goal,modelId",
                        help="comma-separated instance fields used to match treatment to control. "
                             "Use 'profileId,goal,modelId' for repair-vs-no-repair (same model); "
                             "use 'profileId,goal' for model-A-vs-model-B (model is the contrast). "
                             "Add 'variant' to keep grounded/ungrounded matched.")
    parser.add_argument("--iters", type=int, default=10000)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args(argv[1:])

    primary = json.load(open(args.results_file))["perInstance"]
    primary = _filter(primary, args.variant)

    print(f"# Absolute CIs ({args.results_file}, variant={args.variant or 'all'}, seed={args.seed}, iters={args.iters})")
    for metric in ["claim_survival_rate", "mean_shipped_claims_per_instance", "verifier_pass_rate"]:
        ci = bootstrap_rate(primary, metric, iters=args.iters, seed=args.seed)
        pct = metric.endswith("_rate")
        print(f"  {metric:38} {ci.fmt(pct=pct)}")

    if args.paired_against:
        control = json.load(open(args.paired_against))["perInstance"]
        control = _filter(control, args.variant)
        pair_fields = [f.strip() for f in args.pair_by.split(",") if f.strip()]
        key_fn = make_pair_key(pair_fields)
        print()
        print(f"# Paired differences vs control: {args.paired_against}")
        print(f"# pair-by: {pair_fields}")
        for metric in ["claim_survival_rate", "mean_shipped_claims_per_instance", "verifier_pass_rate"]:
            ci, n = bootstrap_paired_diff(primary, control, metric,
                                          iters=args.iters, seed=args.seed, key_fn=key_fn)
            pct = metric.endswith("_rate")
            print(f"  Δ {metric:36} {ci.fmt(pct=pct)}")
        # Note any unmatched instances (silent drops can bias the reported diff).
        treatment_keys = {key_fn(r) for r in primary}
        control_keys = {key_fn(r) for r in control}
        unmatched_in_treatment = treatment_keys - control_keys
        unmatched_in_control = control_keys - treatment_keys
        if unmatched_in_treatment or unmatched_in_control:
            print()
            print(f"  WARNING: {len(unmatched_in_treatment)} treatment instance(s) without control match, "
                  f"{len(unmatched_in_control)} control instance(s) without treatment match.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
