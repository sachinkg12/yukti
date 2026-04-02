package io.yukti.engine.optimizer;

import io.yukti.catalog.ClasspathCatalogSource;
import io.yukti.core.api.Catalog;
import io.yukti.core.api.Optimizer;
import io.yukti.core.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cross-validates MILP and ExhaustiveSearch to confirm that after
 * fixing AllocationSolverV1_1's cap-period bug, both produce
 * equivalent net values on all profiles (including monthly).
 *
 * <p>The tolerance is $1.00 to account for floating-point rounding
 * differences between MILP (double) and AllocationSolverV1_1 (BigDecimal).
 */
@Tag("integration")
class MilpVsExhaustiveCrossValidationTest {

    private static final BigDecimal TOLERANCE = new BigDecimal("1.00");

    private Catalog catalog;
    private Optimizer milp;
    private Optimizer exhaustive;
    private List<ProfileGoal> testCases;

    @BeforeEach
    void setUp() throws Exception {
        catalog = new ClasspathCatalogSource("catalog/catalog-v1.json").load("1.0");
        milp = new MilpOptimizer();
        exhaustive = new ExhaustiveSearchOptimizer();
        testCases = buildTestCases();
    }

    @Test
    void milpMatchesExhaustiveOnAllProfiles() {
        int violations = 0;
        int total = 0;
        StringBuilder failures = new StringBuilder();

        for (ProfileGoal pg : testCases) {
            total++;
            OptimizationResult milpResult = milp.optimize(pg.request, catalog);
            OptimizationResult exhaustiveResult = exhaustive.optimize(pg.request, catalog);

            BigDecimal milpNet = milpResult.getBreakdown().getNet().getAmount();
            BigDecimal exhaustiveNet = exhaustiveResult.getBreakdown().getNet().getAmount();
            BigDecimal diff = milpNet.subtract(exhaustiveNet).abs();

            if (diff.compareTo(TOLERANCE) > 0) {
                violations++;
                failures.append(String.format(
                    "  %s/%s: MILP=$%.2f, Exhaustive=$%.2f, diff=$%.2f%n",
                    pg.profileId, pg.goal, milpNet, exhaustiveNet, diff));
            }
        }
        assertEquals(0, violations,
            String.format("MILP and Exhaustive should match within $%.2f on all %d profiles:\n%s",
                TOLERANCE, total, failures));
    }

    @Test
    void milpAlwaysDominatesExhaustive() {
        int violations = 0;
        StringBuilder failures = new StringBuilder();

        for (ProfileGoal pg : testCases) {
            OptimizationResult milpResult = milp.optimize(pg.request, catalog);
            OptimizationResult exhaustiveResult = exhaustive.optimize(pg.request, catalog);

            BigDecimal milpNet = milpResult.getBreakdown().getNet().getAmount();
            BigDecimal exhaustiveNet = exhaustiveResult.getBreakdown().getNet().getAmount();

            // MILP should always be >= Exhaustive (joint optimization vs two-stage)
            if (milpNet.add(TOLERANCE).compareTo(exhaustiveNet) < 0) {
                violations++;
                failures.append(String.format(
                    "  %s/%s: MILP=$%.2f < Exhaustive=$%.2f%n",
                    pg.profileId, pg.goal, milpNet, exhaustiveNet));
            }
        }
        assertEquals(0, violations,
            "MILP should be >= Exhaustive for all profiles:\n" + failures);
    }

    private List<ProfileGoal> buildTestCases() {
        List<ProfileGoal> cases = new ArrayList<>();
        List<BenchProfile> profiles = createBenchProfiles();

        for (BenchProfile p : profiles) {
            Map<Category, Money> amounts = new EnumMap<>(Category.class);
            p.spend.forEach((cat, amt) -> amounts.put(cat, Money.usd(amt)));
            SpendProfile sp = new SpendProfile(p.monthly ? Period.MONTHLY : Period.ANNUAL, amounts);

            for (GoalType goal : List.of(GoalType.CASHBACK, GoalType.FLEX_POINTS, GoalType.PROGRAM_POINTS)) {
                UserGoal userGoal = goal == GoalType.PROGRAM_POINTS
                    ? new UserGoal(GoalType.PROGRAM_POINTS, Optional.of(RewardCurrencyType.AVIOS), List.of(), Map.of())
                    : UserGoal.of(goal);
                OptimizationRequest request = new OptimizationRequest(
                    sp, userGoal, UserConstraints.defaults(), Map.of());
                cases.add(new ProfileGoal(p.id, goal, request));
            }
        }
        return cases;
    }

    private List<BenchProfile> createBenchProfiles() {
        List<BenchProfile> list = new ArrayList<>();
        // Annual profiles
        list.add(new BenchProfile("light", false, Map.of(Category.GROCERIES, 3000., Category.DINING, 1500., Category.GAS, 1200., Category.OTHER, 2500.)));
        list.add(new BenchProfile("moderate", false, Map.of(Category.GROCERIES, 6000., Category.DINING, 3000., Category.GAS, 2400., Category.TRAVEL, 2000., Category.ONLINE, 1200., Category.OTHER, 5000.)));
        list.add(new BenchProfile("heavy", false, Map.of(Category.GROCERIES, 15000., Category.DINING, 6000., Category.GAS, 3600., Category.TRAVEL, 8000., Category.ONLINE, 3000., Category.OTHER, 10000.)));
        list.add(new BenchProfile("balanced", false, Map.of(Category.GROCERIES, 5000., Category.DINING, 2500., Category.GAS, 2000., Category.TRAVEL, 2500., Category.ONLINE, 1500., Category.OTHER, 4000.)));
        list.add(new BenchProfile("minimal", false, Map.of(Category.OTHER, 5000.)));
        list.add(new BenchProfile("grocery-heavy", false, Map.of(Category.GROCERIES, 12000., Category.DINING, 2000., Category.OTHER, 3000.)));
        list.add(new BenchProfile("travel-heavy", false, Map.of(Category.TRAVEL, 15000., Category.DINING, 4000., Category.OTHER, 5000.)));
        list.add(new BenchProfile("groceries-cap6000-above", false, Map.of(Category.GROCERIES, 6100., Category.OTHER, 1000.)));
        list.add(new BenchProfile("groceries-cap6000-far", false, Map.of(Category.GROCERIES, 12000., Category.OTHER, 1000.)));
        // Monthly profiles (the formerly-failing cases)
        list.add(new BenchProfile("monthly-light", true, Map.of(Category.GROCERIES, 400., Category.DINING, 200., Category.OTHER, 300.)));
        list.add(new BenchProfile("monthly-moderate", true, Map.of(Category.GROCERIES, 600., Category.DINING, 350., Category.GAS, 250., Category.TRAVEL, 200., Category.ONLINE, 100., Category.OTHER, 500.)));
        list.add(new BenchProfile("monthly-heavy", true, Map.of(Category.GROCERIES, 1200., Category.DINING, 800., Category.GAS, 400., Category.TRAVEL, 600., Category.ONLINE, 300., Category.OTHER, 1000.)));
        list.add(new BenchProfile("monthly-grocery-cap", true, Map.of(Category.GROCERIES, 800., Category.DINING, 200., Category.OTHER, 250.)));
        list.add(new BenchProfile("monthly-commuter", true, Map.of(Category.GAS, 500., Category.DINING, 300., Category.OTHER, 400.)));
        return list;
    }

    record BenchProfile(String id, boolean monthly, Map<Category, Double> spend) {}
    record ProfileGoal(String profileId, GoalType goal, OptimizationRequest request) {}
}
