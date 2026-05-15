"""Bradley-Terry with position covariate via logistic regression.

Fits P(A wins) = sigmoid(variant_effect + position_effect) over all judge calls.

Each judge call is a Bernoulli trial. Features:
  - variant_effect: +1 if grounded was in position A, -1 if ungrounded was in A
                    (so positive coefficient means grounded preferred)
  - position_effect: always 1 (intercept-like for position-A advantage)

Outputs:
  - Variant coefficient (log-odds for grounded over ungrounded)
  - Position coefficient (log-odds for position A over position B)
  - Bootstrap 95% CI for each
  - Odds ratios

Usage:
    python -m scripts.eval.bradley_terry artifacts/bench/v2/explanation_eval_results.json
"""

from __future__ import annotations

import json
import math
import random
import sys


def sigmoid(x: float) -> float:
    if x >= 0:
        z = math.exp(-x)
        return 1.0 / (1.0 + z)
    z = math.exp(x)
    return z / (1.0 + z)


def neg_log_likelihood(beta: tuple[float, float], data: list[tuple[float, float, int]]) -> float:
    """Negative log likelihood. data is list of (variant_feature, position_feature, y) with y in {0,1}.

    y = 1 if A won the judge call, y = 0 otherwise. Ties dropped.
    """
    b_variant, b_position = beta
    total = 0.0
    for var_x, pos_x, y in data:
        logit = b_variant * var_x + b_position * pos_x
        p = sigmoid(logit)
        # clip
        if p < 1e-12:
            p = 1e-12
        if p > 1 - 1e-12:
            p = 1 - 1e-12
        total -= (y * math.log(p) + (1 - y) * math.log(1 - p))
    return total


def fit(data: list[tuple[float, float, int]]) -> tuple[float, float]:
    """Simple coordinate descent / gradient based fit. No external deps."""
    # Newton's method with finite differences is overkill. Use coordinate descent
    # with bisection-like search to keep dependencies at zero.
    b_variant, b_position = 0.0, 0.0
    lr = 0.05
    for _ in range(500):
        # numeric gradient
        eps = 1e-4
        loss = neg_log_likelihood((b_variant, b_position), data)
        g_var = (neg_log_likelihood((b_variant + eps, b_position), data) - loss) / eps
        g_pos = (neg_log_likelihood((b_variant, b_position + eps), data) - loss) / eps
        # gradient descent step
        b_variant -= lr * g_var / len(data)
        b_position -= lr * g_pos / len(data)
    return b_variant, b_position


def extract_observations(scores: list[dict]) -> list[tuple[float, float, int]]:
    """Convert matched pair scores into (variant_feature, position_feature, y) tuples per judge call.

    For each ordering:
      variant_feature: +1 if grounded shown as A, -1 if ungrounded shown as A
      position_feature: 1 (constant; coefficient captures A-advantage)
      y: 1 if A won, 0 if B won. Ties dropped.
    """
    obs = []
    for s in scores:
        for ord_key in ("orderingOne", "orderingTwo"):
            o = s[ord_key]
            var_x = 1.0 if o["positionA"] == "GROUNDED" else -1.0
            winner = o["vote"]["winner"]
            if winner == "TIE":
                continue
            y = 1 if winner == "A" else 0
            obs.append((var_x, 1.0, y))
    return obs


def bootstrap_ci(scores: list[dict], iters: int = 1000, seed: int = 42) -> dict:
    """Bootstrap CI for variant and position coefficients.

    Resamples where every judge call is a tie produce zero observations and
    cannot fit a model; those resamples are skipped. If too many are skipped
    the CI is reported as 'insufficient_data'.
    """
    rng = random.Random(seed)
    n = len(scores)
    var_coefs, pos_coefs = [], []
    skipped = 0
    for _ in range(iters):
        sample = [scores[rng.randrange(n)] for _ in range(n)]
        obs = extract_observations(sample)
        if not obs:
            skipped += 1
            continue
        bv, bp = fit(obs)
        var_coefs.append(bv)
        pos_coefs.append(bp)
    var_coefs.sort()
    pos_coefs.sort()
    effective = len(var_coefs)
    if effective < max(30, iters // 4):
        return {
            "variant_coef_ci95": "insufficient_data",
            "position_coef_ci95": "insufficient_data",
            "bootstrap_effective_iters": effective,
            "bootstrap_skipped_iters": skipped,
        }
    return {
        "variant_coef_ci95": (var_coefs[int(0.025 * effective)], var_coefs[int(0.975 * effective)]),
        "position_coef_ci95": (pos_coefs[int(0.025 * effective)], pos_coefs[int(0.975 * effective)]),
        "bootstrap_effective_iters": effective,
        "bootstrap_skipped_iters": skipped,
    }


def main(argv: list[str]) -> int:
    if len(argv) < 2:
        print(__doc__, file=sys.stderr)
        return 1
    with open(argv[1]) as f:
        data = json.load(f)
    scores = data.get("matchedPairScores", [])
    if not scores:
        print(json.dumps({"error": "no matchedPairScores in input"}, indent=2))
        return 1

    obs = extract_observations(scores)
    if not obs:
        print(json.dumps({"error": "all judgments were ties; cannot fit"}, indent=2))
        return 1

    b_variant, b_position = fit(obs)
    ci = bootstrap_ci(scores, iters=1000)

    out = {
        "n_judge_calls_after_dropping_ties": len(obs),
        "n_matched_pairs": len(scores),
        "variant_coef_log_odds": b_variant,
        "variant_odds_ratio_grounded_vs_ungrounded": math.exp(2 * b_variant),  # 2 because var_x is +1 or -1
        "position_coef_log_odds": b_position,
        "position_odds_ratio_A_vs_B": math.exp(b_position),
        **ci,
        "interpretation": {
            "variant": "positive coefficient means grounded is preferred after controlling for position",
            "position": "positive coefficient means slot A is preferred (position bias)",
        },
    }
    print(json.dumps(out, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
