package io.yukti.engine.explainability;

import io.yukti.core.domain.Category;
import io.yukti.core.domain.EvidenceBlock;
import io.yukti.core.domain.Money;
import io.yukti.core.domain.ObjectiveBreakdown;
import io.yukti.core.domain.OptimizationResult;
import io.yukti.explain.core.evidence.graph.EvidenceEdgeType;
import io.yukti.explain.core.evidence.graph.EvidenceGraphV1;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * YuktiEvidenceGraphAdapter: build from OptimizationResult fixture, node/edge count, deterministic digest snapshot.
 */
class YuktiEvidenceGraphAdapterTest {

    @Test
    void build_fromFixture_returnsGraph() {
        OptimizationResult result = fixedFixture();
        EvidenceGraphV1 graph = YuktiEvidenceGraphAdapter.build(result);
        assertNotNull(graph);
        assertNotNull(graph.getGraphDigest());
        assertEquals(64, graph.getGraphDigest().length());
        assertTrue(graph.getGraphDigest().matches("[0-9a-f]+"));
    }

    @Test
    void build_nodeCountAndEdgeCount() {
        OptimizationResult result = fixedFixture();
        EvidenceGraphV1 graph = YuktiEvidenceGraphAdapter.build(result);
        // 1 root + 7 evidence blocks = 8 nodes
        assertEquals(8, graph.getNodes().size());
        // Root SUPPORTS each of 7 evidence nodes = 7; CAP_HIT(GROCERIES) DEPENDS_ON WINNER_BY_CATEGORY(GROCERIES) = 1
        assertTrue(graph.getEdges().size() >= 7, "at least root->each evidence");
        long supportsFromRoot = graph.getEdges().stream()
                .filter(e -> e.edgeType() == EvidenceEdgeType.SUPPORTS)
                .count();
        assertEquals(7, supportsFromRoot);
    }

    @Test
    void build_deterministicDigest() {
        OptimizationResult result = fixedFixture();
        EvidenceGraphV1 g1 = YuktiEvidenceGraphAdapter.build(result);
        EvidenceGraphV1 g2 = YuktiEvidenceGraphAdapter.build(result);
        assertEquals(g1.getGraphDigest(), g2.getGraphDigest());
    }

    @Test
    void build_digestMatchesSnapshot() throws Exception {
        OptimizationResult result = fixedFixture();
        EvidenceGraphV1 graph = YuktiEvidenceGraphAdapter.build(result);
        String actualDigest = graph.getGraphDigest();
        String expected = loadSnapshotOrNull("yukti_evidence_graph_v1_digest.txt");
        if (expected == null) {
            fail("Snapshot missing. Create yukti-engine/src/test/resources/snapshots/yukti_evidence_graph_v1_digest.txt with content:\n" + actualDigest);
        }
        assertEquals(expected.trim(), actualDigest,
                "Graph digest must match snapshot; update snapshot if adapter or payload rules changed intentionally.");
    }

    @Test
    void build_allowedEntities_includePortfolioAndCategories() {
        OptimizationResult result = fixedFixture();
        EvidenceGraphV1 graph = YuktiEvidenceGraphAdapter.build(result);
        assertTrue(graph.getAllowedEntities().contains("amex-bcp"));
        assertTrue(graph.getAllowedEntities().contains("citi-double-cash"));
        assertTrue(graph.getAllowedEntities().contains("GROCERIES"));
        assertTrue(graph.getAllowedEntities().contains("DINING"));
        assertTrue(graph.getAllowedEntities().contains("OTHER"));
    }

    @Test
    void build_allowedNumbers_includeBreakdown() {
        OptimizationResult result = fixedFixture();
        EvidenceGraphV1 graph = YuktiEvidenceGraphAdapter.build(result);
        // Breakdown: 450, 50, 95, net 405
        assertTrue(graph.getAllowedNumbers().contains("450"));
        assertTrue(graph.getAllowedNumbers().contains("50"));
        assertTrue(graph.getAllowedNumbers().contains("95"));
        assertTrue(graph.getAllowedNumbers().contains("405"));
    }

    private static OptimizationResult fixedFixture() {
        List<String> portfolio = List.of("amex-bcp", "citi-double-cash");
        Map<Category, String> allocation = Map.of(
                Category.GROCERIES, "amex-bcp",
                Category.DINING, "amex-bcp",
                Category.OTHER, "citi-double-cash"
        );
        ObjectiveBreakdown breakdown = new ObjectiveBreakdown(
                Money.usd("450"),
                Money.usd("50"),
                Money.usd("95")
        );
        List<EvidenceBlock> blocks = List.of(
                new EvidenceBlock("WINNER_BY_CATEGORY", "amex-bcp", "GROCERIES", "amex-bcp wins GROCERIES over amex-bce: delta $60.00"),
                new EvidenceBlock("WINNER_BY_CATEGORY", "amex-bcp", "DINING", "amex-bcp wins DINING: delta $30.00"),
                new EvidenceBlock("WINNER_BY_CATEGORY", "citi-double-cash", "OTHER", "citi-double-cash wins OTHER: delta $20.00"),
                new EvidenceBlock("CAP_HIT", "amex-bcp", "GROCERIES", "cap $6000, applied $6000, remaining $2000, fallback amex-bce"),
                new EvidenceBlock("FEE_BREAK_EVEN", "amex-bcp", "", "amex-bcp: fee $95, credits $0, net delta $155"),
                new EvidenceBlock("PORTFOLIO_STOP", "", "", "MAX_CARDS_REACHED: Reached maxCards=3"),
                new EvidenceBlock("ASSUMPTION", "valuation", "valuation", "Goal: CASHBACK, cpp: {USD=1.0}")
        );
        return new OptimizationResult(portfolio, allocation, breakdown, blocks, "Narrative.", List.of());
    }

    private static String loadSnapshotOrNull(String name) {
        try (var in = YuktiEvidenceGraphAdapterTest.class.getClassLoader().getResourceAsStream("snapshots/" + name)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
