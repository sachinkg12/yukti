package io.yukti.evaluation.runner;

import io.yukti.core.domain.Category;
import io.yukti.core.domain.GoalType;
import io.yukti.core.explainability.StructuredExplanation;
import io.yukti.engine.explainability.llm.LlmProviderId;
import io.yukti.evaluation.hallucination.HallucinationReport;
import io.yukti.evaluation.judge.PreferenceJudge;
import io.yukti.evaluation.judge.PreferenceVote;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExplanationEvaluatorMatchedPairsTest {

    private PerInstanceEvaluation instance(String profileId, String goal, NarratorVariant variant,
                                           LlmProviderId modelId, String text) {
        return new PerInstanceEvaluation(
            profileId, goal, variant, modelId, text,
            new HallucinationReport(0, 0, new EnumMap<>(io.yukti.evaluation.taxonomy.FailureCategory.class), List.of()),
            List.of()
        );
    }

    @Test
    void buildMatchedPairsPairsGroundedAndUngrounded() {
        var evaluator = new ExplanationEvaluator();
        var instances = List.of(
            instance("p1", "CASHBACK", NarratorVariant.GROUNDED, LlmProviderId.OPENAI_GPT4O_MINI, "G text"),
            instance("p1", "CASHBACK", NarratorVariant.UNGROUNDED, LlmProviderId.OPENAI_GPT4O_MINI, "U text")
        );
        var pairs = evaluator.buildMatchedPairs(instances);
        assertEquals(1, pairs.size());
        assertEquals("p1", pairs.get(0).profileId());
        assertEquals("CASHBACK", pairs.get(0).goal());
        assertEquals(LlmProviderId.OPENAI_GPT4O_MINI, pairs.get(0).modelId());
    }

    @Test
    void buildMatchedPairsHandlesMultipleProfilesGoalsModels() {
        var evaluator = new ExplanationEvaluator();
        var instances = List.of(
            instance("p1", "CASHBACK", NarratorVariant.GROUNDED, LlmProviderId.OPENAI_GPT4O_MINI, "G1"),
            instance("p1", "CASHBACK", NarratorVariant.UNGROUNDED, LlmProviderId.OPENAI_GPT4O_MINI, "U1"),
            instance("p1", "CASHBACK", NarratorVariant.GROUNDED, LlmProviderId.ANTHROPIC_CLAUDE_HAIKU, "G2"),
            instance("p1", "CASHBACK", NarratorVariant.UNGROUNDED, LlmProviderId.ANTHROPIC_CLAUDE_HAIKU, "U2"),
            instance("p2", "FLEX_POINTS", NarratorVariant.GROUNDED, LlmProviderId.OPENAI_GPT4O_MINI, "G3"),
            instance("p2", "FLEX_POINTS", NarratorVariant.UNGROUNDED, LlmProviderId.OPENAI_GPT4O_MINI, "U3")
        );
        var pairs = evaluator.buildMatchedPairs(instances);
        assertEquals(3, pairs.size(), "one pair per (profile, goal, model) combo");
    }

    @Test
    void buildMatchedPairsSkipsOrphans() {
        // grounded exists but no matching ungrounded
        var evaluator = new ExplanationEvaluator();
        var instances = List.of(
            instance("p1", "CASHBACK", NarratorVariant.GROUNDED, LlmProviderId.OPENAI_GPT4O_MINI, "G text")
        );
        var pairs = evaluator.buildMatchedPairs(instances);
        assertEquals(0, pairs.size());
    }

    @Test
    void judgeMatchedPairsCallsJudgeTwiceWithSwappedOrder() {
        var evaluator = new ExplanationEvaluator();
        var instances = List.of(
            instance("p1", "CASHBACK", NarratorVariant.GROUNDED, LlmProviderId.OPENAI_GPT4O_MINI, "GROUNDED_TEXT"),
            instance("p1", "CASHBACK", NarratorVariant.UNGROUNDED, LlmProviderId.OPENAI_GPT4O_MINI, "UNGROUNDED_TEXT")
        );
        var pairs = evaluator.buildMatchedPairs(instances);

        // Recording judge: notes which text was in slot A on each call
        List<String> aSlotTexts = new ArrayList<>();
        PreferenceJudge recordingJudge = (ctx, a, b) -> {
            aSlotTexts.add(a);
            return new PreferenceVote(PreferenceVote.Winner.A, "rec", 0.5);
        };

        var scores = evaluator.judgeMatchedPairs(pairs, recordingJudge, 10, p -> "ctx");
        assertEquals(1, scores.size(), "one matched pair, one score");
        assertEquals(2, aSlotTexts.size(), "judge called twice (counterbalanced)");
        // First call: grounded as A
        assertEquals("GROUNDED_TEXT", aSlotTexts.get(0));
        // Second call: ungrounded as A (swapped)
        assertEquals("UNGROUNDED_TEXT", aSlotTexts.get(1));
    }

    @Test
    void judgeMatchedPairsRespectsLimit() {
        var evaluator = new ExplanationEvaluator();
        var instances = List.of(
            instance("p1", "CASHBACK", NarratorVariant.GROUNDED, LlmProviderId.OPENAI_GPT4O_MINI, "G1"),
            instance("p1", "CASHBACK", NarratorVariant.UNGROUNDED, LlmProviderId.OPENAI_GPT4O_MINI, "U1"),
            instance("p2", "CASHBACK", NarratorVariant.GROUNDED, LlmProviderId.OPENAI_GPT4O_MINI, "G2"),
            instance("p2", "CASHBACK", NarratorVariant.UNGROUNDED, LlmProviderId.OPENAI_GPT4O_MINI, "U2"),
            instance("p3", "CASHBACK", NarratorVariant.GROUNDED, LlmProviderId.OPENAI_GPT4O_MINI, "G3"),
            instance("p3", "CASHBACK", NarratorVariant.UNGROUNDED, LlmProviderId.OPENAI_GPT4O_MINI, "U3")
        );
        var pairs = evaluator.buildMatchedPairs(instances);
        int[] calls = {0};
        PreferenceJudge counter = (ctx, a, b) -> {
            calls[0]++;
            return new PreferenceVote(PreferenceVote.Winner.TIE, "", 0.5);
        };
        var scores = evaluator.judgeMatchedPairs(pairs, counter, 2, p -> "ctx");
        assertEquals(2, scores.size());
        assertEquals(4, calls[0], "limit=2 pairs * 2 orderings = 4 judge calls");
    }

    @Test
    void buildMatchedPairsProducesDeterministicOrderAcrossCalls() {
        // Same input → same output order, every time.
        var evaluator = new ExplanationEvaluator();
        var instances = List.of(
            instance("p5", "FLEX_POINTS", NarratorVariant.GROUNDED, LlmProviderId.ANTHROPIC_CLAUDE_HAIKU, "g"),
            instance("p5", "FLEX_POINTS", NarratorVariant.UNGROUNDED, LlmProviderId.ANTHROPIC_CLAUDE_HAIKU, "u"),
            instance("p2", "CASHBACK", NarratorVariant.GROUNDED, LlmProviderId.OPENAI_GPT4O_MINI, "g"),
            instance("p2", "CASHBACK", NarratorVariant.UNGROUNDED, LlmProviderId.OPENAI_GPT4O_MINI, "u"),
            instance("p9", "PROGRAM_POINTS", NarratorVariant.GROUNDED, LlmProviderId.GOOGLE_GEMINI_FLASH, "g"),
            instance("p9", "PROGRAM_POINTS", NarratorVariant.UNGROUNDED, LlmProviderId.GOOGLE_GEMINI_FLASH, "u")
        );
        var pairs1 = evaluator.buildMatchedPairs(instances);
        var pairs2 = evaluator.buildMatchedPairs(instances);
        var pairs3 = evaluator.buildMatchedPairs(instances);
        assertEquals(pairs1.size(), pairs2.size());
        for (int i = 0; i < pairs1.size(); i++) {
            assertEquals(pairs1.get(i).profileId(), pairs2.get(i).profileId(),
                "deterministic order: pair " + i + " profileId should match");
            assertEquals(pairs1.get(i).profileId(), pairs3.get(i).profileId());
        }
        // Lexicographic order by key: profileId|goal|modelId
        // Keys: "p2|CASHBACK|OPENAI...", "p5|FLEX_POINTS|ANTHROPIC...", "p9|PROGRAM_POINTS|GOOGLE..."
        assertEquals("p2", pairs1.get(0).profileId());
        assertEquals("p5", pairs1.get(1).profileId());
        assertEquals("p9", pairs1.get(2).profileId());
    }

    @Test
    void positionBiasedJudgeProducesInconsistentScores() {
        // Real-world: judge always picks A regardless of content.
        var evaluator = new ExplanationEvaluator();
        var instances = List.of(
            instance("p1", "CASHBACK", NarratorVariant.GROUNDED, LlmProviderId.OPENAI_GPT4O_MINI, "G"),
            instance("p1", "CASHBACK", NarratorVariant.UNGROUNDED, LlmProviderId.OPENAI_GPT4O_MINI, "U")
        );
        var pairs = evaluator.buildMatchedPairs(instances);
        PreferenceJudge alwaysA = (ctx, a, b) -> new PreferenceVote(PreferenceVote.Winner.A, "biased", 0.9);
        var scores = evaluator.judgeMatchedPairs(pairs, alwaysA, 10, p -> "ctx");
        assertEquals(1, scores.size());
        assertEquals(0.5, scores.get(0).groundedPreferenceScore(), 1e-9,
            "counterbalancing should reveal pure position bias as a 0.5 inconsistent score");
        assertTrue(scores.get(0).inconsistent());
    }
}
