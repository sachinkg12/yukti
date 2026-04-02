package io.yukti.engine.explainability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yukti.core.explainability.LlmProvider;
import io.yukti.core.explainability.StructuredExplanation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM-backed claim generator. Output must be JSON array of claim_v1 objects only.
 * Caller validates against schema and verifies against EvidenceGraph; on failure falls back to deterministic.
 */
public final class LlmClaimGeneratorImpl implements LlmClaimGenerator {

    private static final ObjectMapper OM = new ObjectMapper();

    private static final String PROMPT_V1 = """
        You must output ONLY a valid JSON array of claim objects (claim schema v1). No other text, no markdown.
        Each claim must have: claimId (string), claimType (COMPARISON|THRESHOLD|ALLOCATION|ASSUMPTION|FEE_JUSTIFICATION|CAP_SWITCH),
        normalizedFields (object with type-specific keys), citedEvidenceIds (array of strings), citedEntities (array), citedNumbers (array).
        You MUST only cite evidenceIds from the list below. You MUST only use entities and numbers from the provided data.
        Output format: [ {"claimId":"...","claimType":"COMPARISON","normalizedFields":{...},"citedEvidenceIds":[],"citedEntities":[],"citedNumbers":[]}, ... ]

        Evidence IDs you may cite:
        """;

    private static final Pattern CODE_BLOCK = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private final LlmProvider provider;

    public LlmClaimGeneratorImpl(LlmProvider provider) {
        this.provider = provider;
    }

    @Override
    public String generateClaimsJson(StructuredExplanation structured) throws LlmClaimException {
        String evidenceJson;
        try {
            evidenceJson = OM.writeValueAsString(structured);
        } catch (JsonProcessingException e) {
            throw new LlmClaimException("Failed to serialize structured explanation", e);
        }
        String evidenceIdsLine = String.join(", ", structured.evidenceIds());
        String prompt = PROMPT_V1 + evidenceIdsLine + "\n\nStructured data (for reference):\n" + evidenceJson;
        String output = provider.generate(prompt);
        if (output == null || output.isBlank()) {
            throw new LlmClaimException("Empty LLM output");
        }
        return extractJsonArray(output);
    }

    static String extractJsonArray(String output) {
        String trimmed = output.trim();
        Matcher m = CODE_BLOCK.matcher(trimmed);
        if (m.find()) return m.group(1).trim();
        int start = trimmed.indexOf('[');
        int end = trimmed.lastIndexOf(']');
        if (start >= 0 && end > start) return trimmed.substring(start, end + 1);
        return trimmed;
    }
}
