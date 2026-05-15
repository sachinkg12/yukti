package io.yukti.evaluation.runner;

import io.yukti.evaluation.judge.PreferenceVote;

import java.util.Objects;

/**
 * One pairwise judge sample. Records both sides and the vote.
 */
public record JudgedPair(
    String profileId,
    String goal,
    NarratorVariant variantA,
    NarratorVariant variantB,
    PreferenceVote vote
) {
    public JudgedPair {
        Objects.requireNonNull(profileId);
        Objects.requireNonNull(goal);
        Objects.requireNonNull(variantA);
        Objects.requireNonNull(variantB);
        Objects.requireNonNull(vote);
    }
}
