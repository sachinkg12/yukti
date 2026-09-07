# Goal interpretation architecture

This document describes the implemented optional model-assisted goal path.
Downstream optimization always consumes one validated `UserGoal`, regardless
of how that goal was obtained.

## Runtime flow

1. `POST /v1/optimize` receives a structured `goal` and optionally a
   `goalPrompt`.
2. Without `goalPrompt`, the structured goal is mapped directly.
3. With `goalPrompt`, `V1ApiHandler` calls the configured `GoalInterpreter`.
4. `DeterministicGoalInterpreter` is the default implementation.
5. `LlmGoalInterpreter` is selected only when `GOAL_LLM_ENABLED=true` and an
   OpenAI provider can be created from `OPENAI_API_KEY`.
6. Model output is restricted to the supported goal schema. Invalid, missing,
   or failed output uses the deterministic interpreter.
7. `OptimizationMapperV1` combines the interpretation with explicit preference
   and CPP inputs, then constructs the same `OptimizationRequest` used by the
   non-LLM path.

```text
goalPrompt ----> GoalInterpreter ----> validated GoalInterpretation
                       |                         |
structured goal -------+-------------------------+
                                                 v
                                     OptimizationRequest -> Optimizer
```

## Supported output vocabulary

- `CASHBACK`
- `FLEX_POINTS`
- `PROGRAM_POINTS`, with a supported primary reward currency

The interpreter may also return a short rationale for display. It does not
produce card IDs, reward rates, valuations, constraints, or allocations.
Explicit preferred currencies and CPP overrides remain typed request fields.

## Safety boundary

The model is an input adapter, not a decision engine:

- its response must map to closed enum-like values;
- malformed output cannot enter the optimizer;
- the deterministic path remains available for every request;
- card rules and valuations come from versioned application data; and
- the selected optimizer remains responsible for all portfolio decisions.

## Configuration

```bash
export OPENAI_API_KEY=sk-...
export GOAL_LLM_ENABLED=true
./gradlew :yukti-api:runServer
```

If either the switch or credential is absent, goal interpretation stays local
and deterministic. `NARRATION_LLM_ENABLED` controls a different path and is not
required for goal interpretation.

## Extension contract

A new interpreter must implement `GoalInterpreter`, return only supported goal
types/currencies, preserve explicit user preferences, and define a deterministic
failure path. Adding a provider must not change `OptimizationRequest` or the
optimizer interfaces.
