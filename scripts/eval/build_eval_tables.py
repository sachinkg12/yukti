"""Build LaTeX table fragments from the explanation evaluation results.

Outputs (LaTeX fragments under generated/):
    tab_hallucination_rates.tex     -- heuristic taxonomy rate (analysis only)
    tab_verifier_pass_rates.tex     -- production ClaimVerifier pass rate
    tab_verifier_gate_breakdown.tex -- per-gate failure counts (gate 1-4)
    tab_fluency_summary.tex
    tab_judge_winrates.tex
    tab_failure_categories.tex

Usage:
    python -m scripts.eval.build_eval_tables \\
        artifacts/bench/v2/explanation_eval_results.json \\
        path/to/generated/
"""

from __future__ import annotations

import json
import os
import statistics
import sys
from collections import defaultdict
from pathlib import Path

from .load_results import load


def _fmt(v: float, dp: int = 2) -> str:
    return f"{v:.{dp}f}"


def write_hallucination(results, out_dir: Path) -> None:
    rows = []
    by_key = defaultdict(lambda: {"total": 0, "halluc": 0})
    for r in results.per_instance:
        key = (r["variant"], r["modelId"])
        h = r["hallucinations"]
        by_key[key]["total"] += h.get("totalClaims", 0)
        by_key[key]["halluc"] += h.get("hallucinatedClaims", 0)
    for (variant, model), v in by_key.items():
        rate = (v["halluc"] / v["total"] * 100) if v["total"] else 0.0
        rows.append(f"{model} & {variant} & {v['total']} & {v['halluc']} & {_fmt(rate)}\\% \\\\")
    (out_dir / "tab_hallucination_rates.tex").write_text("\n".join(rows) + "\n")


def write_fluency(results, out_dir: Path) -> None:
    rows = []
    by_key = defaultdict(lambda: defaultdict(list))
    for r in results.per_instance:
        key = (r["variant"], r["modelId"])
        for s in r.get("fluencyScores", []):
            by_key[key][s["metricId"]].append(s["value"])
    for (variant, model), metrics in by_key.items():
        fk = statistics.fmean(metrics.get("flesch_kincaid_grade", [0]))
        wc = statistics.fmean(metrics.get("word_count", [0]))
        ld = statistics.fmean(metrics.get("lexical_diversity", [0]))
        rows.append(f"{model} & {variant} & {_fmt(fk)} & {_fmt(wc, 1)} & {_fmt(ld, 3)} \\\\")
    (out_dir / "tab_fluency_summary.tex").write_text("\n".join(rows) + "\n")


def write_judge(results, out_dir: Path) -> None:
    counts = defaultdict(lambda: {"A": 0, "B": 0, "TIE": 0})
    for p in results.judged_pairs:
        key = (p["variantA"], p["variantB"])
        counts[key][p["vote"]["winner"]] += 1
    rows = []
    for (a, b), c in counts.items():
        total = max(c["A"] + c["B"] + c["TIE"], 1)
        rows.append(f"{a} vs {b} & {c['A']} & {c['B']} & {c['TIE']} & {_fmt(c['A'] / total * 100)}\\% \\\\")
    (out_dir / "tab_judge_winrates.tex").write_text("\n".join(rows) + "\n")


def write_categories(results, out_dir: Path) -> None:
    by_key = defaultdict(lambda: defaultdict(int))
    for r in results.per_instance:
        key = (r["variant"], r["modelId"])
        for cat, count in (r["hallucinations"].get("countByCategory") or {}).items():
            by_key[key][cat] += count
    rows = []
    for (variant, model), cats in by_key.items():
        for cat, count in cats.items():
            rows.append(f"{model} & {variant} & {cat} & {count} \\\\")
    (out_dir / "tab_failure_categories.tex").write_text("\n".join(rows) + "\n")


# Canonical reporting order for the four verifier gates.
_GATE_ORDER = ["EVIDENCE_EXISTENCE", "ENTITY_ALLOWLIST", "NUMBER_BINDING", "TYPE_RULES", "OTHER"]


def write_verifier_pass_rates(results, out_dir: Path) -> None:
    """Per (variant, model) production verifier pass rate.

    Pass rate is the share of instances that BOTH produced parseable claims AND
    had the ClaimVerifier return passed=true. Schema-failed rows are excluded
    from the passed count but kept in the denominator, so the rate reflects
    how often the deployed system would emit a verified explanation end-to-end.
    See hallucination_rates.aggregate for the same invariant.

    The claim_fail_rate column is the orthogonal claim-level density
    (failing_claims / total_claims) and is 0 for schema-failed rows because
    they contribute no parseable claims. Pair the two columns with care: the
    pass rate is instance-level strictness, claim_fail_rate is within-instance
    density.
    """
    by_key = defaultdict(lambda: {
        "instance_count": 0,
        "passed": 0,
        "failing_claims": 0,
        "total_claims": 0,
        "schema_failed": 0,
    })
    for r in results.per_instance:
        key = (r["variant"], r["modelId"])
        by_key[key]["instance_count"] += 1
        # Schema failures are not passes. See hallucination_rates.aggregate for
        # the same invariant on the Java side.
        schema_failed = bool(r.get("schemaFailed"))
        if schema_failed:
            by_key[key]["schema_failed"] += 1
        vr = r.get("verifierReport") or {}
        if vr.get("passed") and not schema_failed:
            by_key[key]["passed"] += 1
        by_key[key]["failing_claims"] += vr.get("failingClaims", 0)
        by_key[key]["total_claims"] += vr.get("totalClaims", 0)
    rows = []
    for (variant, model), v in by_key.items():
        pass_rate = (v["passed"] / v["instance_count"] * 100) if v["instance_count"] else 0.0
        claim_fail_rate = (
            v["failing_claims"] / v["total_claims"] * 100
            if v["total_claims"] else 0.0
        )
        rows.append(
            f"{model} & {variant} & {v['instance_count']} & {v['passed']} "
            f"& {_fmt(pass_rate)}\\% & {_fmt(claim_fail_rate)}\\% & {v['schema_failed']} \\\\"
        )
    (out_dir / "tab_verifier_pass_rates.tex").write_text("\n".join(rows) + "\n")


def write_verifier_gate_breakdown(results, out_dir: Path) -> None:
    """Per (variant, model) breakdown of failures across the four production gates."""
    by_key = defaultdict(lambda: defaultdict(int))
    for r in results.per_instance:
        key = (r["variant"], r["modelId"])
        vr = r.get("verifierReport") or {}
        for gate, count in (vr.get("failuresByGate") or {}).items():
            by_key[key][gate] += count
    rows = []
    for (variant, model), gates in by_key.items():
        # Render in canonical gate order for readability.
        cells = [str(gates.get(g, 0)) for g in _GATE_ORDER]
        rows.append(f"{model} & {variant} & " + " & ".join(cells) + " \\\\")
    (out_dir / "tab_verifier_gate_breakdown.tex").write_text("\n".join(rows) + "\n")


def main(argv: list[str]) -> int:
    if len(argv) < 3:
        print(__doc__, file=sys.stderr)
        return 1
    results = load(argv[1])
    out_dir = Path(argv[2])
    out_dir.mkdir(parents=True, exist_ok=True)
    write_hallucination(results, out_dir)
    write_fluency(results, out_dir)
    write_judge(results, out_dir)
    write_categories(results, out_dir)
    write_verifier_pass_rates(results, out_dir)
    write_verifier_gate_breakdown(results, out_dir)
    print(f"Wrote 6 LaTeX fragments under {out_dir}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
