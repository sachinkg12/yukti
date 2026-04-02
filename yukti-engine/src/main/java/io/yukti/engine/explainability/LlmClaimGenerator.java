package io.yukti.engine.explainability;

import io.yukti.core.explainability.StructuredExplanation;

/**
 * Optional LLM-backed claim generation. Returns raw JSON array of claims (schema v1).
 * Caller must validate JSON against claim_v1.schema.json and verify against EvidenceGraph;
 * if validation or verification fails, fallback to deterministic claims.
 */
public interface LlmClaimGenerator {

    /**
     * Generate claims as a JSON array string. Output must be parseable and each element
     * must conform to claim_v1 schema. No other text or markdown.
     *
     * @param structured structured explanation (evidence IDs, allocation, etc.) for grounding
     * @return JSON array string, e.g. "[{\"claimId\":\"...\", ...}, ...]"
     * @throws LlmClaimException if LLM call fails or returns empty
     */
    String generateClaimsJson(StructuredExplanation structured) throws LlmClaimException;
}
