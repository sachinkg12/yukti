package io.yukti.engine.optimizer;

import io.yukti.catalog.ClasspathCatalogSource;
import io.yukti.core.api.Catalog;
import io.yukti.core.api.Optimizer;
import io.yukti.core.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SingleBestPerCategoryBaselineTest {

    private Catalog catalog;
    private Optimizer optimizer;

    @BeforeEach
    void setUp() throws Exception {
        catalog = new ClasspathCatalogSource("catalog/catalog-v1.json").load("1.0");
        optimizer = new SingleBestPerCategoryBaseline();
    }

    @Test
    void id_returnsSingleBestPerCategory() {
        assertEquals("single-best-per-category-v1", optimizer.id());
    }

    @Test
    void optimize_returnsPortfolioAndAllocation() {
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
        assertTrue(r.getBreakdown().getEarnValue().getAmount().compareTo(java.math.BigDecimal.ZERO) >= 0);
    }

    @Test
    void optimize_deterministic() {
        Map<Category, Money> amounts = Map.of(
                Category.GROCERIES, Money.usd(500),
                Category.DINING, Money.usd(250),
                Category.OTHER, Money.usd(400)
        );
        SpendProfile profile = new SpendProfile(Period.MONTHLY, amounts);
        OptimizationRequest request = new OptimizationRequest(
                profile, UserGoal.of(GoalType.CASHBACK), UserConstraints.defaults(), Map.of());

        OptimizationResult r1 = optimizer.optimize(request, catalog);
        OptimizationResult r2 = optimizer.optimize(request, catalog);

        assertEquals(r1.getPortfolioIds(), r2.getPortfolioIds());
        assertEquals(r1.getBreakdown().getNet().getAmount(), r2.getBreakdown().getNet().getAmount());
    }

    @Test
    void optimize_emptySpend_returnsEmpty() {
        SpendProfile profile = new SpendProfile(Period.ANNUAL, new java.util.EnumMap<>(Category.class));
        OptimizationRequest request = new OptimizationRequest(
                profile, UserGoal.of(GoalType.CASHBACK), UserConstraints.defaults(), Map.of());

        OptimizationResult r = optimizer.optimize(request, catalog);

        assertTrue(r.getPortfolioIds().isEmpty());
        assertTrue(r.getAllocation().isEmpty());
    }
}
