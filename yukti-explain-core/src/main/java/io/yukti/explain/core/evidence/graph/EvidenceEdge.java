package io.yukti.explain.core.evidence.graph;

import java.util.Objects;

/**
 * Directed edge between two evidence nodes. Domain-independent.
 */
public record EvidenceEdge(
    String fromEvidenceId,
    String toEvidenceId,
    EvidenceEdgeType edgeType
) {
    public EvidenceEdge {
        Objects.requireNonNull(fromEvidenceId);
        Objects.requireNonNull(toEvidenceId);
        Objects.requireNonNull(edgeType);
    }
}
