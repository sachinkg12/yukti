package io.yukti.evaluation.judge;

/**
 * Pairwise preference judge. Decides whether explanation A or B is more helpful,
 * faithful, or fluent.
 *
 * <p>Open Closed: rules engine, LLM judge, or human judge can all implement this.
 */
public interface PreferenceJudge {

    /**
     * Score a pair (A vs B). The two explanations are about the same underlying
     * optimization result so the judge can compare them directly.
     */
    PreferenceVote judge(String contextSummary, String explanationA, String explanationB);
}
