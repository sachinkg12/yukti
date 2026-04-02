# Yukti – Rewards Optimization

Phase-1 of an AI-first, open/closed, scalable rewards optimization system.

## Modules

- **yukti-core** – Domain model (Category, Period, GoalType, RewardCurrencyType, Money, SpendProfile, UserConstraints, UserGoal, OptimizationRequest/Result), OCP interfaces (CatalogSource, Optimizer, ValuationModel, RewardModel, etc.)
- **yukti-catalog** – JSON catalog parser, ClasspathCatalogSource, 30-card seed catalog
- **yukti-engine** – CapAwareGreedyOptimizer, DefaultValuationModel, SimpleRewardModel, DeterministicPreferenceParser
- **yukti-api** – REST API DTOs, Lambda handler, LocalServer (serves UI at /)
- **yukti-web** – Static web UI (copy from `web/`)
- **yukti-bench** – Benchmark harness (20 profiles × 3 goals = 60 runs)
- **infra** – AWS deployment (placeholder)

## Quick Start

```bash
# Build
./gradlew build

# Run local HTTP server (port 18000, serves UI at /)
./gradlew :yukti-api:runServer

# Run benchmark
java -cp yukti-bench/build/libs/yukti-bench-0.1.0-SNAPSHOT.jar io.yukti.bench.BenchmarkHarness
```

Then open http://localhost:18000

## API

POST `/optimize`:

```json
{
  "monthly": true,
  "spend": { "GROCERIES": 500, "DINING": 250, "GAS": 200, "TRAVEL": 166, "ONLINE": 100, "OTHER": 416 },
  "goal": "CASHBACK",
  "maxCards": 3
}
```

## Goals

- **CASHBACK** – Maximize cash-back value
- **FLEX_POINTS** – Chase UR, Amex MR, Citi TYP, Cap1 Venture
- **PROGRAM_POINTS** – AA miles (and other program currencies)

## Tests

```bash
./gradlew test
```

Domain serialization (Money arithmetic, SpendProfile), catalog parsing, optimizer determinism.
