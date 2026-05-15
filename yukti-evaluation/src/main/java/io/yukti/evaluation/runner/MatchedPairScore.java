package io.yukti.evaluation.runner;

import io.yukti.engine.explainability.llm.LlmProviderId;

import java.util.Objects;

/**
 * Counterbalanced preference score for a single matched pair.
 *
 * <p>Each pair is judged twice with swapped order. The pair score is the average
 * of the two grounded-side scores. Possible values:
 * <ul>
 *   <li>1.0  grounded wins both orders (strict consistent grounded win)</li>
 *   <li>0.75 grounded wins one order, tie the other</li>
 *   <li>0.5  one tie + one loss, two ties, or one grounded win + one ungrounded win (inconsistent)</li>
 *   <li>0.25 ungrounded wins one order, tie the other</li>
 *   <li>0.0  ungrounded wins both orders (strict consistent ungrounded win)</li>
 * </ul>
 */
public record MatchedPairScore(
    String profileId,
    String goal,
    LlmProviderId modelId,
    OrderedJudgement orderingOne,  // grounded shown as A
    OrderedJudgement orderingTwo,  // grounded shown as B
    double groundedPreferenceScore,
    boolean inconsistent
) {
    public MatchedPairScore {
        Objects.requireNonNull(profileId);
        Objects.requireNonNull(goal);
        Objects.requireNonNull(modelId);
        Objects.requireNonNull(orderingOne);
        Objects.requireNonNull(orderingTwo);
        if (groundedPreferenceScore < 0 || groundedPreferenceScore > 1) {
            throw new IllegalArgumentException("groundedPreferenceScore must be in [0,1]");
        }
    }

    /**
     * Construct a score from two counterbalanced orderings. The contract:
     * {@code orderingOne} must have GROUNDED in slot A and UNGROUNDED in slot B;
     * {@code orderingTwo} must be the swap. Mis-ordered inputs are rejected.
     */
    public static MatchedPairScore build(
        String profileId, String goal, LlmProviderId modelId,
        OrderedJudgement orderingOne, OrderedJudgement orderingTwo
    ) {
        if (orderingOne.positionA() != NarratorVariant.GROUNDED
                || orderingOne.positionB() != NarratorVariant.UNGROUNDED) {
            throw new IllegalArgumentException(
                "orderingOne must have GROUNDED as A and UNGROUNDED as B; got "
                + orderingOne.positionA() + " vs " + orderingOne.positionB());
        }
        if (orderingTwo.positionA() != NarratorVariant.UNGROUNDED
                || orderingTwo.positionB() != NarratorVariant.GROUNDED) {
            throw new IllegalArgumentException(
                "orderingTwo must have UNGROUNDED as A and GROUNDED as B; got "
                + orderingTwo.positionA() + " vs " + orderingTwo.positionB());
        }
        double a = orderingOne.groundedScore();
        double b = orderingTwo.groundedScore();
        double avg = (a + b) / 2.0;
        boolean inconsistent =
            (a == 1.0 && b == 0.0) || (a == 0.0 && b == 1.0);
        return new MatchedPairScore(profileId, goal, modelId, orderingOne, orderingTwo, avg, inconsistent);
    }

    /** True if both orderings agreed that grounded won. */
    public boolean strictConsistentGroundedWin() {
        return groundedPreferenceScore == 1.0;
    }

    /** True if both orderings agreed that ungrounded won. */
    public boolean strictConsistentUngroundedWin() {
        return groundedPreferenceScore == 0.0;
    }
}
