"""Compute LLM judge preference win rates by variant pair.

Usage:
    python -m scripts.eval.judge_win_rates artifacts/bench/v2/explanation_eval_results.json
"""

from __future__ import annotations

import json
import sys
from collections import defaultdict

from .load_results import load


def main(argv: list[str]) -> int:
    if len(argv) < 2:
        print(__doc__, file=sys.stderr)
        return 1
    results = load(argv[1])
    counts: dict[tuple[str, str], dict[str, int]] = defaultdict(lambda: {"A": 0, "B": 0, "TIE": 0, "total": 0})
    for pair in results.judged_pairs:
        key = (pair["variantA"], pair["variantB"])
        winner = pair["vote"]["winner"]
        counts[key][winner] += 1
        counts[key]["total"] += 1
    out = []
    for (a, b), c in counts.items():
        total = c["total"] or 1
        out.append({
            "variantA": a,
            "variantB": b,
            "winsA": c["A"],
            "winsB": c["B"],
            "ties": c["TIE"],
            "total": c["total"],
            "winRateA": c["A"] / total,
            "winRateB": c["B"] / total,
        })
    print(json.dumps(out, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
