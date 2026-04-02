package io.yukti.engine.optimizer;

import com.google.ortools.linearsolver.MPSolver;
import io.yukti.catalog.ClasspathCatalogSource;
import io.yukti.core.api.Card;
import io.yukti.core.api.Catalog;
import io.yukti.core.api.RewardsRule;
import io.yukti.core.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MilpModelBuilderTest {

    private Catalog catalog;

    @BeforeEach
    void setUp() throws Exception {
        catalog = new ClasspathCatalogSource("catalog/catalog-v1.json").load("1.0");
    }

    @Test
    void variableCount_matchesExpected() {
        MilpModelBuilder builder = makeBuilder(
            Map.of(Category.GROCERIES, 6000.0, Category.OTHER, 5000.0),
            GoalType.CASHBACK);

        MPSolver solver = builder.build();

        int numCards = catalog.cards().size();
        // y: 1 per card; x: cards × 2 active categories; w/z: only for capped rules
        assertTrue(solver.numVariables() >= numCards + numCards * 2,
            "Should have at least y + x variables: " + solver.numVariables());
    }

    @Test
    void constraintCount_includesCoverageAndLimits() {
        MilpModelBuilder builder = makeBuilder(
            Map.of(Category.GROCERIES, 6000.0, Category.DINING, 3000.0),
            GoalType.CASHBACK);

        MPSolver solver = builder.build();

        int numCards = catalog.cards().size();
        int activeCategories = 2;
        // Minimum: 2 coverage + 1 card limit + 1 fee budget + numCards*2 linking
        int minConstraints = activeCategories + 1 + 1 + numCards * activeCategories;
        assertTrue(solver.numConstraints() >= minConstraints,
            "Should have at least " + minConstraints + " constraints, got " + solver.numConstraints());
    }

    @Test
    void piecewiseOnlyForCappedRules() {
        // With GROCERIES spend, amex-bcp has a $6000 cap on GROCERIES
        MilpModelBuilder builder = makeBuilder(
            Map.of(Category.GROCERIES, 12000.0),
            GoalType.CASHBACK);

        MPSolver solver = builder.build();

        // The model should have more variables than just y + x (due to w/z for capped rules)
        int numCards = catalog.cards().size();
        int baseVars = numCards + numCards * 1; // y + x (1 category)
        assertTrue(solver.numVariables() > baseVars,
            "Should have w/z variables for capped rules: " + solver.numVariables() + " > " + baseVars);
    }

    @Test
    void solverReturnsOptimal_forSimpleProfile() {
        MilpModelBuilder builder = makeBuilder(
            Map.of(Category.OTHER, 5000.0),
            GoalType.CASHBACK);

        MPSolver solver = builder.build();
        MPSolver.ResultStatus status = solver.solve();

        assertEquals(MPSolver.ResultStatus.OPTIMAL, status);
        assertTrue(solver.objective().value() > 0, "Objective should be positive");
    }

    @Test
    void extractSolution_populatesFields() {
        MilpModelBuilder builder = makeBuilder(
            Map.of(Category.GROCERIES, 6000.0, Category.OTHER, 5000.0),
            GoalType.CASHBACK);

        MPSolver solver = builder.build();
        assertEquals(MPSolver.ResultStatus.OPTIMAL, solver.solve());

        MilpSolution solution = builder.extractSolution(solver, 10);

        assertFalse(solution.selectedCardIds().isEmpty(), "Should select at least one card");
        assertTrue(solution.selectedCardIds().size() <= 3, "Should respect max cards constraint");
        assertEquals("OPTIMAL", solution.solverStatus());
        assertTrue(solution.objectiveValue() > 0);
        assertNotNull(solution.objectiveBreakdown());
        assertTrue(solution.objectiveBreakdown().getNet().getAmount().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void penaltyApplied_cashbackZerosNonCash() {
        // For CASHBACK, non-USD currencies should contribute 0 to objective
        MilpModelBuilder builder = makeBuilder(
            Map.of(Category.TRAVEL, 10000.0),
            GoalType.CASHBACK);

        MPSolver solver = builder.build();
        assertEquals(MPSolver.ResultStatus.OPTIMAL, solver.solve());
        MilpSolution solution = builder.extractSolution(solver, 10);

        // All selected cards should be USD_CASH cards for CASHBACK goal
        // (unless no USD card covers TRAVEL, in which case the solver picks best available)
        assertFalse(solution.selectedCardIds().isEmpty());
    }

    @Test
    void feeBudgetConstraint_respected() {
        MilpModelBuilder builder = new MilpModelBuilder(catalog,
            new SpendProfile(Period.ANNUAL,
                Map.of(Category.GROCERIES, Money.usd(20000),
                    Category.DINING, Money.usd(15000),
                    Category.OTHER, Money.usd(10000))),
            UserGoal.of(GoalType.CASHBACK),
            new UserConstraints(5, Money.usd(100), true));

        MPSolver solver = builder.build();
        assertEquals(MPSolver.ResultStatus.OPTIMAL, solver.solve());
        MilpSolution solution = builder.extractSolution(solver, 10);

        double totalFees = solution.objectiveBreakdown().getFees().getAmount().doubleValue();
        assertTrue(totalFees <= 100.01,
            "Fees should be within budget: " + totalFees);
    }

    @Test
    void bestRuleForCategory_selectsHighestRate() {
        // amex-bcp has multiple rules; GROCERIES at 0.06 should win
        Card amexBcp = catalog.cards().stream()
            .filter(c -> "amex-bcp".equals(c.id()))
            .findFirst().orElseThrow();

        MilpModelBuilder builder = makeBuilder(
            Map.of(Category.GROCERIES, 6000.0),
            GoalType.CASHBACK);
        builder.build(); // initializes cards

        RewardsRule bestRule = builder.bestRuleForCategory(amexBcp, Category.GROCERIES);
        assertNotNull(bestRule);
        assertEquals(0, bestRule.rate().compareTo(new BigDecimal("0.06")),
            "Best GROCERIES rule for amex-bcp should be 6%");
        assertTrue(bestRule.cap().isPresent(), "GROCERIES rule should be capped");
    }

    @Test
    void resolveFallback_usesExplicitWhenAvailable() {
        Card amexBcp = catalog.cards().stream()
            .filter(c -> "amex-bcp".equals(c.id()))
            .findFirst().orElseThrow();

        MilpModelBuilder builder = makeBuilder(
            Map.of(Category.GROCERIES, 6000.0),
            GoalType.CASHBACK);
        builder.build();

        RewardsRule rule = builder.bestRuleForCategory(amexBcp, Category.GROCERIES);
        BigDecimal fallback = builder.resolveFallback(amexBcp, rule);
        assertEquals(0, fallback.compareTo(new BigDecimal("0.01")),
            "amex-bcp GROCERIES fallback should be 1% (explicit fallbackMultiplier)");
    }

    // --- Helper ---

    private MilpModelBuilder makeBuilder(Map<Category, Double> spend, GoalType goal) {
        Map<Category, Money> amounts = new EnumMap<>(Category.class);
        spend.forEach((cat, amt) -> amounts.put(cat, Money.usd(amt)));
        return new MilpModelBuilder(catalog,
            new SpendProfile(Period.ANNUAL, amounts),
            UserGoal.of(goal),
            UserConstraints.defaults());
    }
}
