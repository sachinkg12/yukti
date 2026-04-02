# Yukti Phase-1 Specification

## Overview

Yukti (working name) is an AI-first, open/closed, scalable rewards optimization system that recommends a credit-card portfolio and per-category spend allocation policy.

## User Story (Phase-1)

1. User enters annual or monthly spend by category: groceries, dining, gas, travel, online, other.
2. User selects a Goal:
   - **CASHBACK** – maximize cash-back value (USD)
   - **FLEX_POINTS** – maximize transferable bank points (Chase UR, Amex MR, etc.)
   - **PROGRAM_POINTS** – maximize program-specific points (e.g., AA miles)
3. System returns:
   - **Portfolio** – up to N cards
   - **Allocation policy** – category → card, including cap-aware switching notes
   - **Objective breakdown** – earn value, credits value, fees, net
   - **Grounded explanation** – evidence blocks + deterministic narrative

## AI-FIRST Requirement

- **AI reduces friction (optional)**: Parse a single free-text “goal prompt” into structured preferences (Goal + currency + cpp overrides + constraints).
- **AI is NEVER the source of truth** for rewards rules or optimization outputs. Optimization and evidence must be deterministic and auditable.
- AI outputs are suggestions only; safe defaults and user override required.

## Open/Closed Principle

- New reward currencies, valuation policies, rule types, optimizers, catalog sources, and explanation styles must be addable **without modifying orchestration code**.
- Additive extension only: implement via interfaces + registry/factory.
- No scattered if/else by issuer/card type in core logic.

## Tech Stack

- **Java 21**
- **AWS**: API Gateway HTTP API + Lambda for backend; S3 + CloudFront for static web UI
- **No database** in Phase-1; catalog is versioned JSON in repo (optionally loadable from S3)

## Interfaces (Open/Closed)

| Interface | Purpose |
|-----------|---------|
| `RewardCurrency` | Reward type (USD, points, miles) |
| `ValuationPolicy` | Goal-aware cents-per-unit valuation |
| `RewardsRule` | Category, rate, cap, currency |
| `Card` | Card definition (rules, fees, credits) |
| `Catalog` | Card and valuation policy provider |
| `CatalogSource` | Load catalog (classpath, S3) |
| `Optimizer` | Portfolio optimizer (cap-aware greedy v1) |

## Goal-Aware Optimization

"Best" portfolio depends on the user's goal. A card that maximizes cash-back (e.g., Amex BCP 6% groceries) may not be best for a user pursuing flexible points (e.g., Chase UR at 1.25¢/pt). The optimizer uses goal-specific valuation policies (cents-per-point per currency per goal) to compute net value. Deterministic valuation ensures reproducible, auditable recommendations.

## Utility-Aware Optimization (Goal → utility layer)

Valuation converts native rewards (points/miles or USD cash) into **USD utility** for a specific goal. This is the primary fix for "AA miles vs cashback" correctness: **valuation MUST NOT depend on cardId/issuer**—only reward currency and user goal. All valuation is deterministic (no randomness, no network calls).

### CPP units (single unit across system)

- **Unit**: **USD per point** (e.g. 0.013 = 1.3 cents per point). All cpp values use this unit.
- **Config**: `yukti-engine` resource `valuation/default-cpp.v1.json` with `usdPerPoint` map. USD_CASH must be 1.000.
- **Override precedence**: User `UserGoal.cppOverrides` > default table.
- **Override validation**: Values must be > 0 and ≤ 0.10 (10 cents per point upper guard); invalid overrides throw `IllegalArgumentException`.

### Penalty policy (PenaltyPolicyV1)

| Goal | USD_CASH | Preferred / primary | Non-preferred / non-primary |
|------|----------|----------------------|-----------------------------|
| CASHBACK | 1.0 | N/A | 0 (strict cash-only) |
| FLEX_POINTS | 0 | 1.0 (preferred list or BANK_* if empty) | 0.8 |
| PROGRAM_POINTS | 0.6 | 1.0 (primary or in preferred) | 0.6 |

- **CASHBACK**: Only USD_CASH is valued (multiplier 1.0); all other currencies 0.
- **FLEX_POINTS**: Currencies in `preferredCurrencies` (or any BANK_* if list empty) get 1.0; others get 0.8.
- **PROGRAM_POINTS**: `primaryCurrency` and currencies in `preferredCurrencies` get 1.0; others get 0.6.

Computation: `earnedValueUsd = earnedAmountNative * cpp * penaltyMultiplier`. Credits are already in USD; `totalValueUsd = earnedValueUsd + creditValueUsd`. Fees are not subtracted in valuation (optimizer handles fees globally).

### Why "best" depends on goal

- **CASHBACK**: A 6% cashback card (USD) beats a 5% UR card because UR is valued at 0 for this goal.
- **FLEX_POINTS**: Chase UR at 1.8¢/pt can beat cashback when earn rates and cpp combine for higher utility; non-preferred points get 0.8 penalty.
- **PROGRAM_POINTS**: AA co-brands (AA_MILES) get 1.0; Chase UR gets 0.6, so the same portfolio can rank differently by goal.

Assumptions (cpp and penalties) are exported as **AssumptionEvidence** for explainability.

## Greedy Optimizer v1 (Non-optimal)

The Phase-1 optimizer uses a **greedy, deterministic** algorithm. It does **not** claim optimality.

- **Algorithm**: Iteratively add cards by marginal net gain (up to maxCards, Phase-1 hard clamp K≤3). For each candidate portfolio, **AllocationSolverV1** computes per-category allocation and net USD utility. No hidden recomputation: optimizer calls RewardModel and ValuationModel only.
- **Allocation v1**: Exactly one card per category; caps handled inside RewardModel (piecewise). Because allocation assigns all category spend to one card, caps may reduce returns; this is a deliberate v1 simplification (documented in code and here).
- **Per-request cache**: Optimizer passes a single cache into every solve() call so that (cardId, category, spendAmount) results are reused across the greedy loop; cache is in-memory per optimize() only.
- **No MILP**: Phase-1 uses greedy only; no mixed-integer linear programming.
- **Deterministic tie-breaks**: (1) higher marginal gain; (2) within $0.01, prefer lower total fees; (3) lexicographically smallest cardId. Same rules for per-category winner: (1) higher value; (2) within $0.01, lower fee; (3) lexicographically smallest cardId.
- **Evidence**: At minimum—AssumptionEvidence, WinnerByCategoryEvidence (one per category with spend>0), CapHitEvidence (if any), FeeBreakEvenEvidence (one per selected card), PortfolioStopEvidence (exactly one).

## Determinism

Given same inputs + catalog version + valuation config, results must be identical.
