package io.yukti.evaluation.runner;

import io.yukti.engine.explainability.llm.LlmProviderId;
import io.yukti.evaluation.judge.PreferenceVote;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the counterbalanced scoring rubric required by the preference protocol.
 * Each pair is judged twice with swapped order. Score outcomes:
 *   1.0  grounded wins both orders (consistent, inconsistent=false)
 *   0.75 grounded wins one order, tie one (consistent, inconsistent=false)
 *   0.5  tie both orders (consistent, inconsistent=false) OR
 *        grounded wins one + ungrounded wins one (strict disagreement, inconsistent=true)
 *   0.25 ungrounded wins one, tie one (consistent, inconsistent=false)
 *   0.0  ungrounded wins both (consistent, inconsistent=false)
 *
 * The 0.5 score thus collapses two semantically different cases (tie-tie vs flip).
 * The inconsistent flag distinguishes them.
 */
class MatchedPairScoreTest {

    private OrderedJudgement order(NarratorVariant a, NarratorVariant b, PreferenceVote.Winner w) {
        return new OrderedJudgement(a, b, new PreferenceVote(w, "test", 1.0));
    }

    private MatchedPairScore build(OrderedJudgement o1, OrderedJudgement o2) {
        return MatchedPairScore.build("eval-000", "CASHBACK", LlmProviderId.OPENAI_GPT4O_MINI, o1, o2);
    }

    @Test
    void groundedWinsBothOrders_scoreOne() {
        // Ordering 1: grounded as A, vote A => grounded wins
        // Ordering 2: grounded as B, vote B => grounded wins
        var o1 = order(NarratorVariant.GROUNDED, NarratorVariant.UNGROUNDED, PreferenceVote.Winner.A);
        var o2 = order(NarratorVariant.UNGROUNDED, NarratorVariant.GROUNDED, PreferenceVote.Winner.B);
        var s = build(o1, o2);
        assertEquals(1.0, s.groundedPreferenceScore(), 1e-9);
        assertTrue(s.strictConsistentGroundedWin());
        assertFalse(s.strictConsistentUngroundedWin());
        assertFalse(s.inconsistent());
    }

    @Test
    void groundedWinsOnce_tieOnce_scoreThreeQuarters() {
        var o1 = order(NarratorVariant.GROUNDED, NarratorVariant.UNGROUNDED, PreferenceVote.Winner.A);  // G wins
        var o2 = order(NarratorVariant.UNGROUNDED, NarratorVariant.GROUNDED, PreferenceVote.Winner.TIE);
        var s = build(o1, o2);
        assertEquals(0.75, s.groundedPreferenceScore(), 1e-9);
        assertFalse(s.inconsistent());
    }

    @Test
    void tieBothOrders_scoreHalf() {
        var o1 = order(NarratorVariant.GROUNDED, NarratorVariant.UNGROUNDED, PreferenceVote.Winner.TIE);
        var o2 = order(NarratorVariant.UNGROUNDED, NarratorVariant.GROUNDED, PreferenceVote.Winner.TIE);
        var s = build(o1, o2);
        assertEquals(0.5, s.groundedPreferenceScore(), 1e-9);
        assertFalse(s.inconsistent());
    }

    @Test
    void inconsistentJudgments_scoreHalf_andFlagInconsistent() {
        // G wins one order, U wins the other - the judge flipped
        var o1 = order(NarratorVariant.GROUNDED, NarratorVariant.UNGROUNDED, PreferenceVote.Winner.A);  // G wins
        var o2 = order(NarratorVariant.UNGROUNDED, NarratorVariant.GROUNDED, PreferenceVote.Winner.A);  // U wins (A=U)
        var s = build(o1, o2);
        assertEquals(0.5, s.groundedPreferenceScore(), 1e-9);
        assertTrue(s.inconsistent(), "judge contradicted itself across orderings");
    }

    @Test
    void ungroundedWinsOnce_tieOnce_scoreOneQuarter() {
        var o1 = order(NarratorVariant.GROUNDED, NarratorVariant.UNGROUNDED, PreferenceVote.Winner.B);  // U wins
        var o2 = order(NarratorVariant.UNGROUNDED, NarratorVariant.GROUNDED, PreferenceVote.Winner.TIE);
        var s = build(o1, o2);
        assertEquals(0.25, s.groundedPreferenceScore(), 1e-9);
        assertFalse(s.inconsistent());
    }

    @Test
    void ungroundedWinsBothOrders_scoreZero() {
        var o1 = order(NarratorVariant.GROUNDED, NarratorVariant.UNGROUNDED, PreferenceVote.Winner.B);  // U wins
        var o2 = order(NarratorVariant.UNGROUNDED, NarratorVariant.GROUNDED, PreferenceVote.Winner.A);  // U wins
        var s = build(o1, o2);
        assertEquals(0.0, s.groundedPreferenceScore(), 1e-9);
        assertTrue(s.strictConsistentUngroundedWin());
        assertFalse(s.strictConsistentGroundedWin());
    }

    @Test
    void positionABiasIsNotFalselyFlaggedAsGroundedWin() {
        // Pretend judge always picks A regardless of variant
        // Ordering 1: G as A, judge picks A => G "wins"
        // Ordering 2: U as A, judge picks A => U "wins"
        // Avg score should be 0.5 (inconsistent), revealing the bias
        var o1 = order(NarratorVariant.GROUNDED, NarratorVariant.UNGROUNDED, PreferenceVote.Winner.A);
        var o2 = order(NarratorVariant.UNGROUNDED, NarratorVariant.GROUNDED, PreferenceVote.Winner.A);
        var s = build(o1, o2);
        assertEquals(0.5, s.groundedPreferenceScore(), 1e-9,
            "pure position-A bias should yield 0.5, not 1.0");
        assertTrue(s.inconsistent());
    }

    @Test
    void buildRejectsMisorderedFirstOrdering() {
        // orderingOne should have GROUNDED in A, but here it has UNGROUNDED in A.
        var bad = order(NarratorVariant.UNGROUNDED, NarratorVariant.GROUNDED, PreferenceVote.Winner.A);
        var ok2 = order(NarratorVariant.UNGROUNDED, NarratorVariant.GROUNDED, PreferenceVote.Winner.A);
        try {
            MatchedPairScore.build("p", "g", LlmProviderId.OPENAI_GPT4O_MINI, bad, ok2);
            org.junit.jupiter.api.Assertions.fail("expected IllegalArgumentException for misordered orderingOne");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("orderingOne"));
        }
    }

    @Test
    void buildRejectsMisorderedSecondOrdering() {
        var ok1 = order(NarratorVariant.GROUNDED, NarratorVariant.UNGROUNDED, PreferenceVote.Winner.A);
        // orderingTwo should have UNGROUNDED in A, but here it has GROUNDED in A.
        var bad = order(NarratorVariant.GROUNDED, NarratorVariant.UNGROUNDED, PreferenceVote.Winner.B);
        try {
            MatchedPairScore.build("p", "g", LlmProviderId.OPENAI_GPT4O_MINI, ok1, bad);
            org.junit.jupiter.api.Assertions.fail("expected IllegalArgumentException for misordered orderingTwo");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("orderingTwo"));
        }
    }

    @Test
    void positionBBiasIsNotFalselyFlaggedAsGroundedWin() {
        // Judge always picks B
        var o1 = order(NarratorVariant.GROUNDED, NarratorVariant.UNGROUNDED, PreferenceVote.Winner.B);  // U wins
        var o2 = order(NarratorVariant.UNGROUNDED, NarratorVariant.GROUNDED, PreferenceVote.Winner.B);  // G wins
        var s = build(o1, o2);
        assertEquals(0.5, s.groundedPreferenceScore(), 1e-9);
        assertTrue(s.inconsistent());
    }
}
