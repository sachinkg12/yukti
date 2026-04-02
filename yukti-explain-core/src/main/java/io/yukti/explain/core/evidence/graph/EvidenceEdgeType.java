package io.yukti.explain.core.evidence.graph;

/**
 * Edge semantics between evidence nodes. Domain-independent.
 */
public enum EvidenceEdgeType {
    /** Target node supports (backs) the source claim or result. */
    SUPPORTS,
    /** Source node depends on target (e.g. cap-hit depends on category winner). */
    DEPENDS_ON,
    /** Source contradicts target. */
    CONTRADICTS,
    /** Source refines (adds detail to) target. */
    REFINES
}
