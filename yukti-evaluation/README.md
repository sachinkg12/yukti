# yukti-evaluation

Hallucination, fluency, and pairwise preference evaluation for LLM generated
explanations of Yukti optimizer output.

This module is separate from the core optimization path so that production
deployments do not pull in evaluation dependencies.

## Packages

| Package | Purpose |
|---|---|
| `hallucination` | Detect grounding failures in claims against the evidence graph |
| `taxonomy` | Failure category enum and rule based classifier |
| `fluency` | Readability and lexical metrics over rendered explanation text |
| `judge` | Pairwise preference judge interface and an LLM as judge implementation |
| `runner` | Orchestrator, summarizer, and JSON writer |

## Design

All packages follow the Open Closed Principle. New detectors, metrics, judges,
and providers are added by implementing the interface and registering with the
registry. No call site is touched.

## Entry point

The bench module exposes a Gradle task that runs the evaluation:

```
./gradlew :yukti-bench:runExplanationEval
```

Provider credentials are read from environment variables:

- `OPENAI_API_KEY` for GPT-4o family
- `ANTHROPIC_API_KEY` for Claude Sonnet / Haiku
- `TOGETHER_API_KEY` for Llama and Qwen

Models without credentials are skipped, not faked.

## Output

Two JSON files are produced:

- `artifacts/bench/v2/explanation_eval_results.json` (raw)
- `artifacts/bench/v2/explanation_eval_summary.json` (per variant / model means)

LaTeX table fragments are built from these by:

```
python -m scripts.eval.build_eval_tables \
  artifacts/bench/v2/explanation_eval_results.json \
  /path/to/generated/
```
