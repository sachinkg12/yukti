package io.yukti.core.explainability;

import io.yukti.explain.core.claims.Claim;

import java.util.List;

/**
 * Optional AI narrator. Must output machine-readable claims (JSON) that cite evidenceIds.
 * If output fails schema or verification, caller falls back to deterministic claims.
 */
public interface Narrator {
    /**
     * Produce claims from the structured explanation. Output must be valid JSON matching the claim schema.
     * @return list of claims (never null; empty if unable to produce)
     */
    List<Claim> narrate(StructuredExplanation structured) throws NarrationException;
}
