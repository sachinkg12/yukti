package io.yukti.explain.core.evidence.graph;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

/**
 * Builds EvidenceGraph v1 from evidence items and edges.
 * Implementations may be domain adapters (e.g. from optimization result); core provides a default.
 */
public interface EvidenceGraphBuilder {

    /**
     * Build an EvidenceGraph v1 from the given items and edges.
     * Node map key is each item's evidenceId. allowedEntities and allowedNumbers are the union of all nodes' sets.
     * graphDigest is sha256 over canonical representation of nodes and edges.
     */
    EvidenceGraphV1 build(List<EvidenceItem> items, List<EvidenceEdge> edges);

    /**
     * Default implementation: builds nodes map by evidenceId, computes allowed sets from nodes, graphDigest from nodes and edges.
     */
    static EvidenceGraphBuilder defaultBuilder() {
        return (items, edges) -> {
            Map<String, EvidenceItem> nodes = new LinkedHashMap<>();
            if (items != null) {
                for (EvidenceItem item : items) {
                    nodes.put(item.getEvidenceId(), item);
                }
            }
            SortedSet<String> allowedEntities = EvidenceGraphV1.allowedEntitiesFromNodes(nodes);
            SortedSet<String> allowedNumbers = EvidenceGraphV1.allowedNumbersFromNodes(nodes);
            String graphDigest = EvidenceGraphDigest.computeGraphDigest(nodes, edges);
            return new EvidenceGraphV1(nodes, edges, allowedEntities, allowedNumbers, graphDigest);
        };
    }
}
