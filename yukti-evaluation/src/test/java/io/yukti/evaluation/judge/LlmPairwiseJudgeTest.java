package io.yukti.evaluation.judge;

import io.yukti.engine.explainability.llm.MockLlmProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LlmPairwiseJudgeTest {

    @Test
    void parsesValidJsonResponse() {
        var provider = MockLlmProvider.single("{\"winner\": \"A\", \"rationale\": \"more concrete\", \"confidence\": 0.8}");
        var judge = new LlmPairwiseJudge(provider);
        var vote = judge.judge("ctx", "A text", "B text");
        assertEquals(PreferenceVote.Winner.A, vote.winner());
        assertEquals(0.8, vote.confidence(), 1e-9);
    }

    @Test
    void parsesEmbeddedJson() {
        var provider = MockLlmProvider.single("Sure, here is the answer: {\"winner\": \"B\", \"rationale\": \"more accurate\", \"confidence\": 0.9} and that's that.");
        var judge = new LlmPairwiseJudge(provider);
        var vote = judge.judge("ctx", "A", "B");
        assertEquals(PreferenceVote.Winner.B, vote.winner());
    }

    @Test
    void returnsTieOnMalformedOutput() {
        var provider = MockLlmProvider.single("no json here at all");
        var judge = new LlmPairwiseJudge(provider);
        var vote = judge.judge("ctx", "A", "B");
        assertEquals(PreferenceVote.Winner.TIE, vote.winner());
        assertEquals(0.0, vote.confidence(), 1e-9);
    }

    @Test
    void clampConfidenceOutOfRange() {
        var provider = MockLlmProvider.single("{\"winner\": \"A\", \"rationale\": \"\", \"confidence\": 1.5}");
        var judge = new LlmPairwiseJudge(provider);
        var vote = judge.judge("ctx", "A", "B");
        assertEquals(1.0, vote.confidence(), 1e-9);
    }
}
