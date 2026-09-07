# Yukti application specification

## Scope

Yukti is a rewards-optimization application. It recommends a credit-card
portfolio and category-level spend allocation under a user's goal and
constraints. It also returns an explanation derived from typed, solver-produced
evidence.

VerityGate is a separate research contribution in this repository. It validates
structured explanation claims produced over Yukti evidence; it is not the
optimizer and does not change a recommendation. See
[`architecture.md`](architecture.md) for the boundary and full system map.

## User contract

The user supplies:

- annual or monthly spend for groceries, dining, gas, travel, online, and other;
- one goal: `CASHBACK`, `FLEX_POINTS`, or `PROGRAM_POINTS`;
- one to three cards, an annual-fee ceiling, and whether business cards are
  allowed;
- optionally, a goal prompt and an optimizer ID.

The application returns:

- the selected card portfolio;
- category-to-card allocations, including earned value;
- annual reward, credit, fee, and net-value totals;
- a grounded explanation and typed evidence blocks; and
- request, catalog, optimizer, evidence-digest, and verification metadata.

The authoritative HTTP shape is [`../openapi.yaml`](../openapi.yaml).

## Functional requirements

### Goal-aware valuation

All cents-per-point values use **USD per point**. Request-level overrides take
precedence over the versioned default table. Overrides must be greater than zero
and no more than USD 0.10 per point.

`CASHBACK` values only cash rewards. `FLEX_POINTS` prioritizes transferable bank
currencies. `PROGRAM_POINTS` prioritizes a selected airline or hotel currency.
Valuation depends on reward currency and goal, not issuer or card ID.

### Optimization

`milp-v1` is the default optimizer. It jointly chooses cards and allocates
category spend using OR-Tools CBC. The model includes card-count, annual-fee,
allocation, selection-linking, and piecewise cap constraints. Its objective is
annual goal-valued rewards plus credits minus fees.

The optimizer interface also supports exact-search, relaxation, greedy,
content/popularity/random proxies, AHP, rule-based, and simulated-annealing
implementations. The 13 primary strategies are exercised by RewardsBench v2;
the runtime registry additionally exposes two AHP weight variants.

### Explanation

Optimization facts are converted into typed evidence. Deterministic claims are
the default. Optional LLM claims must parse into the claim schema and pass the
four structural verification gates before rendering. A rejected optional
narration falls back to deterministic output.

The explanation response includes graph and claim digests so a consumer can
bind rendered output to the verified structured payload.

### Optional AI assistance

AI reduces input and narration friction but is never the source of truth for
rewards rules or optimization output.

- `GOAL_LLM_ENABLED=true` allows schema-constrained goal interpretation when a
  provider credential is available.
- `NARRATION_LLM_ENABLED=true` allows model-generated structured claims.
- Without credentials or switches, the application is fully operational using
  deterministic goal interpretation and explanations.

## Quality attributes

- **Determinism:** identical request, catalog, configuration, and deterministic
  optimizer produce identical recommendations.
- **Auditability:** catalog version, optimizer ID, evidence IDs, digests, and
  verification status are returned with the result.
- **Fail-closed narration:** unverified structured claims are not rendered.
- **Open/closed design:** new implementations enter through interfaces and
  registries rather than issuer-specific orchestration branches.
- **No runtime database requirement:** the default catalog is versioned JSON.
- **Credential isolation:** provider credentials are environment-only and model
  calls are opt-in.

## Technology and deployment

- Java 21 and Gradle 9.3.1
- OR-Tools CBC 9.10.4067
- Jackson 2.17 and JUnit Jupiter 5
- React 18, TypeScript, Vite 6, and Tailwind CSS 3
- Local HTTP server for development; API Gateway/Lambda and static web hosting
  are the intended cloud boundaries. The current SAM template exposes the
  legacy `/optimize` adapter only; the full `/v1` surface is currently the
  local-server contract.

## Out of scope

- Financial advice or guaranteed realized reward value
- Automatic card applications or account access
- LLM-authored reward rules or solver constraints
- A claim that structural verification proves natural-language entailment
- Cross-domain validation of the VerityGate evaluation
