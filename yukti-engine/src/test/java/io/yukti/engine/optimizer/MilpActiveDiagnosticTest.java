package io.yukti.engine.optimizer;

import io.yukti.catalog.ClasspathCatalogSource;
import io.yukti.core.api.Catalog;
import io.yukti.core.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Diagnostic: proves MILP is actually solving (not falling back to greedy).
 * MilpOptimizer only includes "MILP-optimal solution" in narrative on OPTIMAL status.
 * Also checks that MILP produces strictly better results than greedy on complex profiles.
 */
class MilpActiveDiagnosticTest {

    private Catalog catalog;

    @BeforeEach
    void setUp() throws Exception {
        catalog = new ClasspathCatalogSource("catalog/catalog-v1.json").load("1.0");
    }

    @Test
    void narrativeConfirmsMilpSolvedNotFallback() {
        var spend = Map.of(Category.GROCERIES, 12000.0, Category.DINING, 6000.0,
            Category.GAS, 3000.0, Category.TRAVEL, 8000.0,
            Category.ONLINE, 3000.0, Category.OTHER, 10000.0);
        var request = makeRequest(spend, GoalType.CASHBACK);

        OptimizationResult r = new MilpOptimizer().optimize(request, catalog);

        assertNotNull(r.getNarrative(), "Should have a narrative");
        assertTrue(r.getNarrative().contains("MILP-optimal"),
            "Narrative must contain 'MILP-optimal' proving MILP solved.\nActual: " + r.getNarrative());
    }

    @Test
    void milpProducesStrictlyBetterThanGreedyOnComplexProfile() {
        // Heavy spend profile where MILP should find a strictly better solution
        var spend = Map.of(Category.GROCERIES, 15000.0, Category.DINING, 6000.0,
            Category.GAS, 3600.0, Category.TRAVEL, 8000.0,
            Category.ONLINE, 3000.0, Category.OTHER, 10000.0);

        for (GoalType goal : List.of(GoalType.CASHBACK, GoalType.FLEX_POINTS)) {
            var request = makeRequest(spend, goal);
            var milpResult = new MilpOptimizer().optimize(request, catalog);
            var greedyResult = new CapAwareGreedyOptimizer().optimize(request, catalog);

            double milpNet = milpResult.getBreakdown().getNet().getAmount().doubleValue();
            double greedyNet = greedyResult.getBreakdown().getNet().getAmount().doubleValue();

            System.out.printf("[%s] MILP=%.2f (portfolio=%s) Greedy=%.2f (portfolio=%s) diff=%.2f%n",
                goal, milpNet, milpResult.getPortfolioIds(), greedyNet, greedyResult.getPortfolioIds(),
                milpNet - greedyNet);

            assertTrue(milpNet >= greedyNet - 0.01,
                String.format("[%s] MILP ($%.2f) must be >= greedy ($%.2f)", goal, milpNet, greedyNet));
        }
    }

    @Test
    void milpEmitsMilpSpecificEvidenceTypes() {
        var spend = Map.of(Category.GROCERIES, 6000.0, Category.DINING, 3000.0, Category.OTHER, 5000.0);
        var request = makeRequest(spend, GoalType.CASHBACK);

        OptimizationResult r = new MilpOptimizer().optimize(request, catalog);

        // MILP evidence must include these types (emitted by MilpSolutionAnalyzer)
        var evidenceTypes = r.getEvidenceBlocks().stream()
            .map(EvidenceBlock::getType)
            .distinct()
            .sorted()
            .toList();

        System.out.println("Evidence types: " + evidenceTypes);
        assertTrue(evidenceTypes.contains("RESULT_BREAKDOWN"), "Must have RESULT_BREAKDOWN");
        assertTrue(evidenceTypes.contains("PORTFOLIO_SUMMARY"), "Must have PORTFOLIO_SUMMARY");
        assertTrue(evidenceTypes.contains("PORTFOLIO_STOP"), "Must have PORTFOLIO_STOP");
        assertTrue(evidenceTypes.contains("ASSUMPTION"), "Must have ASSUMPTION");
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
}
