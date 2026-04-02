package io.yukti.engine.optimizer;

import io.yukti.catalog.ClasspathCatalogSource;
import io.yukti.core.api.Catalog;
import io.yukti.core.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LP relaxation optimizer. Requires OR-Tools native libraries.
 * Tests are skipped if OR-Tools is not available.
 */
class LpRelaxationOptimizerTest {

    private Catalog catalog;
    private LpRelaxationOptimizer optimizer;

    @BeforeEach
    void setUp() throws Exception {
        catalog = new ClasspathCatalogSource("catalog/catalog-v1.json").load("1.0");
        optimizer = new LpRelaxationOptimizer();
    }

    static boolean orToolsAvailable() {
        try {
            com.google.ortools.Loader.loadNativeLibraries();
            return true;
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            return false;
        }
    }

    @Test
    @EnabledIf("orToolsAvailable")
    void optimize_returnsValidPortfolio() {
        Map<Category, Money> amounts = Map.of(
            Category.GROCERIES, Money.usd(6000),
            Category.DINING, Money.usd(3000),
            Category.GAS, Money.usd(2400),
            Category.OTHER, Money.usd(5000)
        );
        SpendProfile profile = new SpendProfile(Period.ANNUAL, amounts);
        OptimizationRequest request = new OptimizationRequest(
            profile,
            UserGoal.of(GoalType.CASHBACK),
            UserConstraints.defaults(),
            Map.of()
        );

        OptimizationResult r = optimizer.optimize(request, catalog);

        assertNotNull(r.getPortfolioIds());
        assertFalse(r.getPortfolioIds().isEmpty());
        assertTrue(r.getPortfolioIds().size() <= 3);
        assertNotNull(r.getAllocation());
        assertNotNull(r.getBreakdown());
        assertTrue(r.getBreakdown().getEarnValue().getAmount().compareTo(BigDecimal.ZERO) >= 0);
        assertFalse(r.getEvidenceBlocks().isEmpty(), "Evidence blocks must be generated");
        assertTrue(r.getNarrative().contains("LP relaxation"));
    }

    @Test
    @EnabledIf("orToolsAvailable")
    void optimize_deterministic() {
        Map<Category, Money> amounts = Map.of(
            Category.GROCERIES, Money.usd(500),
            Category.DINING, Money.usd(250),
            Category.OTHER, Money.usd(400)
        );
        SpendProfile profile = new SpendProfile(Period.MONTHLY, amounts);
        OptimizationRequest request = new OptimizationRequest(
            profile, UserGoal.of(GoalType.CASHBACK), UserConstraints.defaults(), Map.of()
        );

        OptimizationResult r1 = optimizer.optimize(request, catalog);
        OptimizationResult r2 = optimizer.optimize(request, catalog);

        assertEquals(r1.getPortfolioIds(), r2.getPortfolioIds());
        assertEquals(r1.getBreakdown().getNet().getAmount(), r2.getBreakdown().getNet().getAmount());
    }

    @Test
    @EnabledIf("orToolsAvailable")
    void optimize_emptySpend_returnsEmpty() {
        SpendProfile profile = new SpendProfile(Period.ANNUAL, new java.util.EnumMap<>(Category.class));
        OptimizationRequest request = new OptimizationRequest(
            profile, UserGoal.of(GoalType.CASHBACK), UserConstraints.defaults(), Map.of()
        );

        OptimizationResult r = optimizer.optimize(request, catalog);

        assertTrue(r.getPortfolioIds().isEmpty());
    }

    @Test
    @EnabledIf("orToolsAvailable")
    void optimize_lpBoundIsUpperBound() {
        Map<Category, Money> amounts = Map.of(
            Category.GROCERIES, Money.usd(6000),
            Category.DINING, Money.usd(3000),
            Category.GAS, Money.usd(2400),
            Category.OTHER, Money.usd(5000)
        );
        SpendProfile profile = new SpendProfile(Period.ANNUAL, amounts);
        OptimizationRequest request = new OptimizationRequest(
            profile, UserGoal.of(GoalType.CASHBACK), UserConstraints.defaults(), Map.of()
        );

        OptimizationResult r = optimizer.optimize(request, catalog);
        double lpBound = optimizer.getLastLpBound();

        assertFalse(Double.isNaN(lpBound), "LP bound must be set after solve");
        assertTrue(lpBound >= r.getBreakdown().getNet().getAmount().doubleValue() - 0.01,
            "LP bound (" + lpBound + ") must be >= rounded net value ("
                + r.getBreakdown().getNet() + ")");
    }

    @Test
    @EnabledIf("orToolsAvailable")
    void optimize_feeBudgetEnforced() {
        Map<Category, Money> amounts = Map.of(
            Category.GROCERIES, Money.usd(20000),
            Category.DINING, Money.usd(15000),
            Category.TRAVEL, Money.usd(10000),
            Category.OTHER, Money.usd(10000)
        );
        SpendProfile profile = new SpendProfile(Period.ANNUAL, amounts);
        UserConstraints constraints = new UserConstraints(5, Money.usd(100), true);
        OptimizationRequest request = new OptimizationRequest(
            profile, UserGoal.of(GoalType.CASHBACK), constraints, Map.of()
        );

        OptimizationResult r = optimizer.optimize(request, catalog);

        assertTrue(r.getBreakdown().getFees().getAmount().compareTo(BigDecimal.valueOf(100)) <= 0,
            "LP relaxation must enforce fee budget after rounding: fees=" + r.getBreakdown().getFees());
    }
}
