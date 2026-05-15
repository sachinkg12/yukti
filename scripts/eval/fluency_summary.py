"""Compute per (variant, model) mean fluency scores.

Usage:
    python -m scripts.eval.fluency_summary artifacts/bench/v2/explanation_eval_results.json
"""

from __future__ import annotations

import json
import statistics
import sys
from collections import defaultdict

from .load_results import load


def main(argv: list[str]) -> int:
    if len(argv) < 2:
        print(__doc__, file=sys.stderr)
        return 1
    results = load(argv[1])
    by_key: dict[tuple[str, str], dict[str, list[float]]] = defaultdict(lambda: defaultdict(list))
    for row in results.per_instance:
        key = (row["variant"], row["modelId"])
        for s in row.get("fluencyScores", []):
            by_key[key][s["metricId"]].append(s["value"])
    out = []
    for (variant, model), metrics in by_key.items():
        entry = {"variant": variant, "model": model}
        for metric_id, values in metrics.items():
            entry[f"{metric_id}_mean"] = statistics.fmean(values) if values else 0.0
            entry[f"{metric_id}_stdev"] = statistics.pstdev(values) if len(values) > 1 else 0.0
        out.append(entry)
    print(json.dumps(out, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
