package io.yukti.evaluation.runner;

import io.yukti.evaluation.judge.PreferenceVote;

import java.util.Objects;

/**
 * One judging event for a matched pair. Records which variant was shown in the
 * "A" slot and which in the "B" slot, plus the judge's vote.
 *
 * <p>The judge sees blinded labels (Explanation A, Explanation B). The variant
 * identity is only recorded after the fact so we can compute per-variant scores.
 */
public record OrderedJudgement(
    NarratorVariant positionA,
    NarratorVariant positionB,
    PreferenceVote vote
) {
    public OrderedJudgement {
        Objects.requireNonNull(positionA);
        Objects.requireNonNull(positionB);
        Objects.requireNonNull(vote);
        if (positionA == positionB) {
            throw new IllegalArgumentException("positionA and positionB must differ");
        }
    }

    /** The grounded score implied by this single judgement (1.0 grounded wins, 0.5 tie, 0.0 ungrounded wins). */
    public double groundedScore() {
        return switch (vote.winner()) {
            case A -> positionA == NarratorVariant.GROUNDED ? 1.0 : 0.0;
            case B -> positionB == NarratorVariant.GROUNDED ? 1.0 : 0.0;
            case TIE -> 0.5;
        };
    }
}
