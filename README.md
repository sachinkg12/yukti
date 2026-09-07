# Yukti

[![CI](https://github.com/sachinkg12/yukti/actions/workflows/ci.yml/badge.svg)](https://github.com/sachinkg12/yukti/actions/workflows/ci.yml)
[![DOI](https://zenodo.org/badge/1199192551.svg)](https://doi.org/10.5281/zenodo.19425818)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

Credit card portfolio optimization via mixed-integer linear programming (MILP) with structurally verified explanations.

Yukti selects up to 3 cards from a 70-card US credit card catalog and allocates spending across 6 categories to maximize net annual reward value under piecewise-linear reward caps, goal-dependent multi-currency valuation, and fee constraints. The MILP achieves solver-certified optimality on all benchmark instances, independently confirmed by exhaustive enumeration.

## Two contributions, one explicit boundary

| Contribution | What it is | Start here |
|---|---|---|
| **Yukti** | The reusable rewards-optimization application: catalog, optimizers, API, and React UI. | [Project documentation](https://sachinkg12.github.io/yukti/#yukti) |
| **VerityGate** | The accepted GroundLM 2026 research contribution: a four-gate structural faithfulness framework and paired benchmark that uses Yukti as its structured-backend case study. | [VerityGate research page](https://sachinkg12.github.io/yukti/#veritygate) |

The integration boundary is narrow: Yukti emits an optimization result and a
typed evidence graph; VerityGate checks structured explanation claims against
that graph. VerityGate never selects cards or changes solver output. Paper
source, PDFs, paper-grade logs, and rater materials remain outside this code
repository and are released separately.

See the [project site and interactive architecture guide](https://sachinkg12.github.io/yukti/)
or the canonical [`docs/architecture.md`](docs/architecture.md).

## Features

- **MILP formulation** with piecewise-linear reward caps, goal-dependent valuation, and fee/cardinality constraints
- **13 benchmarked solver strategies** plus 2 AHP weight variants (exact,
  heuristic, metaheuristic, and proxy baselines) behind a common `Optimizer`
  interface
- **Four-gate structural claim verifier** ensuring explanations cite only solver-emitted evidence (SHA-256 digested evidence graphs)
- **70-card catalog** covering 19 reward currencies, 10 issuers, with checksummed definitions
- **RewardsBench v2** benchmark: 200 profiles, 450 main instances, paired statistical analysis
- **REST API** with per-request optimizer selection
- **React UI** with optimizer dropdown, spend inputs, goal picker

## Modules

| Module | Description |
|--------|-------------|
| `yukti-core` | Domain model, OCP interfaces (Optimizer, RewardModel, ValuationModel, CatalogSource) |
| `yukti-explain-core` | Claims model, evidence graph, SHA-256 digests, ClaimVerifier (4 gates) |
| `yukti-catalog` | JSON catalog parser, checksummed card definitions (carddsl v0.1) |
| `yukti-engine` | 13 benchmarked optimizer strategies plus 2 AHP variants, reward/valuation models, explanation pipeline |
| `yukti-evaluation` | Explanation-evaluation records, metrics, and summaries |
| `yukti-bench` | Benchmark harness, profile generator, BLS profile generator, scaling study |
| `yukti-api` | REST API (`/v1`) local server and legacy `/optimize` Lambda adapter |
| `yukti-web` | React 18 + TypeScript + Tailwind CSS frontend |

## Quick Start

```bash
# Build
./gradlew build

# Run API server (port 18000)
./gradlew :yukti-api:runServer

# Run React frontend (port 15173)
cd yukti-web && npm install && npm run dev
```

Open http://localhost:15173 for the UI, or use the API directly.

### With AI features (optional)

Yukti supports optional LLM-powered features for natural language goal interpretation and enhanced explanations. The optimization itself is always deterministic (MILP) — AI only assists with input parsing and output narration.

```bash
export OPENAI_API_KEY=sk-your-key-here
export GOAL_LLM_ENABLED=true
export NARRATION_LLM_ENABLED=true
./gradlew :yukti-api:runServer
```

| Feature | Env Variable | What it does | Without it |
|---------|-------------|-------------|------------|
| Goal interpretation | `GOAL_LLM_ENABLED=true` | Maps a free-text goal such as "I fly AA a lot" into the supported goal schema | Manual goal selection or deterministic interpretation |
| Structured narration claims | `NARRATION_LLM_ENABLED=true` | Generates normalized claims that must pass schema validation and all four gates before rendering | Deterministic claims and narration |

Both paths require `OPENAI_API_KEY`. Without it, the system works fully with deterministic defaults.

## API

### POST `/v1/optimize`

```json
{
  "period": "ANNUAL",
  "spendByCategoryUsd": {
    "GROCERIES": 8000,
    "DINING": 4000,
    "GAS": 2500,
    "TRAVEL": 5000,
    "ONLINE": 3000,
    "OTHER": 2000
  },
  "goal": { "goalType": "CASHBACK" },
  "constraints": { "maxCards": 3, "maxAnnualFeeUsd": 1000, "allowBusinessCards": false },
  "optimizerId": "milp-v1"
}
```

The `optimizerId` field is optional (defaults to `milp-v1`). Available optimizers: `GET /v1/config/optimizers`.

### Other endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/v1/config/optimizers` | List available optimizer IDs |
| GET | `/v1/config/goals` | Supported goals and default CPP values |
| GET | `/v1/catalog/cards` | List catalog cards |
| GET | `/health` | Health check |

## Goals

- **CASHBACK** — Maximize USD cash rewards
- **FLEX_POINTS** — Maximize transferable bank points (Chase UR, Amex MR, Citi TY, Capital One)
- **PROGRAM_POINTS** — Maximize a specific airline/hotel loyalty currency

## Benchmark

```bash
# Run full v2 benchmark (200 profiles x 13 optimizers)
./gradlew :yukti-bench:runBenchV2

# Run ablation (4 penalty x credits configurations)
./gradlew runAblationStrictCreditsOn runAblationStrictCreditsOff \
          runAblationSoftCreditsOn runAblationSoftCreditsOff

# Run scaling study
./gradlew :yukti-bench:runScalingStudy
```

## Tests

```bash
# All tests
./gradlew test

# Specific module
./gradlew :yukti-engine:test
./gradlew :yukti-explain-core:test
```

## Tech Stack

- Java 21, Gradle 9.3 (Kotlin DSL)
- OR-Tools CBC 9.10.4067 (MILP solver)
- Jackson 2.17 (JSON), JUnit Jupiter 5, ArchUnit
- React 18, TypeScript, Vite 6, Tailwind CSS 3

## Documentation

- [Project site and visual architecture](https://sachinkg12.github.io/yukti/)
- [System architecture](docs/architecture.md)
- [Application specification](docs/spec.md)
- [OpenAPI contract](openapi.yaml)
- [Evidence and claim contract](docs/design/evidence-contract-spec.md)

## License

Apache 2.0 — see [LICENSE](LICENSE).
