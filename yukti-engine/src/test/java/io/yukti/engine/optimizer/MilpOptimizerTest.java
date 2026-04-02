package io.yukti.engine.optimizer;

import io.yukti.catalog.ClasspathCatalogSource;
import io.yukti.core.api.Catalog;
import io.yukti.core.api.Optimizer;
import io.yukti.core.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MilpOptimizerTest {

    private Catalog catalog;
    private Optimizer optimizer;

    @BeforeEach
    void setUp() throws Exception {
        catalog = new ClasspathCatalogSource("catalog/catalog-v1.json").load("1.0");
        optimizer = new MilpOptimizer();
    }

    @Test
    void trivialProfile_producesOptimalResult() {
        OptimizationRequest request = makeRequest(
            Map.of(Category.OTHER, 5000.0),
            GoalType.CASHBACK, Period.ANNUAL);

        OptimizationResult r = optimizer.optimize(request, catalog);

        assertFalse(r.getPortfolioIds().isEmpty());
        assertTrue(r.getBreakdown().getNet().getAmount().compareTo(BigDecimal.ZERO) > 0);
        assertFalse(r.getEvidenceBlocks().isEmpty());
        assertTrue(r.getEvidenceBlocks().stream().anyMatch(e -> "WINNER_BY_CATEGORY".equals(e.getType())));
        assertTrue(r.getEvidenceBlocks().stream().anyMatch(e -> "RESULT_BREAKDOWN".equals(e.getType())));
        assertTrue(r.getEvidenceBlocks().stream().anyMatch(e -> "PORTFOLIO_SUMMARY".equals(e.getType())));
    }

    @Test
    void capHitProfile_emitsCapHitEvidence() {
        // GROCERIES $12000 exceeds amex-bcp $6000 cap
        OptimizationRequest request = makeRequest(
            Map.of(Category.GROCERIES, 12000.0, Category.OTHER, 1000.0),
            GoalType.CASHBACK, Period.ANNUAL);

        OptimizationResult r = optimizer.optimize(request, catalog);

        assertFalse(r.getPortfolioIds().isEmpty());
        assertTrue(r.getEvidenceBlocks().stream().anyMatch(e -> "CAP_HIT".equals(e.getType())),
            "Should emit CAP_HIT evidence when spend exceeds cap");
    }

    @Test
    void zeroSpendCategory_noAllocationOrEvidence() {
        // Only GROCERIES has spend; TRAVEL is zero
        OptimizationRequest request = makeRequest(
            Map.of(Category.GROCERIES, 5000.0),
            GoalType.CASHBACK, Period.ANNUAL);

        OptimizationResult r = optimizer.optimize(request, catalog);

        assertNull(r.getAllocation().get(Category.TRAVEL));
        assertTrue(r.getEvidenceBlocks().stream()
            .filter(e -> "WINNER_BY_CATEGORY".equals(e.getType()))
            .noneMatch(e -> "TRAVEL".equals(e.getCategory())));
    }

    @Test
    void determinism_sameInputProducesSameOutput() {
        OptimizationRequest request = makeRequest(
            Map.of(Category.GROCERIES, 6000.0, Category.DINING, 3000.0, Category.OTHER, 5000.0),
            GoalType.CASHBACK, Period.ANNUAL);

        OptimizationResult r1 = optimizer.optimize(request, catalog);
        OptimizationResult r2 = optimizer.optimize(request, catalog);

        assertEquals(r1.getPortfolioIds(), r2.getPortfolioIds());
        assertEquals(r1.getAllocation(), r2.getAllocation());
        assertEquals(r1.getBreakdown().getNet().getAmount(), r2.getBreakdown().getNet().getAmount());
        assertEquals(r1.getEvidenceBlocks().size(), r2.getEvidenceBlocks().size());
    }

    @Test
    void emptySpend_returnsEmpty() {
        SpendProfile profile = new SpendProfile(Period.ANNUAL, new EnumMap<>(Category.class));
        OptimizationRequest request = new OptimizationRequest(
            profile, UserGoal.of(GoalType.CASHBACK), UserConstraints.defaults(), Map.of());

        OptimizationResult r = optimizer.optimize(request, catalog);

        assertTrue(r.getPortfolioIds().isEmpty());
        assertTrue(r.getAllocation().isEmpty());
    }

    @Test
    void allGoalTypes_produceDifferentResults() {
        Map<Category, Double> spend = Map.of(
            Category.GROCERIES, 6000.0, Category.DINING, 3000.0,
            Category.TRAVEL, 5000.0, Category.OTHER, 4000.0);

        OptimizationResult cashback = optimizer.optimize(
            makeRequest(spend, GoalType.CASHBACK, Period.ANNUAL), catalog);
        OptimizationResult flex = optimizer.optimize(
            makeRequest(spend, GoalType.FLEX_POINTS, Period.ANNUAL), catalog);
        OptimizationResult program = optimizer.optimize(
            makeRequestWithPrimary(spend, GoalType.PROGRAM_POINTS, RewardCurrencyType.AVIOS), catalog);

        // At least some goals should produce different portfolios or net values
        boolean allSameNet = cashback.getBreakdown().getNet().equals(flex.getBreakdown().getNet())
            && flex.getBreakdown().getNet().equals(program.getBreakdown().getNet());
        assertFalse(allSameNet, "Different goals should produce different valuations");
    }

    @Test
    void monthlyProfile_annualizedCorrectly() {
        // Monthly $500 groceries = $6000 annual
        OptimizationRequest monthly = makeRequest(
            Map.of(Category.GROCERIES, 500.0, Category.OTHER, 100.0),
            GoalType.CASHBACK, Period.MONTHLY);
        OptimizationRequest annual = makeRequest(
            Map.of(Category.GROCERIES, 6000.0, Category.OTHER, 1200.0),
            GoalType.CASHBACK, Period.ANNUAL);

        OptimizationResult rMonthly = optimizer.optimize(monthly, catalog);
        OptimizationResult rAnnual = optimizer.optimize(annual, catalog);

        // Should produce same portfolio and same net value
        assertEquals(rMonthly.getPortfolioIds(), rAnnual.getPortfolioIds());
        assertEquals(0, rMonthly.getBreakdown().getNet().getAmount()
            .compareTo(rAnnual.getBreakdown().getNet().getAmount()),
            "Monthly and annual should produce same net after annualization");
    }

    @Test
    void feeBudgetEnforced() {
        OptimizationRequest request = new OptimizationRequest(
            new SpendProfile(Period.ANNUAL,
                Map.of(Category.GROCERIES, Money.usd(20000),
                    Category.DINING, Money.usd(15000),
                    Category.OTHER, Money.usd(10000))),
            UserGoal.of(GoalType.CASHBACK),
            new UserConstraints(5, Money.usd(100), true),
            Map.of());

        OptimizationResult r = optimizer.optimize(request, catalog);

        assertTrue(r.getBreakdown().getFees().getAmount().compareTo(BigDecimal.valueOf(100)) <= 0,
            "MILP must enforce fee budget: fees=" + r.getBreakdown().getFees());
    }

    @Test
    void fallbackGreedy_whenZeroFeeBudgetAndAllCardsHaveFees() {
        // Fee budget $0 with high spend — MILP may be INFEASIBLE if no free cards
        // can cover spend, or it may find free cards. Either way, verify solverStatus is set.
        OptimizationRequest request = new OptimizationRequest(
            new SpendProfile(Period.ANNUAL,
                Map.of(Category.GROCERIES, Money.usd(5000),
                    Category.OTHER, Money.usd(3000))),
            UserGoal.of(GoalType.CASHBACK),
            new UserConstraints(3, Money.usd(0), true),  // $0 fee budget
            Map.of());

        OptimizationResult r = optimizer.optimize(request, catalog);

        // Result should be non-null regardless of path taken
        assertNotNull(r.getSolverStatus());
        // If MILP is infeasible, we should get FALLBACK_GREEDY with evidence
        if (r.getSolverStatus() == SolverStatus.FALLBACK_GREEDY) {
            assertTrue(r.getEvidenceBlocks().stream()
                .anyMatch(e -> "SOLVER_FALLBACK".equals(e.getType())),
                "Fallback result must include SOLVER_FALLBACK evidence");
        }
        // If MILP finds free-card solution, status is OPTIMAL
        assertTrue(r.getSolverStatus() == SolverStatus.OPTIMAL
            || r.getSolverStatus() == SolverStatus.FALLBACK_GREEDY,
            "Status must be OPTIMAL or FALLBACK_GREEDY, was: " + r.getSolverStatus());
    }

    @Test
    void solverStatus_isOptimalOnNormalRequest() {
        OptimizationRequest request = makeRequest(
            Map.of(Category.GROCERIES, 6000.0, Category.DINING, 3000.0, Category.OTHER, 5000.0),
            GoalType.CASHBACK, Period.ANNUAL);

        OptimizationResult r = optimizer.optimize(request, catalog);

        assertEquals(SolverStatus.OPTIMAL, r.getSolverStatus(),
            "Normal requests should produce OPTIMAL status");
    }

    @Test
    void portfolioStopEvidence_emitted() {
        OptimizationRequest request = makeRequest(
            Map.of(Category.GROCERIES, 6000.0, Category.DINING, 3000.0, Category.OTHER, 5000.0),
            GoalType.CASHBACK, Period.ANNUAL);

        OptimizationResult r = optimizer.optimize(request, catalog);

        assertTrue(r.getEvidenceBlocks().stream().anyMatch(e -> "PORTFOLIO_STOP".equals(e.getType())),
            "Should emit PORTFOLIO_STOP evidence");
    }

    // --- Helper methods ---

    private OptimizationRequest makeRequest(Map<Category, Double> spend, GoalType goal, Period period) {
        Map<Category, Money> amounts = new EnumMap<>(Category.class);
        spend.forEach((cat, amt) -> amounts.put(cat, Money.usd(amt)));
        return new OptimizationRequest(
            new SpendProfile(period, amounts),
            UserGoal.of(goal),
            UserConstraints.defaults(),
            Map.of());
    }

    private OptimizationRequest makeRequestWithPrimary(Map<Category, Double> spend,
                                                        GoalType goal, RewardCurrencyType primary) {
        Map<Category, Money> amounts = new EnumMap<>(Category.class);
        spend.forEach((cat, amt) -> amounts.put(cat, Money.usd(amt)));
        return new OptimizationRequest(
            new SpendProfile(Period.ANNUAL, amounts),
            new UserGoal(goal, java.util.Optional.of(primary), java.util.List.of(), Map.of()),
            UserConstraints.defaults(),
            Map.of());
    }
}
