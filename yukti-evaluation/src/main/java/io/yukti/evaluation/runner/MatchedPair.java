package io.yukti.evaluation.runner;

import io.yukti.engine.explainability.llm.LlmProviderId;

import java.util.Objects;

/**
 * A natural matched pair of explanations for the same (profile, goal, model).
 * One is the grounded variant, one is the ungrounded variant. The judge will see
 * both, in two orderings, never knowing which is which.
 */
public record MatchedPair(
    String profileId,
    String goal,
    LlmProviderId modelId,
    PerInstanceEvaluation grounded,
    PerInstanceEvaluation ungrounded
) {
    public MatchedPair {
        Objects.requireNonNull(profileId);
        Objects.requireNonNull(goal);
        Objects.requireNonNull(modelId);
        Objects.requireNonNull(grounded);
        Objects.requireNonNull(ungrounded);
        if (grounded.variant() != NarratorVariant.GROUNDED) {
            throw new IllegalArgumentException("grounded slot must hold a GROUNDED instance");
        }
        if (ungrounded.variant() != NarratorVariant.UNGROUNDED) {
            throw new IllegalArgumentException("ungrounded slot must hold an UNGROUNDED instance");
        }
    }
}
