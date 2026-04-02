package io.yukti.engine.explainability;

import io.yukti.core.explainability.Narrator;
import io.yukti.core.explainability.NarrationException;
import io.yukti.core.explainability.StructuredExplanation;
import io.yukti.explain.core.claims.Claim;
import io.yukti.explain.core.claims.ClaimType;

import java.util.List;

/**
 * Test-only narrator: returns one THRESHOLD claim that cites first evidenceId so verification passes.
 * Not used in production; main code path uses only {@link OpenAiLlmProvider} when LLM is enabled.
 */
public final class FakeNarrator implements Narrator {
    public static final String OUTPUT = "AI_NARRATION_OK";

    @Override
    public List<Claim> narrate(StructuredExplanation structured) throws NarrationException {
        List<String> ids = structured.evidenceIds();
        List<String> cite = ids.isEmpty() ? List.of() : List.of(ids.get(0));
        return List.of(new Claim("fake-1", ClaimType.THRESHOLD, OUTPUT, cite, List.of(), List.of()));
    }
}
