package io.yukti.explain.core.evidence.graph;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EvidenceGraph v1: evidenceId determinism, graphDigest determinism, allowed sets = union of node sets.
 */
class EvidenceGraphV1Test {

    private static SortedSet<String> set(String... s) {
        SortedSet<String> out = new TreeSet<>();
        for (String x : s) out.add(x);
        return out;
    }

    @Test
    void evidenceId_isDeterministic() {
        EvidenceItem a = EvidenceItem.of("WINNER_BY_CATEGORY", "v1",
                Map.of("category", "GROCERIES", "cardId", "amex-bcp"),
                set("amex-bcp", "GROCERIES"),
                set("60.00"));
        EvidenceItem b = EvidenceItem.of("WINNER_BY_CATEGORY", "v1",
                Map.of("category", "GROCERIES", "cardId", "amex-bcp"),
                set("amex-bcp", "GROCERIES"),
                set("60.00"));
        assertEquals(a.getEvidenceId(), b.getEvidenceId());
        assertNotNull(a.getEvidenceId());
        assertEquals(64, a.getEvidenceId().length());
        assertTrue(a.getEvidenceId().matches("[0-9a-f]+"));
    }

    @Test
    void evidenceId_differentPayload_differentId() {
        EvidenceItem a = EvidenceItem.of("WINNER_BY_CATEGORY", "v1",
                Map.of("category", "GROCERIES"),
                set("amex-bcp"),
                set("60.00"));
        EvidenceItem b = EvidenceItem.of("WINNER_BY_CATEGORY", "v1",
                Map.of("category", "DINING"),
                set("amex-bcp"),
                set("60.00"));
        assertNotEquals(a.getEvidenceId(), b.getEvidenceId());
    }

    @Test
    void payloadDigest_isStable() {
        Map<String, Object> payload = Map.of("k", "v", "n", 42);
        EvidenceItem a = EvidenceItem.of("T", "v1", payload, set(), set());
        EvidenceItem b = EvidenceItem.of("T", "v1", payload, set(), set());
        assertEquals(a.getPayloadDigest(), b.getPayloadDigest());
        assertNotNull(a.getPayloadDigest());
        assertEquals(64, a.getPayloadDigest().length());
    }

    @Test
    void graphDigest_isDeterministic() {
        EvidenceItem n1 = EvidenceItem.of("A", "v1", Map.of("x", 1), set("e1"), set("1"));
        EvidenceItem n2 = EvidenceItem.of("B", "v1", Map.of("y", 2), set("e2"), set("2"));
        EvidenceGraphBuilder builder = EvidenceGraphBuilder.defaultBuilder();
        EvidenceGraphV1 g1 = builder.build(
                List.of(n1, n2),
                List.of(new EvidenceEdge(n1.getEvidenceId(), n2.getEvidenceId(), EvidenceEdgeType.SUPPORTS))
        );
        EvidenceGraphV1 g2 = builder.build(
                List.of(n1, n2),
                List.of(new EvidenceEdge(n1.getEvidenceId(), n2.getEvidenceId(), EvidenceEdgeType.SUPPORTS))
        );
        assertEquals(g1.getGraphDigest(), g2.getGraphDigest());
        assertNotNull(g1.getGraphDigest());
        assertEquals(64, g1.getGraphDigest().length());
        assertTrue(g1.getGraphDigest().matches("[0-9a-f]+"));
    }

    @Test
    void graphDigest_differentOrderOfItems_sameDigest() {
        EvidenceItem n1 = EvidenceItem.of("A", "v1", Map.of("x", 1), set("e1"), set("1"));
        EvidenceItem n2 = EvidenceItem.of("B", "v1", Map.of("y", 2), set("e2"), set("2"));
        EvidenceGraphBuilder builder = EvidenceGraphBuilder.defaultBuilder();
        EvidenceGraphV1 g1 = builder.build(List.of(n1, n2), List.of());
        EvidenceGraphV1 g2 = builder.build(List.of(n2, n1), List.of());
        assertEquals(g1.getGraphDigest(), g2.getGraphDigest());
    }

    @Test
    void allowedSets_includeUnionOfNodeSets() {
        EvidenceItem n1 = EvidenceItem.of("A", "v1", Map.of(), set("card1", "GROCERIES"), set("100", "200"));
        EvidenceItem n2 = EvidenceItem.of("B", "v1", Map.of(), set("card2", "GROCERIES"), set("200", "300"));
        EvidenceGraphV1 g = EvidenceGraphBuilder.defaultBuilder().build(List.of(n1, n2), List.of());
        assertTrue(g.getAllowedEntities().contains("card1"));
        assertTrue(g.getAllowedEntities().contains("card2"));
        assertTrue(g.getAllowedEntities().contains("GROCERIES"));
        assertEquals(3, g.getAllowedEntities().size());
        assertTrue(g.getAllowedNumbers().contains("100"));
        assertTrue(g.getAllowedNumbers().contains("200"));
        assertTrue(g.getAllowedNumbers().contains("300"));
        assertEquals(3, g.getAllowedNumbers().size());
    }

    @Test
    void nodesMap_keyIsEvidenceId() {
        EvidenceItem n1 = EvidenceItem.of("T", "v1", Map.of("a", 1), set("e"), set("1"));
        EvidenceGraphV1 g = EvidenceGraphBuilder.defaultBuilder().build(List.of(n1), List.of());
        assertEquals(1, g.getNodes().size());
        assertTrue(g.getNodes().containsKey(n1.getEvidenceId()));
        assertEquals(n1.getEvidenceId(), g.getNodes().get(n1.getEvidenceId()).getEvidenceId());
    }

    @Test
    void helper_allowedEntitiesFromNodes_union() {
        EvidenceItem n1 = EvidenceItem.of("A", "v1", Map.of(), set("x", "y"), set());
        EvidenceItem n2 = EvidenceItem.of("B", "v1", Map.of(), set("y", "z"), set());
        SortedSet<String> entities = EvidenceGraphV1.allowedEntitiesFromNodes(
                Map.of(n1.getEvidenceId(), n1, n2.getEvidenceId(), n2));
        assertEquals(set("x", "y", "z"), entities);
    }

    @Test
    void helper_allowedNumbersFromNodes_union() {
        EvidenceItem n1 = EvidenceItem.of("A", "v1", Map.of(), set(), set("1.0", "2.0"));
        EvidenceItem n2 = EvidenceItem.of("B", "v1", Map.of(), set(), set("2.0", "3.0"));
        SortedSet<String> numbers = EvidenceGraphV1.allowedNumbersFromNodes(
                Map.of(n1.getEvidenceId(), n1, n2.getEvidenceId(), n2));
        assertEquals(set("1.0", "2.0", "3.0"), numbers);
    }
}
