package io.yukti.explain.core.evidence.graph;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * EvidenceGraph v1: nodes (EvidenceItem by id), edges, allowed entities/numbers, graphDigest.
 * Immutable. Domain-independent.
 */
public final class EvidenceGraphV1 {

    private final Map<String, EvidenceItem> nodes;
    private final List<EvidenceEdge> edges;
    private final SortedSet<String> allowedEntities;
    private final SortedSet<String> allowedNumbers;
    private final String graphDigest;

    public EvidenceGraphV1(
            Map<String, EvidenceItem> nodes,
            List<EvidenceEdge> edges,
            SortedSet<String> allowedEntities,
            SortedSet<String> allowedNumbers,
            String graphDigest
    ) {
        this.nodes = nodes != null ? Collections.unmodifiableMap(new java.util.LinkedHashMap<>(nodes)) : Map.of();
        this.edges = edges != null ? List.copyOf(edges) : List.of();
        this.allowedEntities = allowedEntities != null ? Collections.unmodifiableSortedSet(new TreeSet<>(allowedEntities)) : new TreeSet<>();
        this.allowedNumbers = allowedNumbers != null ? Collections.unmodifiableSortedSet(new TreeSet<>(allowedNumbers)) : new TreeSet<>();
        this.graphDigest = Objects.requireNonNull(graphDigest);
    }

    public Map<String, EvidenceItem> getNodes() { return nodes; }
    public List<EvidenceEdge> getEdges() { return edges; }
    public SortedSet<String> getAllowedEntities() { return allowedEntities; }
    public SortedSet<String> getAllowedNumbers() { return allowedNumbers; }
    public String getGraphDigest() { return graphDigest; }

    /**
     * Union of all entities from the given evidence items (sorted).
     */
    public static SortedSet<String> allowedEntitiesFromNodes(Map<String, EvidenceItem> nodes) {
        SortedSet<String> out = new TreeSet<>();
        if (nodes != null) {
            for (EvidenceItem item : nodes.values()) {
                out.addAll(item.getEntities());
            }
        }
        return out;
    }

    /**
     * Union of all numbers from the given evidence items (sorted).
     */
    public static SortedSet<String> allowedNumbersFromNodes(Map<String, EvidenceItem> nodes) {
        SortedSet<String> out = new TreeSet<>();
        if (nodes != null) {
            for (EvidenceItem item : nodes.values()) {
                out.addAll(item.getNumbers());
            }
        }
        return out;
    }
}
