#!/usr/bin/env python3
"""Recompute the camera-ready headline metrics from the released JSON logs.

Only Python's standard library is required.  The script writes deterministic
JSON/CSV files to results/derived and exits non-zero if any paper value drifts.
"""

from __future__ import annotations

import argparse
import csv
import json
from collections import Counter
from pathlib import Path


MODEL_LABELS = {
    "OPENAI_GPT4O_MINI": "mini",
    "TOGETHER_LLAMA_70B": "Llama",
    "ANTHROPIC_CLAUDE_SONNET": "Sonnet",
}

EXPECTED_GATE4 = {
    "mini": {
        "distinct_claims": 652,
        "message_total": 664,
        "comparison_messages": 371,
        "fee_justification_messages": 185,
        "cap_switch_messages": 21,
        "assumption_messages": 87,
        "cap_switch_distinct_claims": 9,
        "cap_hit_missing_claims": 6,
        "allocation_segment_missing_claims": 9,
        "both_missing_claims": 6,
        "additional_guarded_cap_hit_messages": 6,
    },
    "Llama": {
        "distinct_claims": 753,
        "message_total": 815,
        "comparison_messages": 394,
        "fee_justification_messages": 154,
        "cap_switch_messages": 136,
        "assumption_messages": 131,
        "cap_switch_distinct_claims": 74,
        "cap_hit_missing_claims": 68,
        "allocation_segment_missing_claims": 31,
        "both_missing_claims": 25,
        "additional_guarded_cap_hit_messages": 37,
    },
    "Sonnet": {
        "distinct_claims": 841,
        "message_total": 955,
        "comparison_messages": 324,
        "fee_justification_messages": 268,
        "cap_switch_messages": 220,
        "assumption_messages": 143,
        "cap_switch_distinct_claims": 106,
        "cap_hit_missing_claims": 89,
        "allocation_segment_missing_claims": 72,
        "both_missing_claims": 55,
        "additional_guarded_cap_hit_messages": 59,
    },
}


def load(path: Path) -> dict:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def cell_metrics(run_name: str, payload: dict) -> list[dict]:
    cells: dict[tuple[str, str], Counter] = {}
    for row in payload["perInstance"]:
        key = (MODEL_LABELS[row["modelId"]], row["variant"])
        bucket = cells.setdefault(key, Counter())
        report = row["verifierReport"]
        bucket["instances"] += 1
        bucket["total_claims"] += report["totalClaims"]
        bucket["failing_claims"] += report["failingClaims"]

    output = []
    for (model, variant), counts in sorted(cells.items()):
        passing = counts["total_claims"] - counts["failing_claims"]
        output.append(
            {
                "run": run_name,
                "model": model,
                "variant": variant,
                "instances": counts["instances"],
                "total_claims": counts["total_claims"],
                "failing_claims": counts["failing_claims"],
                "passing_claims": passing,
                "survival_percent": round(100 * passing / counts["total_claims"], 1),
                "shipped_per_instance": round(passing / counts["instances"], 2),
            }
        )
    return output


def gate4_reconciliation(payload: dict) -> list[dict]:
    grouped: dict[str, list[dict]] = {label: [] for label in MODEL_LABELS.values()}
    for row in payload["perInstance"]:
        if row["variant"] == "GROUNDED":
            grouped[MODEL_LABELS[row["modelId"]]].append(row)

    output = []
    for model in ("mini", "Llama", "Sonnet"):
        rows = grouped[model]
        messages = [
            message
            for row in rows
            for message in row["verifierReport"].get("allErrors", [])
            if message.startswith(
                ("COMPARISON ", "FEE_JUSTIFICATION ", "CAP_SWITCH ", "ASSUMPTION ")
            )
        ]
        message_types = Counter(message.split(" ", 1)[0] for message in messages)
        distinct = sum(
            row["verifierReport"].get("failuresByGate", {}).get("TYPE_RULES", 0)
            for row in rows
        )

        # COMPARISON, FEE_JUSTIFICATION, and ASSUMPTION each have one Gate-4
        # message per failing claim. CAP_SWITCH is the only multi-message type,
        # so subtracting the other types from the authoritative distinct total
        # recovers the distinct CAP_SWITCH union.
        cap_distinct = distinct - sum(
            message_types[name] for name in ("COMPARISON", "FEE_JUSTIFICATION", "ASSUMPTION")
        )
        cap_hit_missing = sum(
            message == "CAP_SWITCH must cite [CAP_HIT] evidence" for message in messages
        )
        guarded_cap_hit = sum(
            message == "CAP_SWITCH must cite CAP_HIT evidence" for message in messages
        )
        allocation_missing = sum(
            message == "CAP_SWITCH must cite ALLOCATION_SEGMENT evidence"
            for message in messages
        )
        both_missing = cap_hit_missing + allocation_missing - cap_distinct

        row = {
            "model": model,
            "distinct_claims": distinct,
            "message_total": len(messages),
            "comparison_messages": message_types["COMPARISON"],
            "fee_justification_messages": message_types["FEE_JUSTIFICATION"],
            "cap_switch_messages": message_types["CAP_SWITCH"],
            "assumption_messages": message_types["ASSUMPTION"],
            "cap_switch_distinct_claims": cap_distinct,
            "cap_hit_missing_claims": cap_hit_missing,
            "allocation_segment_missing_claims": allocation_missing,
            "both_missing_claims": both_missing,
            "additional_guarded_cap_hit_messages": guarded_cap_hit,
        }
        assert row | {"model": None} == EXPECTED_GATE4[model] | {"model": None}, (
            model,
            row,
            EXPECTED_GATE4[model],
        )
        output.append(row)
    return output


def write_csv(path: Path, rows: list[dict]) -> None:
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Recompute VerityGate camera-ready metrics from the Zenodo artifact."
    )
    parser.add_argument(
        "--artifact-root",
        type=Path,
        required=True,
        help="Extracted supplementary directory containing results/raw and results/derived.",
    )
    return parser.parse_args()


def main() -> None:
    root = parse_args().artifact_root.resolve()
    raw = root / "results" / "raw"
    derived = root / "results" / "derived"
    runs = {
        "r0": raw / "explanation_eval_results.large-r0.json",
        "r1": raw / "explanation_eval_results.large-r1.json",
    }
    missing = [str(path) for path in runs.values() if not path.is_file()]
    if missing:
        raise SystemExit("Missing required artifact file(s): " + ", ".join(missing))

    derived.mkdir(parents=True, exist_ok=True)
    payloads = {name: load(path) for name, path in runs.items()}
    cells = [row for name, payload in payloads.items() for row in cell_metrics(name, payload)]
    gate4 = gate4_reconciliation(payloads["r1"])

    # Camera-ready headline regression checks.
    keyed = {(row["run"], row["model"], row["variant"]): row for row in cells}
    expected_headlines = {
        ("r0", "mini", "GROUNDED"): (19.7, 1.60),
        ("r1", "mini", "GROUNDED"): (28.0, 1.74),
        ("r0", "Llama", "GROUNDED"): (40.1, 4.29),
        ("r1", "Llama", "GROUNDED"): (41.3, 3.58),
        ("r0", "Sonnet", "GROUNDED"): (52.1, 7.15),
        ("r1", "Sonnet", "GROUNDED"): (54.3, 6.67),
    }
    for key, expected in expected_headlines.items():
        observed = (keyed[key]["survival_percent"], keyed[key]["shipped_per_instance"])
        assert observed == expected, (key, observed, expected)

    reconciliation = {
        "source_runs": {name: str(path.relative_to(root)) for name, path in runs.items()},
        "interpretation": {
            "distinct_counts": "Sum of verifierReport.failuresByGate.TYPE_RULES.",
            "message_counts": "Gate-4 strings in verifierReport.allErrors.",
            "cap_switch_overlap": "|missing CAP_HIT| + |missing ALLOCATION_SEGMENT| - |union|.",
            "duplicate_path": "Bare CAP_HIT messages are additional outputs from the guarded two-type rule path.",
        },
        "cell_metrics": cells,
        "gate4": gate4,
    }
    with (derived / "reconciliation.json").open("w", encoding="utf-8") as handle:
        json.dump(reconciliation, handle, indent=2, sort_keys=True)
        handle.write("\n")
    write_csv(derived / "cell_metrics.csv", cells)
    write_csv(derived / "gate4_reconciliation.csv", gate4)
    print("Reconciliation passed; camera-ready values match the raw logs.")


if __name__ == "__main__":
    main()
