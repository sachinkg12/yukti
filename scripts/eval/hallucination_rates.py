"""Compute hallucination rates by (variant, model) and by failure category.

Reports two independent rates per (variant, model):
  - verifier rate: share of instances that BOTH produced parseable claims AND
    had the production ClaimVerifier return passed=true. Schema-failed rows are
    NOT counted as passes (the Java side returns VerifierReport.allPass(0) for
    empty claim lists; that vacuous "pass" must not inflate the aggregate). The
    denominator is still the total instance count for the bucket.
  - taxonomy rate: heuristic failure category rate, used only for per category
    analysis.

Usage:
    python -m scripts.eval.hallucination_rates artifacts/bench/v2/explanation_eval_results.json
"""

from __future__ import annotations

import json
import sys
from collections import defaultdict

from .load_results import load


def aggregate(per_instance: list[dict]) -> dict[tuple[str, str], dict]:
    out: dict[tuple[str, str], dict] = defaultdict(lambda: {
        "instance_count": 0,
        "total_claims": 0,
        "hallucinated_claims": 0,
        "by_category": defaultdict(int),
        # Production verifier columns.
        "verifier_passed_instances": 0,
        "verifier_failing_claims": 0,
        "verifier_total_claims": 0,
        "schema_failed_instances": 0,
        "failures_by_gate": defaultdict(int),
        # Permissive-emission columns (co-primary metric).
        "shipped_claims": 0,
        "shipped_per_instance": [],  # per-instance values for downstream bootstrap
    })
    for row in per_instance:
        key = (row["variant"], row["modelId"])
        h = row["hallucinations"]
        out[key]["instance_count"] += 1
        out[key]["total_claims"] += h.get("totalClaims", 0)
        out[key]["hallucinated_claims"] += h.get("hallucinatedClaims", 0)
        for cat, count in (h.get("countByCategory") or {}).items():
            out[key]["by_category"][cat] += count
        # Verifier report fields (may be absent on legacy result files).
        #
        # Schema-failed rows did not produce parseable claims, so the production
        # verifier never ran. They MUST NOT be counted as passes: the Java side
        # returns VerifierReport.allPass(0) (passed=true) for empty claim lists,
        # which would otherwise let unparseable output inflate the aggregate.
        schema_failed = bool(row.get("schemaFailed"))
        if schema_failed:
            out[key]["schema_failed_instances"] += 1
        vr = row.get("verifierReport")
        if vr:
            if vr.get("passed") and not schema_failed:
                out[key]["verifier_passed_instances"] += 1
            out[key]["verifier_failing_claims"] += vr.get("failingClaims", 0)
            out[key]["verifier_total_claims"] += vr.get("totalClaims", 0)
            for gate, count in (vr.get("failuresByGate") or {}).items():
                out[key]["failures_by_gate"][gate] += count
            # Permissive-emission per-instance shipped count (totalClaims - failing).
            # Schema-failed rows contribute 0 by construction.
            shipped_this = 0 if schema_failed else max(
                0, vr.get("totalClaims", 0) - vr.get("failingClaims", 0))
            out[key]["shipped_claims"] += shipped_this
            out[key]["shipped_per_instance"].append(shipped_this)
    return out


def main(argv: list[str]) -> int:
    if len(argv) < 2:
        print(__doc__, file=sys.stderr)
        return 1
    results = load(argv[1])
    agg = aggregate(results.per_instance)
    rows = []
    for (variant, model), v in agg.items():
        rate = v["hallucinated_claims"] / v["total_claims"] if v["total_claims"] else 0.0
        # Instance-level strictness: share of (parseable AND passed) instances.
        verifier_pass_rate = (
            v["verifier_passed_instances"] / v["instance_count"]
            if v["instance_count"] else 0.0
        )
        # Claim-level density: failingClaims / totalClaims aggregated across
        # instances. Schema-failed instances contribute 0 to both numerator and
        # denominator (they have no parseable claims), so this is NOT directly
        # comparable to the Java EvaluationSummaryRow.meanVerifierFailureRate,
        # which is instance-level strictness with schema-failed treated as 1.0.
        verifier_claim_failure_rate = (
            v["verifier_failing_claims"] / v["verifier_total_claims"]
            if v["verifier_total_claims"] else 0.0
        )
        # Permissive-emission co-primary: bucket-level claim survival rate and
        # per-instance shipped counts (mean for reporting, list retained for
        # downstream bootstrap CIs).
        claim_survival_rate = (
            v["shipped_claims"] / v["verifier_total_claims"]
            if v["verifier_total_claims"] else 0.0
        )
        mean_shipped_per_instance = (
            sum(v["shipped_per_instance"]) / len(v["shipped_per_instance"])
            if v["shipped_per_instance"] else 0.0
        )
        rows.append({
            "variant": variant,
            "model": model,
            "instance_count": v["instance_count"],
            "total_claims": v["total_claims"],
            "hallucinated_claims": v["hallucinated_claims"],
            "taxonomy_rate": rate,
            "by_category": dict(v["by_category"]),
            "verifier_pass_rate": verifier_pass_rate,
            "verifier_claim_failure_rate": verifier_claim_failure_rate,
            "verifier_passed_instances": v["verifier_passed_instances"],
            "schema_failed_instances": v["schema_failed_instances"],
            "failures_by_gate": dict(v["failures_by_gate"]),
            # Co-primary deploy metric.
            "claim_survival_rate": claim_survival_rate,
            "shipped_claims": v["shipped_claims"],
            "mean_shipped_claims_per_instance": mean_shipped_per_instance,
        })
    print(json.dumps(rows, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
