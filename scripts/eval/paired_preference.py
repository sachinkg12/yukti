"""Counterbalanced paired preference analysis.

Reads matchedPairScores from the eval JSON and reports:
  - Mean grounded preference (per model and overall)
  - 95% bootstrap CI over matched pairs
  - Strict consistent grounded win rate (both orderings: grounded wins)
  - Strict consistent ungrounded win rate (both orderings: ungrounded wins)
  - Strict disagreement rate (one ordering: G wins, other ordering: U wins) - same as Java "inconsistent" flag
  - Any disagreement rate (the judge gave any two different verdicts across orderings; broader than strict)
  - Position A win rate (across all judge calls)
  - Position B win rate
  - Tie rate (judge said TIE)

Usage:
    python -m scripts.eval.paired_preference artifacts/bench/v2/explanation_eval_results.json
"""

from __future__ import annotations

import json
import random
import statistics
import sys
from collections import defaultdict


def bootstrap_ci(values: list[float], iters: int = 10_000, seed: int = 42) -> tuple[float, float]:
    """Percentile bootstrap CI for the mean."""
    if not values:
        return 0.0, 0.0
    rng = random.Random(seed)
    means = []
    n = len(values)
    for _ in range(iters):
        sample = [values[rng.randrange(n)] for _ in range(n)]
        means.append(sum(sample) / n)
    means.sort()
    return means[int(0.025 * iters)], means[int(0.975 * iters)]


def analyze(scores: list[dict]) -> dict:
    if not scores:
        return {"error": "no matched pair scores"}

    grounded_scores = [s["groundedPreferenceScore"] for s in scores]
    strict_g_wins = sum(1 for s in scores if s["groundedPreferenceScore"] == 1.0)
    strict_u_wins = sum(1 for s in scores if s["groundedPreferenceScore"] == 0.0)
    # Strict disagreement: one ordering says G wins, the other says U wins.
    # This is exactly the Java "inconsistent" flag.
    strict_disagreement = sum(1 for s in scores if s.get("inconsistent", False))

    a_wins = 0
    b_wins = 0
    ties = 0
    total_calls = 0
    any_disagreement = 0
    for s in scores:
        verdicts = []
        for slot in ("orderingOne", "orderingTwo"):
            o = s[slot]
            w = o["vote"]["winner"]
            verdicts.append(w)
            if w == "A":
                a_wins += 1
            elif w == "B":
                b_wins += 1
            else:
                ties += 1
            total_calls += 1
        # Any disagreement = the two orderings produced any pair of different verdicts.
        # Includes G-wins-once-tie-once and the strict G-vs-U flip case.
        if verdicts[0] != verdicts[1]:
            any_disagreement += 1

    mean = statistics.fmean(grounded_scores)
    lo, hi = bootstrap_ci(grounded_scores)

    return {
        "n_pairs": len(scores),
        "mean_grounded_preference": mean,
        "ci95_lo": lo,
        "ci95_hi": hi,
        "strict_consistent_grounded_win_rate": strict_g_wins / len(scores),
        "strict_consistent_ungrounded_win_rate": strict_u_wins / len(scores),
        "strict_disagreement_rate": strict_disagreement / len(scores),
        "any_disagreement_rate": any_disagreement / len(scores),
        "position_A_win_rate": a_wins / total_calls if total_calls else 0.0,
        "position_B_win_rate": b_wins / total_calls if total_calls else 0.0,
        "tie_rate": ties / total_calls if total_calls else 0.0,
    }


def analyze_by_model(scores: list[dict]) -> dict[str, dict]:
    by_model: dict[str, list[dict]] = defaultdict(list)
    for s in scores:
        by_model[s["modelId"]].append(s)
    return {model: analyze(group) for model, group in by_model.items()}


def main(argv: list[str]) -> int:
    if len(argv) < 2:
        print(__doc__, file=sys.stderr)
        return 1
    with open(argv[1]) as f:
        data = json.load(f)
    scores = data.get("matchedPairScores", [])
    if not scores:
        print(json.dumps({"error": "no matchedPairScores in input", "n_perInstance": len(data.get('perInstance', []))}, indent=2))
        return 1

    overall = analyze(scores)
    by_model = analyze_by_model(scores)
    print(json.dumps({"overall": overall, "by_model": by_model}, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
