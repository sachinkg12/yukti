package io.yukti.engine.optimizer;

import io.yukti.catalog.impl.ImmutableCard;
import io.yukti.catalog.impl.ImmutableCatalog;
import io.yukti.catalog.impl.ImmutableRewardsRule;
import io.yukti.core.api.Card;
import io.yukti.core.api.Catalog;
import io.yukti.core.domain.*;
import io.yukti.engine.reward.RewardModelV1;
import io.yukti.engine.valuation.ValuationModelV1;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AllocationSolverV1_1Test {

    private static final RewardCurrency USD = new RewardCurrency(RewardCurrencyType.USD_CASH, "USD");

    @Test
    void solve_splitAllocation_capAwareSwitching() {
        // Card A: groceries 5% up to $6000, fallback 1%. Card B: groceries 2% uncapped.
        Cap cap6k = new Cap(Money.usd(6000), Period.ANNUAL);
        Card cardA = new ImmutableCard(
            "card-a",
            "Card A",
            "Issuer",
            Money.usd(0),
            List.of(
                new ImmutableRewardsRule("a-cap", Category.GROCERIES, new BigDecimal("0.05"), Optional.of(cap6k), Optional.empty(), USD),
                new ImmutableRewardsRule("a-fb", Category.GROCERIES, new BigDecimal("0.01"), Optional.empty(), Optional.empty(), USD)
            ),
            Money.zeroUsd()
        );
        Card cardB = new ImmutableCard(
            "card-b",
            "Card B",
            "Issuer",
            Money.usd(0),
            List.of(new ImmutableRewardsRule("b1", Category.GROCERIES, new BigDecimal("0.02"), Optional.empty(), Optional.empty(), USD)),
            Money.zeroUsd()
        );
        Catalog catalog = new ImmutableCatalog("1.0", List.of(cardA, cardB), List.of());

        SpendProfile profile = new SpendProfile(Period.ANNUAL, Map.of(Category.GROCERIES, Money.usd(8000)));
        OptimizationRequest request = new OptimizationRequest(
            profile, UserGoal.of(GoalType.CASHBACK), UserConstraints.defaults(), Map.of());

        AllocationSolverV1_1 solver = new AllocationSolverV1_1();
        AllocationResult result = solver.solve(
            request, catalog, List.of("card-a", "card-b"), new RewardModelV1(), new ValuationModelV1(catalog));

        assertEquals("card-a", result.allocationByCategory().get(Category.GROCERIES), "First segment winner");

        List<AllocationSegment> segments = result.allocationPlan().segmentsByCategory().get(Category.GROCERIES);
        assertNotNull(segments);
        assertEquals(2, segments.size());
        assertEquals("card-a", segments.get(0).cardId());
        assertEquals(0, new BigDecimal("6000").compareTo(segments.get(0).spendUsd()));
        assertEquals("card-b", segments.get(1).cardId());
        assertEquals(0, new BigDecimal("2000").compareTo(segments.get(1).spendUsd()));

        boolean hasCapHitWithFallback = result.evidenceBlocks().stream()
            .anyMatch(eb -> "CAP_HIT".equals(eb.getType()) && eb.getContent().contains("fallback=card-b"));
        assertTrue(hasCapHitWithFallback, "Evidence should include cap hit with fallbackCardId=card-b");
    }

    @Test
    void solve_tieBreak_lowerFeeThenLexicographic() {
        Card cardX = new ImmutableCard("card-x", "X", "I", Money.usd(50),
            List.of(new ImmutableRewardsRule("rx", Category.OTHER, new BigDecimal("0.02"), Optional.empty(), Optional.empty(), USD)),
            Money.zeroUsd());
        Card cardY = new ImmutableCard("card-y", "Y", "I", Money.usd(50),
            List.of(new ImmutableRewardsRule("ry", Category.OTHER, new BigDecimal("0.02"), Optional.empty(), Optional.empty(), USD)),
            Money.zeroUsd());
        Catalog catalog = new ImmutableCatalog("1.0", List.of(cardX, cardY), List.of());
        SpendProfile profile = new SpendProfile(Period.ANNUAL, Map.of(Category.OTHER, Money.usd(5000)));
        OptimizationRequest request = new OptimizationRequest(
            profile, UserGoal.of(GoalType.CASHBACK), UserConstraints.defaults(), Map.of());

        AllocationSolverV1_1 solver = new AllocationSolverV1_1();
        AllocationResult result = solver.solve(
            request, catalog, List.of("card-x", "card-y"), new RewardModelV1(), new ValuationModelV1(catalog));

        String winner = result.allocationByCategory().get(Category.OTHER);
        assertTrue("card-x".equals(winner) || "card-y".equals(winner));
        assertEquals("card-x", winner, "Tie-break: lexicographic cardId (card-x before card-y) when fees equal");
    }

    @Test
    void solve_tieBreak_lowerFeePreferred() {
        Card lowFee = new ImmutableCard("low", "Low", "I", Money.usd(0),
            List.of(new ImmutableRewardsRule("r1", Category.OTHER, new BigDecimal("0.02"), Optional.empty(), Optional.empty(), USD)),
            Money.zeroUsd());
        Card highFee = new ImmutableCard("high", "High", "I", Money.usd(95),
            List.of(new ImmutableRewardsRule("r2", Category.OTHER, new BigDecimal("0.02"), Optional.empty(), Optional.empty(), USD)),
            Money.zeroUsd());
        Catalog catalog = new ImmutableCatalog("1.0", List.of(lowFee, highFee), List.of());
        SpendProfile profile = new SpendProfile(Period.ANNUAL, Map.of(Category.OTHER, Money.usd(5000)));
        OptimizationRequest request = new OptimizationRequest(
            profile, UserGoal.of(GoalType.CASHBACK), UserConstraints.defaults(), Map.of());

        AllocationSolverV1_1 solver = new AllocationSolverV1_1();
        AllocationResult result = solver.solve(
            request, catalog, List.of("low", "high"), new RewardModelV1(), new ValuationModelV1(catalog));

        assertEquals("low", result.allocationByCategory().get(Category.OTHER),
            "Same rate: prefer lower fee card");
    }

    @Test
    void solve_deterministicSameInputSameSegments() {
        Cap cap = new Cap(Money.usd(6000), Period.ANNUAL);
        Card cardA = new ImmutableCard("a", "A", "I", Money.usd(0),
            List.of(
                new ImmutableRewardsRule("a1", Category.GROCERIES, new BigDecimal("0.05"), Optional.of(cap), Optional.empty(), USD),
                new ImmutableRewardsRule("a2", Category.GROCERIES, new BigDecimal("0.01"), Optional.empty(), Optional.empty(), USD)
            ),
            Money.zeroUsd());
        Card cardB = new ImmutableCard("b", "B", "I", Money.usd(0),
            List.of(new ImmutableRewardsRule("b1", Category.GROCERIES, new BigDecimal("0.02"), Optional.empty(), Optional.empty(), USD)),
            Money.zeroUsd());
        Catalog catalog = new ImmutableCatalog("1.0", List.of(cardA, cardB), List.of());
        SpendProfile profile = new SpendProfile(Period.ANNUAL, Map.of(Category.GROCERIES, Money.usd(8000)));
        OptimizationRequest request = new OptimizationRequest(
            profile, UserGoal.of(GoalType.CASHBACK), UserConstraints.defaults(), Map.of());
        AllocationSolverV1_1 solver = new AllocationSolverV1_1();
        RewardModelV1 rewardModel = new RewardModelV1();
        ValuationModelV1 valuationModel = new ValuationModelV1(catalog);

        AllocationResult r1 = solver.solve(request, catalog, List.of("a", "b"), rewardModel, valuationModel);
        AllocationResult r2 = solver.solve(request, catalog, List.of("a", "b"), rewardModel, valuationModel);

        assertEquals(r1.allocationByCategory(), r2.allocationByCategory());
        assertEquals(r1.allocationPlan().segmentsByCategory(), r2.allocationPlan().segmentsByCategory());
        assertEquals(r1.netValueUsd().getAmount(), r2.netValueUsd().getAmount());
    }
}
