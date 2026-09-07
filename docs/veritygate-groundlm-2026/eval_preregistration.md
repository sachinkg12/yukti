# Pre-registration: Explanation Evaluation (paper-grade)

Filed before the paper-grade run, ahead of seeing any paper-grade numbers, so the
primary metrics and decision rules cannot be retroactively chosen to favour the
prettiest table. Smoke results (N=9 per cell) informed metric *choice* but not
threshold *selection*.

## Run identity

- **Profile set**: `ProfileGenerator.generate(50, seed=42, "eval-")` × 3 goals (CASHBACK, FLEX_POINTS, PROGRAM_POINTS) = 150 (profile, goal) instances
- **Narrators**: `OPENAI_GPT4O_MINI`, `TOGETHER_LLAMA_70B` (Meta Llama 3.3 70B Instruct Turbo), `ANTHROPIC_CLAUDE_SONNET` (4.6)
- **Variants**: GROUNDED + UNGROUNDED, paired
- **Judge**: `OPENAI_GPT4O` (disjoint from every narrator family used)
- **Two parallel runs**, otherwise identical:
  - `repair=0` — baseline ablation
  - `repair=1` — headline
- **Catalog**: `catalog-v1.json` (SHA-256 fixed in repo)
- **Seed**: 42 (also the profile-generator seed)
- **Code freeze**: branch tip at run start (record commit hash in run output)

## Co-primary metrics

1. **Claim-level survival rate** = total verifier-accepted claims / total generated claims, aggregated within each (variant, model, repair) bucket. Reflects what fraction of model output the deployable verifier+repair pipeline keeps.
2. **Mean shipped verified claims per instance** = arithmetic mean of `shippedClaims` per instance within each bucket. Reflects how many sentences of explanation the user actually sees, on average.

Both are recorded per-instance in `explanation_eval_results.json` and aggregated in `explanation_eval_summary.json`. They are computed automatically by `EvaluationSummarizer` and Python `hallucination_rates.aggregate`.

## Secondary / descriptive metrics

- Strict verifier instance pass rate (parseable AND verifier passed)
- Per-gate failure counts (NUMBER_BINDING, TYPE_RULES, ENTITY_ALLOWLIST, EVIDENCE_EXISTENCE)
- Schema-failed instance count
- Mean fluency scores (Flesch-Kincaid, reading ease, sentence length, lexical diversity, word count)
- Heuristic taxonomy hallucination rate (legacy, for back-compat with prior tables only)

## Inference procedure

- **Cluster bootstrap on instances** (not claims): 10000 iterations, seed=42 via `scripts/eval/bootstrap_ci.py`.
- **Primary report form**: paired differences with 95% CI on the difference. Pairing keys:
  - For repair=1 vs repair=0 within the same model: pair by (profileId, goal, modelId).
  - For model A vs model B within the same repair setting: pair by (profileId, goal); model is the contrast.
  - For variant comparisons within the same model and repair setting: pair by (profileId, goal, modelId).
- **Absolute rates** with bootstrap CIs are reported as secondary.

## Decision rules

These thresholds are committed before paper-grade data is examined.

| claim | required evidence |
|---|---|
| "Repair improves claim survival" | 95% CI on Δ (repair=1 − repair=0) excludes 0, in at least one narrator. |
| "Frontier model improves claim survival under repair" | 95% CI on Δ (Sonnet − mini at repair=1) excludes 0. |
| "Repair has diminishing returns" | 95% CI on Δ (repair=2 − repair=1, Sonnet, smoke data) **includes 0**. Already met at smoke N=9. |
| "Open-weights mid-tier behaves between small and frontier" | Llama claim survival point estimate at repair=1 falls between mini and Sonnet, with CI overlap allowed at one boundary. |
| "Verifier is fail-closed" | Every claim that the published renderer would ship has `verifierReport.passed = true` per the permissive-emission policy, by construction of the policy. Verified by `validate_paper.py`-style check that the shipped subset is the verified subset. |

## What we will NOT claim

- **"LLMs never hallucinate when grounded."** Smoke killed this; prose and tables must use the verifier-channel scoping language.
- **"100 % of hallucinations caught."** Use "100 % of verifier-evaluable violations in the structured claim channel are rejected (gates 1–4 as implemented)."
- **"Grounded narration beats ungrounded."** Only after repair, only on claim-level survival, paired and bootstrapped. Otherwise the smoke data shows raw grounded < raw ungrounded on claim survival.
- **"Cross-domain generalization."** Eval is single-domain (credit-card portfolio optimization). The verifier itself is domain-agnostic but the evidence-graph builder is not; cross-domain transfer is future work.
- **"Seed robustness."** Single seed (42) per run. Reproducibility anchor only; not a robustness claim.

## Stop conditions

- A run that hits >5% provider call failures aborts; we re-run that cell only after diagnosing.
- A run whose total cost exceeds the budget +50% aborts and we re-plan.
- If post-hoc inspection reveals a code bug that materially changes metrics (e.g., a verifier bug), we discard affected runs, fix the bug, and re-run; the discarded data is *not* used in the paper.
