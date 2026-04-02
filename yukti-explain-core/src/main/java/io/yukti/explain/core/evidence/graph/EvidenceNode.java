package io.yukti.explain.core.evidence.graph;

import java.util.Objects;

/**
 * A single evidence node in the graph. Wraps one evidence block with a stable evidenceId.
 * Domain-independent: type and string fields only.
 */
public record EvidenceNode(
    String evidenceId,
    String type,
    String cardId,
    String category,
    String content
) {
    public EvidenceNode {
        Objects.requireNonNull(evidenceId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(cardId);
        Objects.requireNonNull(category);
        Objects.requireNonNull(content);
    }
}
