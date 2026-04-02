package io.yukti.explain.core.evidence.graph;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable evidence graph: nodes (one per evidence block), edges, allowed entities/numbers, and a digest.
 * Supports verifiable explanation generation: any claim can reference evidenceIds and be checked against this graph.
 * Domain-independent.
 */
public final class EvidenceGraph {

    private static final String ROOT_EVIDENCE_ID = "result";

    private final List<EvidenceNode> nodes;
    private final List<EvidenceEdge> edges;
    private final Set<String> allowedEntities;
    private final Set<String> allowedNumbers;
    private final String digest;

    public EvidenceGraph(
        List<EvidenceNode> nodes,
        List<EvidenceEdge> edges,
        Set<String> allowedEntities,
        Set<String> allowedNumbers,
        String digest
    ) {
        this.nodes = List.copyOf(Objects.requireNonNull(nodes));
        this.edges = List.copyOf(Objects.requireNonNull(edges));
        this.allowedEntities = Set.copyOf(Objects.requireNonNull(allowedEntities));
        this.allowedNumbers = Set.copyOf(Objects.requireNonNull(allowedNumbers));
        this.digest = Objects.requireNonNull(digest);
    }

    public List<EvidenceNode> getNodes() {
        return nodes;
    }

    public List<EvidenceEdge> getEdges() {
        return edges;
    }

    public Set<String> getAllowedEntities() {
        return allowedEntities;
    }

    public Set<String> getAllowedNumbers() {
        return allowedNumbers;
    }

    public String getDigest() {
        return digest;
    }

    /** Root node id used to link result to all evidence (SUPPORTS). */
    public static String rootEvidenceId() {
        return ROOT_EVIDENCE_ID;
    }

    /** Unmodifiable list of evidenceIds of evidence nodes only (excludes root). */
    public List<String> getEvidenceIds() {
        return nodes.stream()
            .filter(n -> !ROOT_EVIDENCE_ID.equals(n.evidenceId()))
            .map(EvidenceNode::evidenceId)
            .sorted()
            .toList();
    }
}
