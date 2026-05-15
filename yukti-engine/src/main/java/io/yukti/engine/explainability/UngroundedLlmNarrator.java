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
 * Ungrounded LLM narrator used as the hallucination evaluation baseline.
 *
 * <p>Unlike {@link LlmNarrator}, this narrator does not give the LLM the evidence graph,
 * the allowed entity list, or the allowed number list. It asks the model to write an
 * explanation directly from a high level summary of the optimizer output.
 *
 * <p>The output is parsed against the claim schema to enable apples to apples
 * comparison with the grounded narrator. Hallucinations are detected later by
 * checking each cited evidence id, entity, and number against the actual evidence
 * graph emitted by the solver.
 *
 * <p>This narrator must never be used in production. It is provided for evaluation only.
 */
public final class UngroundedLlmNarrator implements Narrator {
    private static final ObjectMapper OM = new ObjectMapper();

    private static final String CLAIM_SCHEMA_DESC = """
        Each claim must have: claimId (string), claimType (one of: COMPARISON, THRESHOLD, ALLOCATION, ASSUMPTION, FEE_JUSTIFICATION, CAP_SWITCH),
        text (string), citedEvidenceIds (array of strings), citedEntities (array of card ids or category names),
        citedNumbers (array of number strings).
        """;

    private static final String UNGROUNDED_PROMPT = """
        Explain why this credit card portfolio is a good choice. Be specific. Cite card names,
        category earn rates, dollar amounts, and any reasons that support the decision.
        Output ONLY a valid JSON array of claim objects. No markdown, no commentary.
        """ + CLAIM_SCHEMA_DESC + """

        Output format: [ {"claimId": "...", "claimType": "...", "text": "...", "citedEvidenceIds": [], "citedEntities": [], "citedNumbers": []}, ... ]

        Portfolio summary:
        """;

    private final LlmProvider provider;

    public UngroundedLlmNarrator(LlmProvider provider) {
        if (provider == null) throw new IllegalArgumentException("provider must be non-null");
        this.provider = provider;
    }

    @Override
    public List<Claim> narrate(StructuredExplanation structured) throws NarrationException {
        String summary = buildSummaryOnly(structured);
        String output = provider.generate(UNGROUNDED_PROMPT + summary);
        if (output == null || output.isBlank()) {
            throw new NarrationException("Empty narration output");
        }
        String json = extractJsonArray(output);
        try {
            return ClaimSchema.parseClaimsJson(json);
        } catch (IllegalArgumentException e) {
            throw new NarrationException("Ungrounded LLM output did not match claim schema: " + e.getMessage(), e);
        }
    }

    /**
     * Build a minimal summary that omits the evidence graph and allowed lists. The LLM
     * sees the optimizer's selected cards, goal, and the bottom line net value, but
     * must fabricate any supporting details that the grounded narrator would have
     * been given through the evidence graph.
     */
    private static String buildSummaryOnly(StructuredExplanation structured) {
        try {
            return OM.writeValueAsString(java.util.Map.of(
                "goal", structured.goalType().name(),
                "portfolio", structured.portfolioCardIds(),
                "primaryCurrency", structured.primaryCurrencyOrNull() != null ? structured.primaryCurrencyOrNull() : "",
                "netValueUsd", structured.breakdown().netValueUsd().toPlainString(),
                "totalEarnValueUsd", structured.breakdown().totalEarnValueUsd().toPlainString(),
                "totalCreditValueUsd", structured.breakdown().totalCreditValueUsd().toPlainString(),
                "totalFeesUsd", structured.breakdown().totalFeesUsd().toPlainString()
            ));
        } catch (JsonProcessingException e) {
            return "";
        }
    }

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
