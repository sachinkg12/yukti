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

/**
 * Hand-verified profiles with known optimal solutions.
 * These serve as regression tests and validate the MILP formulation.
 *
 * <p>Catalog v1 key cards for hand computation:
 * <ul>
 *   <li>citi-double-cash: 2% OTHER (USD_CASH), $0 fee</li>
 *   <li>wf-active-cash: 2% OTHER (USD_CASH), $0 fee</li>
 *   <li>amex-bcp: 6% GROCERIES cap $6000 fallback 1%, 3% DINING/GAS/TRAVEL, $95 fee (USD_CASH)</li>
 *   <li>amex-bce: 3% GROCERIES cap $6000 fallback 1%, $0 fee (USD_CASH)</li>
 * </ul>
 */
class MilpHandVerifiedTest {

    private Catalog catalog;
    private Optimizer optimizer;

    @BeforeEach
    void setUp() throws Exception {
        catalog = new ClasspathCatalogSource("catalog/catalog-v1.json").load("1.0");
        optimizer = new MilpOptimizer();
    }

    @Test
    void trivialOtherOnly_cashback() {
        // Spend: OTHER $5000, CASHBACK goal
        // Best: wf-active-cash or citi-double-cash (both 2% on OTHER, $0 fee)
        // Expected earn: $5000 × 0.02 = $100, fee = $0, net = $100
        OptimizationResult r = optimize(
            Map.of(Category.OTHER, 5000.0),
            GoalType.CASHBACK);

        assertFalse(r.getPortfolioIds().isEmpty());
        BigDecimal net = r.getBreakdown().getNet().getAmount();
        assertEquals(0, net.compareTo(BigDecimal.valueOf(100)),
            "OTHER $5000 at 2% should give net $100, got " + net);
    }

    @Test
    void minimalProfile_onlyOther() {
        // From BenchmarkHarness: "minimal" profile — OTHER $5000 only
        // Best cashback: 2% flat card, $0 fee → net $100
        OptimizationResult r = optimize(
            Map.of(Category.OTHER, 5000.0),
            GoalType.CASHBACK);

        assertEquals(0, r.getBreakdown().getNet().getAmount().compareTo(BigDecimal.valueOf(100)),
            "Minimal OTHER $5000 cashback: expected $100 net");
    }

    @Test
    void groceryCapHit_cashback() {
        // Spend: GROCERIES $12000, OTHER $1000
        // amex-bcp: 6% on first $6000 = $360, 1% on remaining $6000 = $60, 1% OTHER $1000 = $10
        //   → earn = $430, fee = $95, net = $335
        // With expanded catalog (70 cards), MILP may find equal or better combos
        OptimizationResult r = optimize(
            Map.of(Category.GROCERIES, 12000.0, Category.OTHER, 1000.0),
            GoalType.CASHBACK);

        BigDecimal net = r.getBreakdown().getNet().getAmount();
        // MILP should do at least as well as single amex-bcp ($335)
        assertTrue(net.compareTo(BigDecimal.valueOf(335)) >= 0,
            "MILP should beat or match single-card amex-bcp ($335), got " + net);
    }

    @Test
    void feeSensitive_zeroBudget_cashback() {
        // maxAnnualFee = $0 → only no-fee cards allowed
        // Spend: GROCERIES $6000, OTHER $5000
        // Best no-fee GROCERIES: amex-bce (3% cap $6000) → $180
        // Best no-fee OTHER: citi-double-cash/wf-active-cash (2%) → $100
        // Expected net ≈ $280 (no fees)
        OptimizationRequest request = new OptimizationRequest(
            new SpendProfile(Period.ANNUAL,
                Map.of(Category.GROCERIES, Money.usd(6000),
                    Category.OTHER, Money.usd(5000))),
            UserGoal.of(GoalType.CASHBACK),
            new UserConstraints(3, Money.usd(0), true),
            Map.of());

        OptimizationResult r = optimizer.optimize(request, catalog);

        assertEquals(0, r.getBreakdown().getFees().getAmount().compareTo(BigDecimal.ZERO),
            "Zero fee budget means no fees");
        assertTrue(r.getBreakdown().getNet().getAmount().compareTo(BigDecimal.valueOf(200)) > 0,
            "No-fee cards should still earn significant cashback, got " + r.getBreakdown().getNet());
    }

    @Test
    void milpNetMatchesPythonForLight_cashback() {
        // "light" profile from BenchmarkHarness:
        // GROCERIES=$3000, DINING=$1500, GAS=$1200, OTHER=$2500
        // Python MILP (28-card catalog) reported net=$236.00 for CASHBACK
        // With expanded catalog (70 cards), MILP should find equal or better solution
        OptimizationResult r = optimize(
            Map.of(Category.GROCERIES, 3000.0, Category.DINING, 1500.0,
                Category.GAS, 1200.0, Category.OTHER, 2500.0),
            GoalType.CASHBACK);

        BigDecimal net = r.getBreakdown().getNet().getAmount();
        BigDecimal pythonNet = BigDecimal.valueOf(236.00);
        assertTrue(net.compareTo(pythonNet) >= 0,
            "Java MILP net should be at least Python's $236: got " + net);
    }

    // --- Helpers ---

    private OptimizationResult optimize(Map<Category, Double> spend, GoalType goal) {
        Map<Category, Money> amounts = new EnumMap<>(Category.class);
        spend.forEach((cat, amt) -> amounts.put(cat, Money.usd(amt)));
        return optimizer.optimize(
            new OptimizationRequest(
                new SpendProfile(Period.ANNUAL, amounts),
                UserGoal.of(goal),
                UserConstraints.defaults(),
                Map.of()),
            catalog);
    }
}
