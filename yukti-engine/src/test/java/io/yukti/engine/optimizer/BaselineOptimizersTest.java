package io.yukti.engine.optimizer;

import io.yukti.catalog.ClasspathCatalogSource;
import io.yukti.core.api.Catalog;
import io.yukti.core.api.Optimizer;
import io.yukti.core.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for baseline optimizers: ContentBasedTopK, TopKPopular, RandomK.
 * Validates: non-empty results, determinism, evidence emission, and MILP dominance.
 */
class BaselineOptimizersTest {

    private Catalog catalog;

    @BeforeEach
    void setUp() throws Exception {
        catalog = new ClasspathCatalogSource("catalog/catalog-v1.json").load("1.0");
    }

    private OptimizationRequest makeRequest(Map<Category, Double> spend, GoalType goal) {
        Map<Category, Money> amounts = new EnumMap<>(Category.class);
        spend.forEach((cat, amt) -> amounts.put(cat, Money.usd(amt)));
        UserGoal userGoal = goal == GoalType.PROGRAM_POINTS
            ? new UserGoal(GoalType.PROGRAM_POINTS, Optional.of(RewardCurrencyType.AVIOS), List.of(), Map.of())
            : UserGoal.of(goal);
        return new OptimizationRequest(
            new SpendProfile(Period.ANNUAL, amounts), userGoal, UserConstraints.defaults(), Map.of());
    }

    @Nested
    class ContentBasedTopKTests {

        private Optimizer optimizer;

        @BeforeEach
        void init() { optimizer = new ContentBasedTopKBaseline(); }

        @Test
        void id() { assertEquals("content-based-top3", optimizer.id()); }

        @Test
        void producesNonEmptyResult() {
            var request = makeRequest(Map.of(Category.GROCERIES, 6000.0, Category.OTHER, 5000.0), GoalType.CASHBACK);
            OptimizationResult r = optimizer.optimize(request, catalog);
            assertFalse(r.getPortfolioIds().isEmpty());
            assertTrue(r.getBreakdown().getNet().getAmount().compareTo(BigDecimal.ZERO) > 0);
        }

        @Test
        void deterministic() {
            var request = makeRequest(
                Map.of(Category.GROCERIES, 6000.0, Category.DINING, 3000.0, Category.OTHER, 5000.0),
                GoalType.CASHBACK);
            OptimizationResult r1 = optimizer.optimize(request, catalog);
            OptimizationResult r2 = optimizer.optimize(request, catalog);
            assertEquals(r1.getPortfolioIds(), r2.getPortfolioIds());
            assertEquals(r1.getBreakdown().getNet().getAmount(), r2.getBreakdown().getNet().getAmount());
        }

        @Test
        void emitsEvidence() {
            var request = makeRequest(Map.of(Category.GROCERIES, 6000.0, Category.OTHER, 5000.0), GoalType.CASHBACK);
            OptimizationResult r = optimizer.optimize(request, catalog);
            assertFalse(r.getEvidenceBlocks().isEmpty());
            assertTrue(r.getEvidenceBlocks().stream().anyMatch(e -> "ASSUMPTION".equals(e.getType())));
        }

        @Test
        void emptySpendReturnsEmpty() {
            var request = new OptimizationRequest(
                new SpendProfile(Period.ANNUAL, new EnumMap<>(Category.class)),
                UserGoal.of(GoalType.CASHBACK), UserConstraints.defaults(), Map.of());
            OptimizationResult r = optimizer.optimize(request, catalog);
            assertTrue(r.getPortfolioIds().isEmpty());
        }
    }

    @Nested
    class TopKPopularTests {

        private Optimizer optimizer;

        @BeforeEach
        void init() { optimizer = new TopKPopularBaseline(); }

        @Test
        void id() { assertEquals("top-k-popular", optimizer.id()); }

        @Test
        void producesNonEmptyResult() {
            var request = makeRequest(Map.of(Category.GROCERIES, 6000.0, Category.OTHER, 5000.0), GoalType.CASHBACK);
            OptimizationResult r = optimizer.optimize(request, catalog);
            assertFalse(r.getPortfolioIds().isEmpty());
        }

        @Test
        void samePortfolioForDifferentProfiles() {
            // Non-personalized: same cards regardless of spend distribution (for same goal)
            var reqA = makeRequest(Map.of(Category.GROCERIES, 20000.0, Category.OTHER, 1000.0), GoalType.CASHBACK);
            var reqB = makeRequest(Map.of(Category.TRAVEL, 20000.0, Category.OTHER, 1000.0), GoalType.CASHBACK);
            OptimizationResult rA = optimizer.optimize(reqA, catalog);
            OptimizationResult rB = optimizer.optimize(reqB, catalog);
            assertEquals(rA.getPortfolioIds(), rB.getPortfolioIds(),
                "Non-personalized baseline should select same cards for different profiles");
        }

        @Test
        void emitsEvidence() {
            var request = makeRequest(Map.of(Category.GROCERIES, 6000.0, Category.OTHER, 5000.0), GoalType.CASHBACK);
            OptimizationResult r = optimizer.optimize(request, catalog);
            assertFalse(r.getEvidenceBlocks().isEmpty());
        }
    }

    @Nested
    class RandomKTests {

        private Optimizer optimizer;

        @BeforeEach
        void init() { optimizer = new RandomKBaseline(); }

        @Test
        void id() { assertEquals("random-k", optimizer.id()); }

        @Test
        void producesNonEmptyResult() {
            var request = makeRequest(Map.of(Category.GROCERIES, 6000.0, Category.OTHER, 5000.0), GoalType.CASHBACK);
            OptimizationResult r = optimizer.optimize(request, catalog);
            assertFalse(r.getPortfolioIds().isEmpty());
        }

        @Test
        void deterministicWithSameSeed() {
            var request = makeRequest(Map.of(Category.GROCERIES, 6000.0, Category.OTHER, 5000.0), GoalType.CASHBACK);
            OptimizationResult r1 = new RandomKBaseline(42).optimize(request, catalog);
            OptimizationResult r2 = new RandomKBaseline(42).optimize(request, catalog);
            assertEquals(r1.getPortfolioIds(), r2.getPortfolioIds());
        }

        @Test
        void differentSeedsProduceDifferentPortfolios() {
            var request = makeRequest(
                Map.of(Category.GROCERIES, 6000.0, Category.DINING, 3000.0, Category.OTHER, 5000.0),
                GoalType.CASHBACK);
            OptimizationResult r1 = new RandomKBaseline(1).optimize(request, catalog);
            OptimizationResult r2 = new RandomKBaseline(999).optimize(request, catalog);
            // Very unlikely for two different seeds to produce same 3 cards from 28
            assertNotEquals(r1.getPortfolioIds(), r2.getPortfolioIds(),
                "Different seeds should usually produce different portfolios");
        }
    }

    @Nested
    class MilpDominanceTests {

        @Test
        void milpBeatsOrMatchesAllBaselines() {
            var request = makeRequest(
                Map.of(Category.GROCERIES, 12000.0, Category.DINING, 6000.0,
                    Category.GAS, 3000.0, Category.TRAVEL, 8000.0,
                    Category.ONLINE, 3000.0, Category.OTHER, 10000.0),
                GoalType.CASHBACK);

            Optimizer milp = new MilpOptimizer();
            double milpNet = milp.optimize(request, catalog).getBreakdown().getNet().getAmount().doubleValue();

            for (Optimizer baseline : List.of(
                    new ContentBasedTopKBaseline(),
                    new TopKPopularBaseline(),
                    new RandomKBaseline())) {
                double baselineNet = baseline.optimize(request, catalog)
                    .getBreakdown().getNet().getAmount().doubleValue();
                assertTrue(milpNet >= baselineNet - 0.01,
                    String.format("MILP ($%.2f) must >= %s ($%.2f)", milpNet, baseline.id(), baselineNet));
            }
        }
    }

    @Test
    void registryHasAllNineOptimizers() {
        OptimizerRegistry registry = new OptimizerRegistry();
        Set<String> ids = registry.availableIds();
        assertTrue(ids.contains("milp-v1"));
        assertTrue(ids.contains("lp-relaxation-v1"));
        assertTrue(ids.contains("cap-aware-greedy-v1"));
        assertTrue(ids.contains("greedy-v1"));
        assertTrue(ids.contains("exhaustive-search-v1"));
        assertTrue(ids.contains("single-best-per-category-v1"));
        assertTrue(ids.contains("content-based-top3"));
        assertTrue(ids.contains("top-k-popular"));
        assertTrue(ids.contains("random-k"));
        assertTrue(ids.contains("ahp-mcdm"));
        assertTrue(ids.contains("ahp-mcdm-fee-heavy"));
        assertTrue(ids.contains("ahp-mcdm-coverage-heavy"));
        assertTrue(ids.contains("ahp-pairwise"));
        assertTrue(ids.contains("rule-based"));
        assertTrue(ids.contains("sa-v1"));
        assertEquals(15, ids.size(), "Should have exactly 15 optimizers (13 benchmark + 2 AHP variants)");
    }
}
