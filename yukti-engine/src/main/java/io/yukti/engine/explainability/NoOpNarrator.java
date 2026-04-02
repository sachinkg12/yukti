package io.yukti.engine.explainability;

import io.yukti.core.explainability.Narrator;
import io.yukti.core.explainability.NarrationException;
import io.yukti.core.explainability.StructuredExplanation;
import io.yukti.explain.core.claims.Claim;

import java.util.List;

/**
 * No-op narrator: returns empty list so service always uses deterministic claims.
 */
public final class NoOpNarrator implements Narrator {
    @Override
    public List<Claim> narrate(StructuredExplanation structured) throws NarrationException {
        return List.of();
    }
}
