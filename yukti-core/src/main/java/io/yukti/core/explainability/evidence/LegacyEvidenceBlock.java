package io.yukti.core.explainability.evidence;

import java.util.Objects;

/**
 * Adapter for legacy string-based evidence. Implements EvidenceBlock for backward compatibility.
 */
public record LegacyEvidenceBlock(String type, String cardId, String category, String content) implements EvidenceBlock {
    public LegacyEvidenceBlock {
        Objects.requireNonNull(type);
        Objects.requireNonNull(cardId);
        Objects.requireNonNull(category);
        Objects.requireNonNull(content);
    }
}
