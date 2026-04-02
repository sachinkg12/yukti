package io.yukti.engine.explainability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yukti.core.explainability.LlmProvider;
import io.yukti.core.explainability.Narrator;
import io.yukti.core.explainability.NarrationException;
import io.yukti.core.explainability.StructuredExplanation;
import io.yukti.explain.core.claims.Claim;
import io.yukti.explain.core.claims.ClaimSchema;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM narrator that must output machine-readable claims only (JSON array matching claim schema).
 * Verification of claims (evidenceIds, entities, numbers) is done by ClaimVerifier in the service.
 */
public final class LlmNarrator implements Narrator {
    private static final ObjectMapper OM = new ObjectMapper();

    private static final String CLAIM_SCHEMA_DESC = """
        Each claim object must have: claimId (string), claimType (one of: COMPARISON, THRESHOLD, ALLOCATION, ASSUMPTION, FEE_JUSTIFICATION, CAP_SWITCH),
        text (string), citedEvidenceIds (array of evidence id strings from the evidenceIds list below),
        citedEntities (array of card ids or categories mentioned in the claim), citedNumbers (array of number strings mentioned).
        You MUST only cite evidenceIds that appear in the evidenceIds list. You MUST only use entities and numbers from the provided data.
        """;

    private static final String GROUNDING_PROMPT = """
        You must output ONLY a valid JSON array of claim objects. No other text, no markdown, no explanation.
        """ + CLAIM_SCHEMA_DESC + """
        
        Output format: [ {"claimId": "...", "claimType": "...", "text": "...", "citedEvidenceIds": [], "citedEntities": [], "citedNumbers": []}, ... ]
        
        Evidence IDs you may cite (use exactly these strings):
        """;

    private final LlmProvider provider;

    public LlmNarrator(LlmProvider provider) {
        this.provider = provider;
    }

    @Override
    public List<Claim> narrate(StructuredExplanation structured) throws NarrationException {
        String evidenceJson;
        try {
            evidenceJson = OM.writeValueAsString(structured);
        } catch (JsonProcessingException e) {
            throw new NarrationException("Failed to serialize structured explanation", e);
        }
        String evidenceIdsLine = String.join(", ", structured.evidenceIds());
        String prompt = GROUNDING_PROMPT + evidenceIdsLine + "\n\nStructured data (for reference):\n" + evidenceJson;
        String output = provider.generate(prompt);
        if (output == null || output.isBlank()) {
            throw new NarrationException("Empty narration output");
        }
        String json = extractJsonArray(output);
        try {
            return ClaimSchema.parseClaimsJson(json);
        } catch (IllegalArgumentException e) {
            throw new NarrationException("LLM output did not match claim schema: " + e.getMessage(), e);
        }
    }

    /** Extract JSON array from output (handles optional markdown code fence). */
    private static String extractJsonArray(String output) {
        String trimmed = output.trim();
        Pattern codeBlock = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
        Matcher m = codeBlock.matcher(trimmed);
        if (m.find()) {
            return m.group(1).trim();
        }
        int start = trimmed.indexOf('[');
        int end = trimmed.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }
}
