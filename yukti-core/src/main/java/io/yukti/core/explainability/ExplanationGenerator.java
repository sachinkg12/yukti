package io.yukti.core.explainability;

import io.yukti.explain.core.claims.Claim;

import java.util.List;

/**
 * Generates narrative from structured explanation. Produces claims and rendered text.
 * renderFromClaims is used when verified LLM claims are used instead of deterministic claims.
 */
public interface ExplanationGenerator {
    NarrativeExplanation generate(StructuredExplanation structured);

    /**
     * Build narrative from a given list of claims (e.g. verified LLM output). Summary and allocation table from structured.
     */
    NarrativeExplanation renderFromClaims(StructuredExplanation structured, List<Claim> claims);
}
