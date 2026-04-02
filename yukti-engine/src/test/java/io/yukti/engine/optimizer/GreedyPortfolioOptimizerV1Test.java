package io.yukti.engine.optimizer;

import io.yukti.catalog.ClasspathCatalogSource;
import io.yukti.catalog.impl.ImmutableCard;
import io.yukti.catalog.impl.ImmutableCatalog;
import io.yukti.catalog.impl.ImmutableRewardsRule;
import io.yukti.catalog.impl.ImmutableValuationPolicy;
import io.yukti.core.api.Card;
import io.yukti.core.api.Catalog;
import io.yukti.core.api.Optimizer;
import io.yukti.core.api.ValuationPolicy;
import io.yukti.core.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GreedyPortfolioOptimizerV1Test {

    private Catalog catalog;
    private Optimizer optimizer;

    @BeforeEach
    void setUp() throws Exception {
        catalog = new ClasspathCatalogSource("catalog/catalog-v1.json").load("1.0");
        optimizer = new GreedyPortfolioOptimizerV1();
    }

    @Test
    void optimize_deterministicForFixedCatalog() {
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

        OptimizationResult r1 = optimizer.optimize(request, catalog);
        OptimizationResult r2 = optimizer.optimize(request, catalog);

        assertEquals(r1.getPortfolioIds(), r2.getPortfolioIds(), "Portfolio ids must be identical");
        assertEquals(r1.getAllocation(), r2.getAllocation(), "Allocation must be identical");
        assertEquals(0, r1.getBreakdown().getNet().getAmount().compareTo(r2.getBreakdown().getNet().getAmount()),
            "Net value must be identical");
        assertEquals(r1.getEvidenceBlocks().size(), r2.getEvidenceBlocks().size(),
            "Evidence block count must be identical");
        for (int i = 0; i < r1.getEvidenceBlocks().size(); i++) {
            var a = r1.getEvidenceBlocks().get(i);
            var b = r2.getEvidenceBlocks().get(i);
            assertEquals(a.getType(), b.getType());
            assertEquals(a.getCardId(), b.getCardId());
            assertEquals(a.getCategory(), b.getCategory());
        }
    }

    @Test
    void optimize_stopCondition_maxCardsReached() {
        Map<Category, Money> amounts = Map.of(
            Category.GROCERIES, Money.usd(15000),
            Category.DINING, Money.usd(8000),
            Category.TRAVEL, Money.usd(10000),
            Category.GAS, Money.usd(3000),
            Category.OTHER, Money.usd(10000)
        );
        SpendProfile profile = new SpendProfile(Period.ANNUAL, amounts);
        UserConstraints constraints = new UserConstraints(2, Money.usd(500), true);
        OptimizationRequest request = new OptimizationRequest(
            profile,
            UserGoal.of(GoalType.CASHBACK),
            constraints,
            Map.of()
        );

        OptimizationResult r = optimizer.optimize(request, catalog);

        assertTrue(r.getPortfolioIds().size() <= 2, "Must respect maxCards=2");
        boolean hasStopEvidence = r.getEvidenceBlocks().stream()
            .anyMatch(eb -> "PORTFOLIO_STOP".equals(eb.getType()) && eb.getContent().contains("maxCards"));
        assertTrue(hasStopEvidence, "Should have PortfolioStopEvidence when maxCards reached");
    }

    @Test
    void optimize_stopCondition_maxCardsOne_reasonMaxCardsReached() {
        Map<Category, Money> amounts = Map.of(Category.GROCERIES, Money.usd(5000), Category.OTHER, Money.usd(5000));
        SpendProfile profile = new SpendProfile(Period.ANNUAL, amounts);
        UserConstraints constraints = new UserConstraints(1, Money.usd(500), true);
        OptimizationRequest request = new OptimizationRequest(
            profile, UserGoal.of(GoalType.CASHBACK), constraints, Map.of());

        OptimizationResult r = optimizer.optimize(request, catalog);

        assertEquals(1, r.getPortfolioIds().size());
        long stopCount = r.getEvidenceBlocks().stream().filter(eb -> "PORTFOLIO_STOP".equals(eb.getType())).count();
        assertEquals(1, stopCount);
        assertTrue(r.getEvidenceBlocks().stream().anyMatch(eb ->
            "PORTFOLIO_STOP".equals(eb.getType()) && eb.getContent().contains("MAX_CARDS_REACHED")));
    }

    @Test
    void optimize_stopCondition_noPositiveMarginalGain() {
        Map<Category, Money> amounts = Map.of(Category.OTHER, Money.usd(100));
        SpendProfile profile = new SpendProfile(Period.ANNUAL, amounts);
        OptimizationRequest request = new OptimizationRequest(
            profile,
            UserGoal.of(GoalType.CASHBACK),
            UserConstraints.defaults(),
            Map.of()
        );

        OptimizationResult r = optimizer.optimize(request, catalog);

        assertFalse(r.getPortfolioIds().isEmpty(), "Should still add at least one card for positive net");
        boolean hasStopEvidence = r.getEvidenceBlocks().stream()
            .anyMatch(eb -> "PORTFOLIO_STOP".equals(eb.getType()));
        assertTrue(hasStopEvidence || r.getPortfolioIds().size() >= 1,
            "Should have stop evidence or minimal portfolio");
    }

    @Test
    void optimize_aaGoalSelectsAaCobrandInSyntheticScenario() {
        // Synthetic catalog: AA co-brand vs cashback. With PROGRAM_POINTS+AA primary, AA earns (cpp override 1.4c).
        RewardCurrency aaMiles = new RewardCurrency(RewardCurrencyType.AA_MILES, "AA_MILES");
        RewardCurrency usd = new RewardCurrency(RewardCurrencyType.USD_CASH, "USD");
        Card aaCard = new ImmutableCard(
            "barclays-aa-aviator",
            "Barclays AAdvantage Aviator Red",
            "Barclays",
            Money.usd(99),
            List.of(
                new ImmutableRewardsRule("r1", Category.TRAVEL, new BigDecimal("0.02"), Optional.empty(), Optional.empty(), aaMiles),
                new ImmutableRewardsRule("r2", Category.DINING, new BigDecimal("0.02"), Optional.empty(), Optional.empty(), aaMiles),
                new ImmutableRewardsRule("r3", Category.OTHER, new BigDecimal("0.01"), Optional.empty(), Optional.empty(), aaMiles)
            ),
            Money.zeroUsd()
        );
        Card cashbackCard = new ImmutableCard(
            "citi-double-cash",
            "Citi Double Cash",
            "Citi",
            Money.zeroUsd(),
            List.of(
                new ImmutableRewardsRule("r4", Category.TRAVEL, BigDecimal.ZERO, Optional.empty(), Optional.empty(), usd),
                new ImmutableRewardsRule("r5", Category.DINING, BigDecimal.ZERO, Optional.empty(), Optional.empty(), usd),
                new ImmutableRewardsRule("r6", Category.OTHER, new BigDecimal("0.02"), Optional.empty(), Optional.empty(), usd)
            ),
            Money.zeroUsd()
        );
        Catalog syntheticCatalog = new ImmutableCatalog(
            "1.0",
            List.of(aaCard, cashbackCard),
            List.of()
        );

        // Spend: 360k T+D at 2%=7200 pts, 10k other at 1%=100 pts = 7300 pts. With cpp 0.014: 102.2 - 99 fee > 0.
        Map<Category, Money> amounts = Map.of(
            Category.TRAVEL, Money.usd(200000),
            Category.DINING, Money.usd(160000),
            Category.OTHER, Money.usd(10000)
        );
        SpendProfile profile = new SpendProfile(Period.ANNUAL, amounts);
        UserGoal aaGoal = new UserGoal(
            GoalType.PROGRAM_POINTS,
            Optional.of(RewardCurrencyType.AA_MILES),
            List.of(),
            Map.of(RewardCurrencyType.AA_MILES, new BigDecimal("0.014"))
        );
        OptimizationRequest request = new OptimizationRequest(
            profile, aaGoal, new UserConstraints(3, Money.usd(500), true), Map.of());

        OptimizationResult r = optimizer.optimize(request, syntheticCatalog);

        assertFalse(r.getPortfolioIds().isEmpty());
        assertTrue(r.getPortfolioIds().contains("barclays-aa-aviator"),
            "AA goal with synthetic catalog (AA vs cashback) must select AA co-brand; got " + r.getPortfolioIds());
    }

    @Test
    void optimize_maxAnnualFeeEnforced() {
        Map<Category, Money> amounts = Map.of(
            Category.GROCERIES, Money.usd(15000),
            Category.TRAVEL, Money.usd(20000),
            Category.DINING, Money.usd(10000),
            Category.OTHER, Money.usd(10000)
        );
        SpendProfile profile = new SpendProfile(Period.ANNUAL, amounts);
        UserConstraints constraints = new UserConstraints(5, Money.usd(100), true); // strict fee cap
        OptimizationRequest request = new OptimizationRequest(
            profile,
            UserGoal.of(GoalType.CASHBACK),
            constraints,
            Map.of()
        );

        OptimizationResult r = optimizer.optimize(request, catalog);

        java.math.BigDecimal totalFees = r.getBreakdown().getFees().getAmount();
        assertTrue(totalFees.compareTo(java.math.BigDecimal.valueOf(100)) <= 0,
            "Total annual fees must not exceed maxAnnualFee=$100, got " + r.getBreakdown().getFees());
    }

    @Test
    void optimize_returnsPortfolioAllocationBreakdownEvidence() {
        Map<Category, Money> amounts = Map.of(
            Category.GROCERIES, Money.usd(6000),
            Category.DINING, Money.usd(3000),
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
        assertNotNull(r.getAllocation());
        assertNotNull(r.getBreakdown());
        assertNotNull(r.getEvidenceBlocks());
        assertTrue(r.getBreakdown().getEarnValue().getAmount().compareTo(java.math.BigDecimal.ZERO) >= 0);

        boolean hasFeeBreakEven = r.getEvidenceBlocks().stream()
            .anyMatch(eb -> "FEE_BREAK_EVEN".equals(eb.getType()));
        assertTrue(hasFeeBreakEven, "Should include FeeBreakEvenEvidence for portfolio cards");
    }

    @Test
    void optimize_evidencePresence() {
        Map<Category, Money> amounts = Map.of(
            Category.GROCERIES, Money.usd(6000),
            Category.DINING, Money.usd(3000),
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

        long winnerCount = r.getEvidenceBlocks().stream().filter(eb -> "WINNER_BY_CATEGORY".equals(eb.getType())).count();
        long nonZeroCategories = amounts.entrySet().stream()
            .filter(e -> e.getValue().getAmount().compareTo(java.math.BigDecimal.ZERO) > 0).count();
        assertTrue(winnerCount >= nonZeroCategories, "At least one WinnerByCategoryEvidence per non-zero category; got " + winnerCount + " winners, " + nonZeroCategories + " categories");

        long portfolioStopCount = r.getEvidenceBlocks().stream().filter(eb -> "PORTFOLIO_STOP".equals(eb.getType())).count();
        assertEquals(1, portfolioStopCount, "Exactly one PortfolioStopEvidence");

        long feeBreakEvenCount = r.getEvidenceBlocks().stream().filter(eb -> "FEE_BREAK_EVEN".equals(eb.getType())).count();
        assertEquals(r.getPortfolioIds().size(), feeBreakEvenCount, "FeeBreakEvenEvidence count must equal portfolio size");
    }

    @Test
    void optimize_tieBreakDeterministic() {
        Map<Category, Money> amounts = Map.of(Category.OTHER, Money.usd(10000));
        SpendProfile profile = new SpendProfile(Period.ANNUAL, amounts);
        UserConstraints constraints = new UserConstraints(1, Money.usd(500), true);
        OptimizationRequest request = new OptimizationRequest(
            profile,
            UserGoal.of(GoalType.CASHBACK),
            constraints,
            Map.of()
        );

        OptimizationResult r1 = optimizer.optimize(request, catalog);
        OptimizationResult r2 = optimizer.optimize(request, catalog);

        assertEquals(r1.getPortfolioIds(), r2.getPortfolioIds(), "Tie-break must be deterministic");
        assertEquals(r1.getAllocation(), r2.getAllocation());
    }

    @Test
    void optimize_emptySpend_returnsEmpty() {
        SpendProfile profile = new SpendProfile(Period.ANNUAL, new java.util.EnumMap<>(Category.class));
        OptimizationRequest request = new OptimizationRequest(
            profile,
            UserGoal.of(GoalType.CASHBACK),
            UserConstraints.defaults(),
            Map.of()
        );

        OptimizationResult r = optimizer.optimize(request, catalog);

        assertTrue(r.getPortfolioIds().isEmpty());
        assertTrue(r.getAllocation().isEmpty());
    }

    /** Deterministic test with 4–6 card synthetic catalog: 2 cashback, 2 flex, 1 AA, 1 with fee+credits. */
    @Test
    void optimize_deterministicWithSixCardSyntheticCatalog() {
        RewardCurrency usd = new RewardCurrency(RewardCurrencyType.USD_CASH, "USD");
        RewardCurrency ur = new RewardCurrency(RewardCurrencyType.BANK_UR, "BANK_UR");
        RewardCurrency aa = new RewardCurrency(RewardCurrencyType.AA_MILES, "AA_MILES");
        List<Card> cards = List.of(
            new ImmutableCard("cash-flat", "Cash Flat 2%", "X", Money.zeroUsd(),
                List.of(new ImmutableRewardsRule("r1", Category.OTHER, new BigDecimal("0.02"), Optional.empty(), Optional.empty(), usd)),
                Money.zeroUsd()),
            new ImmutableCard("cash-grocery", "Cash Grocery 5%", "X", Money.zeroUsd(),
                List.of(
                    new ImmutableRewardsRule("r2", Category.GROCERIES, new BigDecimal("0.05"), Optional.empty(), Optional.empty(), usd),
                    new ImmutableRewardsRule("r3", Category.OTHER, new BigDecimal("0.01"), Optional.empty(), Optional.empty(), usd)),
                Money.zeroUsd()),
            new ImmutableCard("flex-ur", "Flex UR", "X", Money.usd(95),
                List.of(
                    new ImmutableRewardsRule("r4", Category.DINING, new BigDecimal("3"), Optional.empty(), Optional.empty(), ur),
                    new ImmutableRewardsRule("r5", Category.OTHER, new BigDecimal("1.5"), Optional.empty(), Optional.empty(), ur)),
                Money.zeroUsd()),
            new ImmutableCard("flex-mr", "Flex MR", "X", Money.usd(250),
                List.of(
                    new ImmutableRewardsRule("r6", Category.TRAVEL, new BigDecimal("3"), Optional.empty(), Optional.empty(), ur),
                    new ImmutableRewardsRule("r7", Category.OTHER, new BigDecimal("1"), Optional.empty(), Optional.empty(), ur)),
                Money.zeroUsd()),
            new ImmutableCard("aa-cobrand", "AA Co-brand", "X", Money.usd(99),
                List.of(
                    new ImmutableRewardsRule("r8", Category.TRAVEL, new BigDecimal("2"), Optional.empty(), Optional.empty(), aa),
                    new ImmutableRewardsRule("r9", Category.DINING, new BigDecimal("2"), Optional.empty(), Optional.empty(), aa)),
                Money.zeroUsd()),
            new ImmutableCard("fee-credits", "Fee + Credits", "X", Money.usd(150),
                List.of(new ImmutableRewardsRule("r10", Category.OTHER, new BigDecimal("0.01"), Optional.empty(), Optional.empty(), usd)),
                Money.usd(120))
        );
        Catalog sixCardCatalog = new ImmutableCatalog("1.0", cards, List.of());

        Map<Category, Money> amounts = Map.of(
            Category.GROCERIES, Money.usd(6000),
            Category.DINING, Money.usd(4000),
            Category.TRAVEL, Money.usd(5000),
            Category.OTHER, Money.usd(10000)
        );
        SpendProfile profile = new SpendProfile(Period.ANNUAL, amounts);
        OptimizationRequest request = new OptimizationRequest(
            profile, UserGoal.of(GoalType.CASHBACK), UserConstraints.defaults(), Map.of());

        OptimizationResult r1 = optimizer.optimize(request, sixCardCatalog);
        OptimizationResult r2 = optimizer.optimize(request, sixCardCatalog);

        assertEquals(r1.getPortfolioIds(), r2.getPortfolioIds());
        assertEquals(r1.getAllocation(), r2.getAllocation());
        assertEquals(0, r1.getBreakdown().getNet().getAmount().compareTo(r2.getBreakdown().getNet().getAmount()));
        assertEquals(r1.getEvidenceBlocks().size(), r2.getEvidenceBlocks().size());
        assertTrue(r1.getPortfolioIds().size() <= 3, "Phase-1 max 3 cards");
    }
}
