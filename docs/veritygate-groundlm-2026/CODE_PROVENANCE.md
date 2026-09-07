# VerityGate code provenance

## Paper-grade base

The VerityGate release changes use this repository commit as their
paper-grade base:

```text
c7e6392a791454d2d57b670fe83e352edd8c1928
2026-05-15T00:48:39-07:00
Add yukti-evaluation module for grounded LLM narration evaluation
```

The paper-grade JSON runs were generated on May 14, 2026 but did not record a
Git commit. The commit above is therefore the committed evaluation snapshot
associated with those runs, not a cryptographic proof that every run used
those exact bytes.

## Additive camera-ready code

The following files include later, allowlisted additions used only for the
rater-capture studies or verifier microbenchmark:

- `yukti-bench/build.gradle.kts`
- `yukti-bench/src/main/java/io/yukti/bench/explanation/ExplanationEvalRunner.java`
- `yukti-bench/src/main/java/io/yukti/bench/explanation/PairedNarratorRunner.java`
- `yukti-bench/src/main/java/io/yukti/bench/verifier/VerifierMicroBench.java`
- `yukti-evaluation/src/main/java/io/yukti/evaluation/runner/ExplanationEvaluator.java`
- `yukti-evaluation/src/main/java/io/yukti/evaluation/runner/PerInstanceEvaluation.java`

These additions expose rater-capture fields and a deterministic local
microbenchmark. They do not alter the primary four-gate verifier rules or the
already collected paper-grade results.

## Release binding

The camera-ready code release is identified by the annotated tag
[`veritygate-groundlm-2026-v1.0.0`](https://github.com/sachinkg12/yukti/releases/tag/veritygate-groundlm-2026-v1.0.0).
The Zenodo supplement's `CODE_POINTER.md` records the tag's immutable target
commit so the evidence package does not depend on the repository's moving
default branch.
