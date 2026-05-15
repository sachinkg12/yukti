package io.yukti.bench.explanation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yukti.core.explainability.StructuredExplanation;
import io.yukti.explain.core.evidence.graph.EvidenceGraph;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Prompt builders for the grounded and ungrounded narrator variants used in
 * evaluation. Kept as a single utility to make the prompt text auditable.
 */
public final class NarratorPrompts {

    private static final ObjectMapper OM = new ObjectMapper();

    private static final String CLAIM_SCHEMA = """
        Each claim must have these fields:
        - claimId (string)
        - claimType (one of: COMPARISON, THRESHOLD, ALLOCATION, ASSUMPTION, FEE_JUSTIFICATION, CAP_SWITCH)
        - text (string)
        - citedEvidenceIds (array of strings)
        - citedEntities (array of card ids or category names)
        - citedNumbers (array of number strings)
        Output ONLY a JSON array. No commentary, no markdown.
        Output format: [ {"claimId":"...","claimType":"COMPARISON","text":"...","citedEvidenceIds":[],"citedEntities":[],"citedNumbers":[]} ]
        """;

    private NarratorPrompts() {}

    public static String groundedPrompt(StructuredExplanation evidence, EvidenceGraph graph) {
        // The bench previously handed the model only the structured evidence JSON
        // and the evidence-id list. The model would then paraphrase entity names
        // ("Amex Blue Cash" vs "amex-bcp") and round numbers, tripping gates 2 and
        // 3 even though the verifier-allowed strings were available somewhere in
        // the JSON. We now surface allowedEntities and allowedNumbers verbatim so
        // citedEntities / citedNumbers can be filled with strings the ClaimVerifier
        // actually accepts. The verifier-side semantics are unchanged; this is a
        // methodology fix to operationalise the allowlists in the eval prompt.
        String evidenceJson = safeJson(evidence);
        String evidenceIdsLine = String.join(", ", evidence.evidenceIds());
        String allowedEntitiesLine = String.join(", ", sortedCopy(graph.getAllowedEntities()));
        String allowedNumbersLine = String.join(", ", sortedCopy(graph.getAllowedNumbers()));
        return """
            You must output ONLY a valid JSON array of claim objects. Cite only the evidence IDs listed below.
            """ + CLAIM_SCHEMA + """

            Evidence IDs you may cite (use exactly these strings):
            """ + evidenceIdsLine + """

            For citedEntities, use ONLY these exact strings (card ids, category names, currencies):
            """ + allowedEntitiesLine + """

            For citedNumbers, use ONLY these exact strings (do not round, do not reformat; small ints 0-3 are also allowed):
            """ + allowedNumbersLine + """

            Structured evidence (for reference):
            """ + evidenceJson;
    }

    private static List<String> sortedCopy(java.util.Collection<String> in) {
        List<String> out = new ArrayList<>(in);
        out.sort(String::compareTo);
        return out;
    }

    public static String ungroundedPrompt(StructuredExplanation evidence) {
        // Mirrors the engine's UngroundedLlmNarrator summary: same four breakdown
        // fields (net, earn, credits, fees) are exposed. The grounded vs ungrounded
        // ablation should differ only in the evidence graph and allowlists, not
        // in which top line numbers the model sees.
        Map<String, Object> summary = Map.of(
            "goal", evidence.goalType().name(),
            "portfolio", evidence.portfolioCardIds(),
            "primaryCurrency", evidence.primaryCurrencyOrNull() != null ? evidence.primaryCurrencyOrNull() : "",
            "netValueUsd", evidence.breakdown().netValueUsd().toPlainString(),
            "totalEarnValueUsd", evidence.breakdown().totalEarnValueUsd().toPlainString(),
            "totalCreditValueUsd", evidence.breakdown().totalCreditValueUsd().toPlainString(),
            "totalFeesUsd", evidence.breakdown().totalFeesUsd().toPlainString()
        );
        String summaryJson = safeJson(summary);
        return """
            Explain why this credit card portfolio is a good choice. Be specific. Cite card names,
            category earn rates, dollar amounts, and supporting reasons.
            """ + CLAIM_SCHEMA + """

            Portfolio summary:
            """ + summaryJson;
    }

    private static String safeJson(Object o) {
        try {
            return OM.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
