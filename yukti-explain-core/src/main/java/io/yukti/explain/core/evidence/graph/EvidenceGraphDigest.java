package io.yukti.explain.core.evidence.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yukti.explain.core.canonical.DigestUtil;
import io.yukti.explain.core.canonical.YuktiCanonicalizer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;

/**
 * Computes graphDigest for EvidenceGraph v1 and converts maps to JsonNode for canonicalization.
 */
public final class EvidenceGraphDigest {

    private static final ObjectMapper OM = new ObjectMapper();

    private EvidenceGraphDigest() {}

    /**
     * Convert a map (and nested structures) to JsonNode for Yukti canonical JSON.
     * Only supports Map, List, String, Number, Boolean; other values are stringified.
     */
    @SuppressWarnings("unchecked")
    public static JsonNode toJsonNode(Object value) {
        if (value == null) return OM.getNodeFactory().nullNode();
        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            Map<String, Object> sorted = new TreeMap<>(map);
            ObjectNode node = OM.createObjectNode();
            for (Map.Entry<String, Object> e : sorted.entrySet()) {
                node.set(e.getKey(), toJsonNode(e.getValue()));
            }
            return node;
        }
        if (value instanceof Iterable && !(value instanceof SortedSet)) {
            List<Object> list = new ArrayList<>();
            for (Object o : (Iterable<?>) value) list.add(o);
            ArrayNode arr = OM.createArrayNode();
            for (Object o : list) arr.add(toJsonNode(o));
            return arr;
        }
        if (value instanceof SortedSet) {
            List<Object> list = new ArrayList<>((SortedSet<?>) value);
            ArrayNode arr = OM.createArrayNode();
            for (Object o : list) arr.add(toJsonNode(o));
            return arr;
        }
        if (value instanceof String) return OM.getNodeFactory().textNode((String) value);
        if (value instanceof Number) return OM.getNodeFactory().numberNode(((Number) value).doubleValue());
        if (value instanceof Boolean) return OM.getNodeFactory().booleanNode((Boolean) value);
        return OM.getNodeFactory().textNode(String.valueOf(value));
    }

    /**
     * Canonical representation of a single EvidenceItem for graph digest (id + payload digest + type/version/entities/numbers).
     */
    public static Map<String, Object> itemToCanonicalMap(EvidenceItem item) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("evidenceId", item.getEvidenceId());
        m.put("evidenceType", item.getEvidenceType());
        m.put("evidenceVersion", item.getEvidenceVersion());
        m.put("payloadDigest", item.getPayloadDigest());
        m.put("entities", new ArrayList<>(item.getEntities()));
        m.put("numbers", new ArrayList<>(item.getNumbers()));
        return m;
    }

    /**
     * graphDigest = sha256(canonical JSON of nodes and edges).
     * Nodes are sorted by evidenceId; edges sorted by fromEvidenceId, toEvidenceId, edgeType.
     */
    public static String computeGraphDigest(Map<String, EvidenceItem> nodes, List<EvidenceEdge> edges) {
        Map<String, Object> graph = new LinkedHashMap<>();
        Map<String, Object> nodesMap = new TreeMap<>();
        if (nodes != null) {
            for (Map.Entry<String, EvidenceItem> e : new TreeMap<>(nodes).entrySet()) {
                nodesMap.put(e.getKey(), itemToCanonicalMap(e.getValue()));
            }
        }
        graph.put("nodes", nodesMap);

        List<Map<String, Object>> edgeList = new ArrayList<>();
        if (edges != null) {
            List<EvidenceEdge> sorted = new ArrayList<>(edges);
            sorted.sort(java.util.Comparator
                    .comparing(EvidenceEdge::fromEvidenceId)
                    .thenComparing(EvidenceEdge::toEvidenceId)
                    .thenComparing(e -> e.edgeType().name()));
            for (EvidenceEdge edge : sorted) {
                Map<String, Object> em = new LinkedHashMap<>();
                em.put("fromEvidenceId", edge.fromEvidenceId());
                em.put("toEvidenceId", edge.toEvidenceId());
                em.put("edgeType", edge.edgeType().name());
                edgeList.add(em);
            }
        }
        graph.put("edges", edgeList);

        byte[] canonical = YuktiCanonicalizer.canonicalize(toJsonNode(graph));
        return DigestUtil.sha256Hex(canonical);
    }
}
