# VerityGate: GroundLM 2026 code release

This directory documents the code associated with the accepted paper
**“VERITYGATE: A Four-Gate Schema-Level Faithfulness Framework and Paired
Benchmark for Grounded LLM Narrations over Structured Evidence.”** GroundLM
2026 is an EMNLP 2026 workshop.

## Boundary from Yukti

**Yukti** is the reusable application in this repository. It optimizes a
credit-card portfolio and produces a typed evidence graph.

**VerityGate** is the research contribution. It consumes that graph and checks
structured explanation claims; it does not choose cards, assign spend, value
currencies, or modify the solver result.

The paper source and PDFs are intentionally excluded. Paper-grade result logs,
rater data, and immutable manifests belong in the separately released Zenodo
supplementary artifact.

## Code map

| Paper component | Repository location |
|---|---|
| Four-gate verifier | `yukti-explain-core/src/main/java/io/yukti/explain/core/claims/ClaimVerifier.java` |
| Claim model and type rules | `yukti-explain-core/src/main/java/io/yukti/explain/core/claims/` |
| Evidence graph and digest | `yukti-explain-core/src/main/java/io/yukti/explain/core/evidence/graph/` |
| Claim and evidence schemas | `docs/design/schemas/` |
| Narrator and judge prompts | `yukti-bench/src/main/java/io/yukti/bench/explanation/NarratorPrompts.java` |
| Paired evaluation runner | `yukti-bench/src/main/java/io/yukti/bench/explanation/` |
| Evaluation records and summaries | `yukti-evaluation/src/main/java/io/yukti/evaluation/` |
| Bootstrap/statistical utilities | `scripts/eval/` |
| Verifier microbenchmark | `yukti-bench/src/main/java/io/yukti/bench/verifier/VerifierMicroBench.java` |
| Pre-registration copy | `docs/veritygate-groundlm-2026/eval_preregistration.md` |
| Artifact reconciliation launcher | `scripts/eval/reconcile_veritygate_results.py` |

## Build and test

Requirements:

- Java 21
- the bundled Gradle 9.3.1 wrapper
- Python 3 for the result reconciliation utility

Run Java tests without model credentials:

```bash
./gradlew test --no-daemon
```

Run the deterministic verifier microbenchmark without API calls:

```bash
./gradlew :yukti-bench:runVerifierMicroBench --no-daemon
```

After downloading and extracting the Zenodo supplementary artifact, reproduce
the camera-ready result tables with:

```bash
python3 scripts/eval/reconcile_veritygate_results.py \
  --artifact-root /path/to/veritygate-groundlm-2026
```

The paper-grade LLM calls are not required for reconciliation. Re-running
generation requires provider credentials and can produce different natural
language rationales even when the experimental configuration is unchanged.

## Source lineage

The paper-grade generation code is based on commit
`c7e6392a791454d2d57b670fe83e352edd8c1928`. The result JSON did not embed a
Git hash, so this is the committed evaluation snapshot associated with the
runs rather than a cryptographic code-to-run binding. Camera-ready additions
for rater capture and the verifier microbenchmark are documented in
[`CODE_PROVENANCE.md`](CODE_PROVENANCE.md).

## Release identity

- Intended Git tag: `veritygate-groundlm-2026-v1.0.0`
- Public release URL: pending publication
- Zenodo supplementary version DOI: pending publication

Replace the two pending fields after the GitHub release and Zenodo record are
public. Cite the version-specific Zenodo DOI so the evidence files are pinned.

## Credentials and generated data

No credentials are committed. Copy `.env.example` to a private local `.env`
only when re-running model calls. Build directories, `.env` files, model
outputs, and downloaded supplementary data remain ignored by `.gitignore`.

## Related documents

- [`eval_preregistration.md`](eval_preregistration.md) — paper-grade evaluation
  plan recorded before the main run
- [`CODE_PROVENANCE.md`](CODE_PROVENANCE.md) — base commit and additive
  camera-ready changes
- [`CITATION.cff`](CITATION.cff) — research-code citation metadata
- [`../design/evidence-contract-spec.md`](../design/evidence-contract-spec.md) —
  portable evidence/claim contract
- [`../design/verification-guarantees.md`](../design/verification-guarantees.md) —
  guarantees and non-guarantees

## License

Apache License 2.0; see the repository `LICENSE`.
