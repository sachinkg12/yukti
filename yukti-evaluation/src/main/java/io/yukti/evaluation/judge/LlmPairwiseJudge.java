package io.yukti.evaluation.judge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yukti.core.explainability.LlmProvider;

import java.util.Optional;

/**
 * LLM as judge. Parses a small JSON object from the LLM output. If parsing fails
 * the vote is recorded as a TIE with low confidence.
 *
 * <p>Extraction uses a brace balanced, string aware scanner (see
 * {@link JsonObjectExtractor}). The previous greedy regex broke whenever the
 * model emitted multiple JSON objects or prose containing curly braces.
 */
public final class LlmPairwiseJudge implements PreferenceJudge {

    private static final ObjectMapper OM = new ObjectMapper();

    private final LlmProvider provider;

    public LlmPairwiseJudge(LlmProvider provider) {
        if (provider == null) throw new IllegalArgumentException("provider must be non-null");
        this.provider = provider;
    }

    @Override
    public PreferenceVote judge(String contextSummary, String explanationA, String explanationB) {
        String prompt = JudgePromptBuilder.build(contextSummary, explanationA, explanationB);
        String output;
        try {
            output = provider.generate(prompt);
        } catch (RuntimeException e) {
            return new PreferenceVote(PreferenceVote.Winner.TIE, "judge_error: " + e.getMessage(), 0.0);
        }
        if (output == null || output.isBlank()) {
            return new PreferenceVote(PreferenceVote.Winner.TIE, "empty_output", 0.0);
        }
        Optional<String> extracted = JsonObjectExtractor.firstObject(output);
        if (extracted.isEmpty()) {
            return new PreferenceVote(PreferenceVote.Winner.TIE, "no_json_object", 0.0);
        }
        try {
            JsonNode node = OM.readTree(extracted.get());
            String winnerStr = node.path("winner").asText("TIE");
            String rationale = node.path("rationale").asText("");
            double confidence = node.path("confidence").asDouble(0.5);
            if (confidence < 0) confidence = 0;
            if (confidence > 1) confidence = 1;
            PreferenceVote.Winner winner = parseWinner(winnerStr);
            return new PreferenceVote(winner, rationale, confidence);
        } catch (Exception e) {
            return new PreferenceVote(PreferenceVote.Winner.TIE, "parse_error: " + e.getMessage(), 0.0);
        }
    }

    private static PreferenceVote.Winner parseWinner(String s) {
        if (s == null) return PreferenceVote.Winner.TIE;
        return switch (s.trim().toUpperCase()) {
            case "A" -> PreferenceVote.Winner.A;
            case "B" -> PreferenceVote.Winner.B;
            default -> PreferenceVote.Winner.TIE;
        };
    }
}
