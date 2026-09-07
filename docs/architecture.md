# Yukti system architecture

This document is the canonical technical map for the repository. It separates
the production application from the research contribution that evaluates one
part of its explanation pipeline.

## Contribution boundary

| Contribution | Purpose | Primary code | Release material |
|---|---|---|---|
| **Yukti application** | Optimize a credit-card portfolio and spend allocation, then explain the result from solver-produced evidence. | `yukti-core`, `yukti-catalog`, `yukti-engine`, `yukti-api`, `yukti-web` | Root `README.md`, API contract, app tests, benchmark documentation |
| **VerityGate research** | Test and enforce structural faithfulness of LLM-generated claims over typed evidence. | `yukti-explain-core`, evaluation paths in `yukti-engine`, `yukti-evaluation`, and `yukti-bench` | `docs/veritygate-groundlm-2026/` plus a separately versioned Zenodo supplement |

The only architectural bridge is a typed contract:

```text
Yukti OptimizationResult
  -> typed EvidenceGraph
  -> optional structured ClaimBundle
  -> VerityGate verification report
  -> verified narration or deterministic fallback
```

VerityGate does not choose cards, allocate spend, value currencies, or modify
solver output. Yukti can run end to end without model credentials; in that
mode it produces deterministic explanations.

## Repository modules

```text
yukti-explain-core  <- yukti-core <- yukti-catalog
        ^                  ^              ^
        |                  |              |
        +------------ yukti-engine -------+
                          ^
              +-----------+-----------+
              |           |           |
          yukti-api  yukti-evaluation  yukti-bench
              |
          yukti-web (HTTP client)
```

| Module | Responsibility | Depends on |
|---|---|---|
| `yukti-explain-core` | Claim types, evidence graph, stable digests, four-gate verifier | Jackson |
| `yukti-core` | Domain model and extension interfaces such as `Optimizer`, `RewardModel`, `ValuationModel`, and `CatalogSource` | `yukti-explain-core` |
| `yukti-catalog` | Loads and validates the versioned card catalog | `yukti-core` |
| `yukti-engine` | Reward calculation, valuation, optimizers, goal interpretation, evidence production, narration | core, catalog, explain-core, OR-Tools |
| `yukti-api` | `/v1` HTTP contract, validation, request IDs, local server, Lambda entry point | core, catalog, engine |
| `yukti-web` | React/TypeScript user interface | `/v1` HTTP API |
| `yukti-evaluation` | Per-instance explanation records, metrics, and summaries | core, engine, explain-core |
| `yukti-bench` | RewardsBench, paired narrator experiments, statistics launchers, verifier microbenchmark | all evaluation-side Java modules |
| `infra` | Deployment boundary for AWS resources | none at runtime |

## Yukti request path

1. The React client obtains goals, cards, and optimizer IDs from the `/v1`
   configuration endpoints.
2. `POST /v1/optimize` validates period, categories, goal, constraints, and the
   requested optimizer.
3. A structured goal is used directly. If `goalPrompt` is present, the goal
   interpreter maps it into the same closed goal schema. The deterministic
   interpreter is the default; the LLM interpreter is explicitly opt-in.
4. `ClasspathCatalogSource` loads the versioned JSON catalog.
5. `OptimizerRegistry` selects `milp-v1` by default or a requested strategy.
6. The optimizer returns selected cards, category allocations, and a value
   breakdown. The MILP implementation uses OR-Tools CBC and falls back to the
   cap-aware greedy implementation if an acceptable solver result is not
   available.
7. `MilpSolutionAnalyzer` and the explanation builders derive typed evidence
   from the optimization result.
8. The deterministic generator produces claims. The primary optional model
   path parses render-safe normalized claims and validates their schema before
   verification. A compatibility narrator path parses prose-bearing claims and
   still applies the four gates, but does not run the normalized schema check.
9. `ClaimVerifier` checks every claim against the evidence graph. Only passing
   claims can be rendered; a failed optional narration falls back to the
   deterministic explanation path.
10. The API returns the recommendation, evidence, digests, claim count, and
    verification status under one request ID.

## Optimization model

The default `milp-v1` solver decides both card selection and category-level
spend allocation. Its objective maximizes goal-valued annual rewards and
credits minus annual fees. Constraints enforce:

- one to three selected cards;
- the user's annual-fee ceiling;
- full allocation of supplied category spend;
- linking between selected cards and allocations; and
- piecewise-linear reward-cap segments.

Currency valuation is deterministic and goal-dependent. Values use USD per
point, with request overrides taking precedence over the versioned defaults.
The LLM boundary never supplies reward rules or objective values.

The benchmark compares 13 primary strategies spanning exact optimization,
relaxation, deterministic heuristics, proxy baselines, and simulated annealing.
The runtime registry also exposes two additional AHP weight variants.

## Explanation and verification path

### Evidence production

Evidence is generated after optimization and includes the values required to
justify the result: winners by category, cap boundaries, allocation segments,
fee break-even facts, assumptions, and result summaries. Stable evidence IDs
and a SHA-256 graph digest make the payload inspectable and comparable.

### Four gates

The verifier checks a structured claim in order:

1. **Evidence existence** — every cited evidence ID exists.
2. **Entity allowlist** — cited cards and categories occur in the evidence
   graph's allowed entities.
3. **Number binding** — cited numbers occur in the graph's numeric allowlist,
   apart from the small fixed constants documented by the contract.
4. **Type rules** — the claim cites the evidence types required for its claim
   type; for example, a cap-switch claim needs cap-hit and allocation-segment
   support when those nodes are present.

These gates establish structural support within the typed claim channel. They
do not prove prose entailment, evidence completeness, factual correctness of
the upstream catalog, or cross-domain validity. See
[`design/verification-guarantees.md`](design/verification-guarantees.md).

## Optional model boundary

There are two independently controlled model-assisted paths:

| Path | Switch | Input | Accepted output | Fallback |
|---|---|---|---|---|
| Goal interpretation | `GOAL_LLM_ENABLED=true` plus `OPENAI_API_KEY` | Free-text `goalPrompt` and closed goal vocabulary | One validated `UserGoal` | Deterministic interpreter |
| Explanation claims | `NARRATION_LLM_ENABLED=true` plus provider credentials | Solver result and typed evidence | Schema-valid claims that pass all four gates | Deterministic explanation |

`NARRATION_MODE=llm` is retained as a compatibility path for prose-bearing
`Claim` records. New integrations should use `NARRATION_LLM_ENABLED` and the
normalized-claim channel.

Neither path is required for optimization. See
[`design/llm-goal-interpretation.md`](design/llm-goal-interpretation.md) for
the implemented goal-interpreter contract.

## Evaluation architecture

Yukti and VerityGate have separate evaluation questions:

- **RewardsBench v2** evaluates portfolio quality, exactness, runtime, and
  optimizer comparisons over 200 profiles. The main split contains 150
  profiles (450 profile-goal instances) and the holdout contains 50.
- **VerityGate paired benchmark** evaluates grounded and ungrounded narrations
  over 50 generated profiles, three goals, three narrator families, and two
  variants: 900 paired-generation instances per repair setting.

Paper-grade result logs and rater material are intentionally excluded from the
code repository. The repository contains implementation, schemas, prompts,
pre-registration, and reconciliation code; immutable experimental data belongs
in the version-specific Zenodo supplement.

## Deployment surfaces

| Surface | Local | Hosted boundary |
|---|---|---|
| API | `./gradlew :yukti-api:runServer` on port 18000 | API Gateway HTTP API + Lambda |
| Web app | `npm run dev` in `yukti-web` on port 15173 | Static site hosting such as S3 + CloudFront |
| Project documentation | Static files in `docs/` | GitHub Pages |

The GitHub Pages site is documentation, not the Yukti runtime UI.

### Current deployment compatibility note

`LocalServer` exposes both the current `/v1` routes and a legacy root
`/optimize` route. `openapi.yaml` documents the current `/v1` local contract.
The existing AWS SAM `template.yaml` still wires only the legacy `/optimize`
Lambda adapter; deploying the complete `/v1` surface requires a v1-aware
Lambda routing adapter and corresponding SAM events. This is a deployment gap,
not an application-core or local-API ambiguity.

## Extension points

- Add an optimizer by implementing `Optimizer` and registering a distinct ID.
- Add a catalog source by implementing `CatalogSource`; do not put provider
  conditionals into orchestration code.
- Add a reward or valuation policy behind the corresponding core interface.
- Add evidence or claim types by versioning the portable schemas, updating the
  rule map, and adding verifier and compatibility tests.
- Add an LLM provider behind the existing provider interface while preserving
  schema validation, opt-in configuration, and deterministic fallback.

Changes across the Yukti–VerityGate boundary require contract tests on both
sides: evidence generation in the application and claim verification in the
research/runtime verifier.
