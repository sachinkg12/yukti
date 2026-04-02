package io.yukti.engine.explainability;

import io.yukti.core.domain.*;
import io.yukti.explain.core.evidence.graph.EvidenceEdgeType;
import io.yukti.explain.core.evidence.graph.EvidenceGraph;
import io.yukti.explain.core.evidence.graph.EvidenceNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EvidenceGraphBuilderTest {

    @Test
    void build_sameResult_producesSameDigest() {
        OptimizationResult result = sampleResult();
        EvidenceGraphBuilder builder = new EvidenceGraphBuilder();
        EvidenceGraph g1 = builder.build(result);
        EvidenceGraph g2 = builder.build(result);
        assertEquals(g1.getDigest(), g2.getDigest());
    }

    @Test
    void build_deterministicIds() {
        OptimizationResult result = sampleResult();
        EvidenceGraphBuilder builder = new EvidenceGraphBuilder();
        EvidenceGraph g1 = builder.build(result);
        EvidenceGraph g2 = builder.build(result);
        assertEquals(g1.getEvidenceIds(), g2.getEvidenceIds());
        assertEquals(g1.getNodes().size(), g2.getNodes().size());
        assertEquals(g1.getEdges().size(), g2.getEdges().size());
    }

    @Test
    void build_containsRootAndEvidenceNodes() {
        OptimizationResult result = sampleResult();
        EvidenceGraph graph = new EvidenceGraphBuilder().build(result);
        Set<String> nodeIds = graph.getNodes().stream().map(EvidenceNode::evidenceId).collect(java.util.stream.Collectors.toSet());
        assertTrue(nodeIds.contains(EvidenceGraph.rootEvidenceId()));
        assertTrue(graph.getNodes().size() > 1);
        assertTrue(graph.getEvidenceIds().size() >= result.getEvidenceBlocks().size());
    }

    @Test
    void build_completeness_requiredNodesForEachChosenCardAndCategoryWithSpend() {
        OptimizationResult result = sampleResult();
        EvidenceGraph graph = new EvidenceGraphBuilder().build(result);
        Set<String> allowedEntities = graph.getAllowedEntities();
        for (String cardId : result.getPortfolioIds()) {
            assertTrue(allowedEntities.contains(cardId), "allowedEntities must contain chosen card: " + cardId);
        }
        for (Category cat : result.getAllocation().keySet()) {
            assertTrue(allowedEntities.contains(cat.name()), "allowedEntities must contain category with spend: " + cat);
        }
        for (EvidenceBlock eb : result.getEvidenceBlocks()) {
            if (eb.getCardId() != null && !eb.getCardId().isEmpty()) {
                assertTrue(allowedEntities.contains(eb.getCardId()), "allowedEntities must contain card from evidence: " + eb.getCardId());
            }
            if (eb.getCategory() != null && !eb.getCategory().isEmpty()) {
                assertTrue(allowedEntities.contains(eb.getCategory()), "allowedEntities must contain category from evidence: " + eb.getCategory());
            }
        }
    }

    @Test
    void build_digestNonEmptyAndHex() {
        OptimizationResult result = sampleResult();
        EvidenceGraph graph = new EvidenceGraphBuilder().build(result);
        String digest = graph.getDigest();
        assertNotNull(digest);
        assertFalse(digest.isEmpty());
        assertEquals(64, digest.length());
        assertTrue(digest.matches("[0-9a-f]{64}"));
    }

    @Test
    void build_allowedNumbers_containsBreakdown() {
        OptimizationResult result = sampleResult();
        EvidenceGraph graph = new EvidenceGraphBuilder().build(result);
        Set<String> numbers = graph.getAllowedNumbers();
        assertFalse(numbers.isEmpty());
        ObjectiveBreakdown b = result.getBreakdown();
        assertTrue(numbers.contains(b.getEarnValue().getAmount().toPlainString()));
        assertTrue(numbers.contains(b.getCreditsValue().getAmount().toPlainString()));
        assertTrue(numbers.contains(b.getFees().getAmount().toPlainString()));
    }

    @Test
    void build_rootSupportsEveryEvidenceNode() {
        OptimizationResult result = sampleResult();
        EvidenceGraph graph = new EvidenceGraphBuilder().build(result);
        long supportsFromRoot = graph.getEdges().stream()
            .filter(e -> EvidenceGraph.rootEvidenceId().equals(e.fromEvidenceId()) && e.edgeType() == EvidenceEdgeType.SUPPORTS)
            .count();
        assertEquals(graph.getEvidenceIds().size(), supportsFromRoot);
    }

    private static OptimizationResult sampleResult() {
        List<String> portfolio = List.of("amex-bcp", "citi-double-cash");
        java.util.Map<Category, String> allocation = java.util.Map.of(
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
            new EvidenceBlock("FEE_BREAK_EVEN", "amex-bcp", "", "fee $95, credits $0, incremental earn $250, net delta $155"),
            new EvidenceBlock("PORTFOLIO_STOP", "", "", "MAX_CARDS_REACHED"),
            new EvidenceBlock("ASSUMPTION", "valuation", "valuation", "Goal: CASHBACK, cpp: {USD=1.0}")
        );
        return new OptimizationResult(portfolio, allocation, breakdown, blocks, "Narrative.", List.of());
    }
}
