package io.yukti.bench.explanation;

import io.yukti.explain.core.claims.Claim;

import java.util.List;
import java.util.Objects;

/**
 * One LLM narration result with both the raw text and the leniently parsed claims.
 *
 * <p>The raw text is preserved even when claim parsing fails so that fluency metrics
 * still apply. Schema failures themselves count as a hallucination signal.
 */
public record RawNarration(String rawText, List<Claim> claims, boolean schemaFailed) {
    public RawNarration {
        Objects.requireNonNull(rawText);
        claims = claims != null ? List.copyOf(claims) : List.of();
    }
}
