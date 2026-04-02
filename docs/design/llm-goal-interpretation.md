# LLM-first goal interpretation (design)

## Problem

When the user types a **multi-faceted** or **ambiguous** goal in AI Assist, the current **keyword parser** cannot handle it well. Example:

- *"I am looking for shopping, with some travel and the goal is to maximize money."*

The user has effectively stated:

- **Shopping** → could mean cashback, retail rewards, or general spend.
- **Some travel** → points/miles or transferable points.
- **Maximize money** → cashback / total value.

Today we must pick a **single** goal (CASHBACK | FLEX_POINTS | PROGRAM_POINTS) and optional primary currency. The deterministic parser would likely default to one (e.g. CASHBACK from "money") and ignore the rest, with no way to disambiguate or confirm with the user.

## Proposed direction: LLM-first interpretation

**Yes — going to an LLM first for complex prompts is the right direction.** Suggested flow:

1. **User submits** a goal prompt (e.g. "shopping, some travel, maximize money").
2. **Backend calls an LLM** with:
   - The user’s free-text prompt.
   - A **fixed schema** of what we support: goal types (CASHBACK, FLEX_POINTS, PROGRAM_POINTS), primary currencies (e.g. AA_MILES, AVIOS, BANK_UR, …), and short descriptions for each.
3. **LLM returns** a structured interpretation, for example:
   - **Primary goal** + optional **primary currency** (for PROGRAM_POINTS).
   - Optional **secondary** or **alternatives** (e.g. "User also mentioned travel; alternative: PROGRAM_POINTS").
   - Optional **short rationale** (e.g. "Maximize money chosen as primary; travel mentioned as secondary.").
4. **We then** either:
   - **Option A:** Use the primary interpretation as the single `UserGoal` for this run (same as today’s single-goal optimizer), and optionally show the rationale + alternatives in the UI (“We’re optimizing for cashback to maximize money; you also mentioned travel — [Optimize for travel instead]”).
   - **Option B:** If the LLM returns multiple plausible interpretations, **show the user a small set of choices** (“We understood: 1) Maximize money (cashback), 2) Travel (points/miles). Which should we use?”) and run optimization after they pick one.

So we **get meaning from the LLM**, **map strictly to the options we support**, and **then** either auto-pick one category or present options and come up with one goal for the run.

## Where it plugs in

- **PreferenceParser** (or a new **GoalInterpreter** interface): today `PreferenceParserV1` is deterministic. We could:
  - Add an **LlmGoalInterpreter** that:
    - For “simple” prompts (e.g. single keyword), optionally still use the fast deterministic parser.
    - For longer or multi-clause prompts, call the LLM, then map the LLM output to our schema and return a single `ParsedPreferences` (and optionally a “disambiguation” payload for the UI).
  - Keep the same **downstream contract**: optimizer still receives one `UserGoal` per request.
- **API**: either same `POST /v1/optimize` with `goalPrompt` (backend chooses parser path), or an optional **`POST /v1/interpret-goal`** that returns structured options + primary recommendation so the frontend can show “We interpreted as X; confirm or change” before calling optimize.

## Schema we support (for LLM prompt)

- **GoalType:** CASHBACK, FLEX_POINTS, PROGRAM_POINTS.
- **Primary currency (only for PROGRAM_POINTS):** AA_MILES, AVIOS (and any other we add).
- **Short descriptions** to give the LLM: e.g. “CASHBACK = maximize cash back / statement credit”, “FLEX_POINTS = transferable points (Chase UR, Amex MR, etc.)”, “PROGRAM_POINTS = airline/hotel program miles (e.g. AA, Avios)”.

The LLM must **only** output one of these enum-like values (plus optional rationale and alternatives); we validate and fall back to deterministic parser or default (e.g. CASHBACK) if the LLM output is invalid or missing.

## Summary

- **Should we go to the LLM first for meaning and then map to our options?** Yes, for complex or multi-goal prompts.
- **Do we still end up with one category for the run?** Yes — we either auto-select the primary interpretation or let the user pick one from the options we support, then run the existing single-goal optimizer with that choice.

This doc can be used to implement an LLM-based goal interpreter and, if desired, a small disambiguation/confirmation step in the UI.
