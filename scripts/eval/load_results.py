"""Load evaluation results JSON written by ExplanationEvalRunner."""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any


@dataclass
class EvaluationResults:
    raw: dict[str, Any]

    @property
    def per_instance(self) -> list[dict[str, Any]]:
        return self.raw.get("perInstance", [])

    @property
    def judged_pairs(self) -> list[dict[str, Any]]:
        return self.raw.get("judgedPairs", [])

    @property
    def config(self) -> dict[str, Any]:
        return self.raw.get("config", {})


def load(path: str | Path) -> EvaluationResults:
    with open(path, "r") as f:
        return EvaluationResults(json.load(f))
