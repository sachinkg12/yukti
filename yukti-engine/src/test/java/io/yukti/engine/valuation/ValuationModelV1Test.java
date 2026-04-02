package io.yukti.engine.valuation;

import io.yukti.catalog.DefaultValuationPolicies;
import io.yukti.catalog.RepoCatalogSource;
import io.yukti.catalog.util.CurrencyMapping;
import io.yukti.core.api.Catalog;
import io.yukti.core.api.Optimizer;
import io.yukti.core.domain.*;
import io.yukti.core.explainability.evidence.AssumptionEvidence;
import io.yukti.core.valuation.ValuationContext;
import io.yukti.engine.optimizer.CapAwareGreedyOptimizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ValuationModelV1Test {

    private Catalog catalog;
    private Optimizer optimizer;

    @BeforeEach
    void setUp() throws Exception {
        catalog = new RepoCatalogSource("catalog/v1", DefaultValuationPolicies.defaults()).load("v1");
        optimizer = new CapAwareGreedyOptimizer();
    }

    @Test
    void goalSelection_measurablyChangesOutcomes() {
        Map<Category, Money> amounts = Map.of(
            Category.GROCERIES, Money.usd(6000),
            Category.DINING, Money.usd(6000),
            Category.OTHER, Money.usd(60000)
        );
        SpendProfile profile = new SpendProfile(Period.ANNUAL, amounts);

        OptimizationResult cashback = optimizer.optimize(
            new OptimizationRequest(profile, UserGoal.of(GoalType.CASHBACK), UserConstraints.defaults(), Map.of()),
            catalog);

        OptimizationResult flex = optimizer.optimize(
            new OptimizationRequest(profile, UserGoal.of(GoalType.FLEX_POINTS), UserConstraints.defaults(), Map.of()),
            catalog);

        UserGoal programGoal = new UserGoal(
            GoalType.PROGRAM_POINTS,
            Optional.of(RewardCurrencyType.AA_MILES),
            List.of(),
            Map.of());
        OptimizationResult program = optimizer.optimize(
            new OptimizationRequest(profile, programGoal, UserConstraints.defaults(), Map.of()),
            catalog);

        boolean allSame = cashback.getPortfolioIds().equals(flex.getPortfolioIds())
            && flex.getPortfolioIds().equals(program.getPortfolioIds());
        assertFalse(allSame, "Goals should change value outcomes; at least one portfolio must differ. " +
            "CASHBACK=" + cashback.getPortfolioIds() + " FLEX=" + flex.getPortfolioIds() + " PROGRAM=" + program.getPortfolioIds());
    }

    @Test
    void programGoal_producesValidResult() {
        Map<Category, Money> amounts = Map.of(
            Category.TRAVEL, Money.usd(30000),
            Category.DINING, Money.usd(24000),
            Category.OTHER, Money.usd(10000)
        );
        SpendProfile profile = new SpendProfile(Period.ANNUAL, amounts);

        UserGoal programGoal = new UserGoal(
            GoalType.PROGRAM_POINTS,
            Optional.of(RewardCurrencyType.AA_MILES),
            List.of(),
            Map.of());
        OptimizationResult result = optimizer.optimize(
            new OptimizationRequest(profile, programGoal, UserConstraints.defaults(), Map.of()),
            catalog);

        assertFalse(result.getPortfolioIds().isEmpty());
        assertNotNull(result.getBreakdown());
    }

    @Test
    void cppOverride_changesRanking() {
        Map<Category, Money> amounts = Map.of(Category.OTHER, Money.usd(60000));
        SpendProfile profile = new SpendProfile(Period.ANNUAL, amounts);

        OptimizationResult base = optimizer.optimize(
            new OptimizationRequest(profile, UserGoal.of(GoalType.FLEX_POINTS), UserConstraints.defaults(), Map.of()),
            catalog);

        UserGoal overridden = new UserGoal(
            GoalType.FLEX_POINTS,
            java.util.Optional.empty(),
            java.util.List.of(),
            Map.of(RewardCurrencyType.BANK_UR, java.math.BigDecimal.valueOf(0.02))
        );
        OptimizationResult withOverride = optimizer.optimize(
            new OptimizationRequest(profile, overridden, UserConstraints.defaults(), Map.of()),
            catalog);

        assertNotNull(base.getBreakdown());
        assertNotNull(withOverride.getBreakdown());
        assertTrue(withOverride.getBreakdown().getEarnValue().getAmount()
                .compareTo(base.getBreakdown().getEarnValue().getAmount()) >= 0,
            "Higher cpp override should increase or maintain earn value");
    }

    @Test
    void spec_cashbackGoal_valuesOnlyCash() {
        ValuationModelV1 model = new ValuationModelV1(catalog);
        RewardsBreakdown rewardsCashback = new RewardsBreakdown(
            Map.of(CurrencyMapping.fromJsonId("USD"), Points.of(new BigDecimal("1000.00"))),
            Map.of(), Money.zeroUsd(), List.of(), List.of());
        RewardsBreakdown rewardsAA = new RewardsBreakdown(
            Map.of(CurrencyMapping.fromJsonId("AA_MILES"), Points.of(new BigDecimal("100000"))),
            Map.of(), Money.zeroUsd(), List.of(), List.of());
        RewardsBreakdown rewardsUR = new RewardsBreakdown(
            Map.of(CurrencyMapping.fromJsonId("CHASE_UR"), Points.of(new BigDecimal("80000"))),
            Map.of(), Money.zeroUsd(), List.of(), List.of());

        ValuationResult cashbackResult = model.value(rewardsCashback, UserGoal.of(GoalType.CASHBACK), (ValuationContext) null);
        ValuationResult aaResult = model.value(rewardsAA, UserGoal.of(GoalType.CASHBACK), (ValuationContext) null);
        ValuationResult urResult = model.value(rewardsUR, UserGoal.of(GoalType.CASHBACK), (ValuationContext) null);

        assertTrue(cashbackResult.earnedValueUsd().compareTo(BigDecimal.ZERO) > 0, "Cashback goal values cash");
        assertEquals(0, new BigDecimal("1000.00").compareTo(cashbackResult.earnedValueUsd()));
        assertEquals(0, aaResult.earnedValueUsd().compareTo(BigDecimal.ZERO), "Cashback goal: non-cash valued 0");
        assertEquals(0, urResult.earnedValueUsd().compareTo(BigDecimal.ZERO), "Cashback goal: non-cash valued 0");
    }

    @Test
    void spec_flexGoal_prefersBankPoints() {
        ValuationModelV1 model = new ValuationModelV1(catalog);
        RewardsBreakdown rewardsUR = new RewardsBreakdown(
            Map.of(CurrencyMapping.fromJsonId("CHASE_UR"), Points.of(new BigDecimal("80000"))),
            Map.of(), Money.zeroUsd(), List.of(), List.of());
        RewardsBreakdown rewardsAA = new RewardsBreakdown(
            Map.of(CurrencyMapping.fromJsonId("AA_MILES"), Points.of(new BigDecimal("100000"))),
            Map.of(), Money.zeroUsd(), List.of(), List.of());
        UserGoal flexWithUr = new UserGoal(GoalType.FLEX_POINTS, Optional.empty(), List.of(RewardCurrencyType.BANK_UR), Map.of());

        ValuationResult urResult = model.value(rewardsUR, flexWithUr, (ValuationContext) null);
        ValuationResult aaResult = model.value(rewardsAA, flexWithUr, (ValuationContext) null);

        assertEquals(new BigDecimal("1440.00"), urResult.earnedValueUsd(), "80k UR * 0.018 * 1.0 = 1440");
        assertEquals(new BigDecimal("1040.00"), aaResult.earnedValueUsd(), "100k AA * 0.013 * 0.8 = 1040");
        assertTrue(urResult.earnedValueUsd().compareTo(aaResult.earnedValueUsd()) > 0, "UR > AA for FLEX with UR preferred");
    }

    @Test
    void spec_programGoal_prefersAA() {
        ValuationModelV1 model = new ValuationModelV1(catalog);
        RewardsBreakdown rewardsAA = new RewardsBreakdown(
            Map.of(CurrencyMapping.fromJsonId("AA_MILES"), Points.of(new BigDecimal("100000"))),
            Map.of(), Money.zeroUsd(), List.of(), List.of());
        RewardsBreakdown rewardsUR = new RewardsBreakdown(
            Map.of(CurrencyMapping.fromJsonId("CHASE_UR"), Points.of(new BigDecimal("80000"))),
            Map.of(), Money.zeroUsd(), List.of(), List.of());
        UserGoal programAA = new UserGoal(GoalType.PROGRAM_POINTS, Optional.of(RewardCurrencyType.AA_MILES), List.of(), Map.of());

        ValuationResult aaResult = model.value(rewardsAA, programAA, (ValuationContext) null);
        ValuationResult urResult = model.value(rewardsUR, programAA, (ValuationContext) null);

        assertEquals(new BigDecimal("1300.00"), aaResult.earnedValueUsd(), "100k AA * 0.013 * 1.0 = 1300");
        assertEquals(new BigDecimal("864.00"), urResult.earnedValueUsd(), "80k UR * 0.018 * 0.6 = 864");
        assertTrue(aaResult.earnedValueUsd().compareTo(urResult.earnedValueUsd()) > 0, "AA > UR for PROGRAM+AA primary");
    }

    @Test
    void sameRewards_valuedDifferentlyByGoal() {
        ValuationModelV1 model = new ValuationModelV1(catalog);
        RewardsBreakdown breakdown = new RewardsBreakdown(
            Map.of(
                CurrencyMapping.fromJsonId("USD"), Points.of(100),
                CurrencyMapping.fromJsonId("CHASE_UR"), Points.of(1000)
            )
        );

        Money cashbackValue = model.value(breakdown, GoalType.CASHBACK, catalog);
        Money flexValue = model.value(breakdown, GoalType.FLEX_POINTS, catalog);

        assertTrue(cashbackValue.getAmount().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(flexValue.getAmount().compareTo(BigDecimal.ZERO) > 0);
        assertNotEquals(cashbackValue.getAmount(), flexValue.getAmount(),
            "Same rewards should be valued differently for CASHBACK vs FLEX_POINTS");
    }

    /** Override changes ranking: AA at 0.008 => 800; UR at 0.6 penalty => 864 wins. */
    @Test
    void spec_overrideChangesRankingDeterministically() {
        ValuationModelV1 model = new ValuationModelV1(catalog);
        RewardsBreakdown rewardsAA = new RewardsBreakdown(
            Map.of(CurrencyMapping.fromJsonId("AA_MILES"), Points.of(new BigDecimal("100000"))),
            Map.of(), Money.zeroUsd(), List.of(), List.of());
        RewardsBreakdown rewardsUR = new RewardsBreakdown(
            Map.of(CurrencyMapping.fromJsonId("CHASE_UR"), Points.of(new BigDecimal("80000"))),
            Map.of(), Money.zeroUsd(), List.of(), List.of());
        UserGoal programAALowCpp = new UserGoal(
            GoalType.PROGRAM_POINTS,
            Optional.of(RewardCurrencyType.AA_MILES),
            List.of(),
            Map.of(RewardCurrencyType.AA_MILES, new BigDecimal("0.008")));

        ValuationResult aaResult = model.value(rewardsAA, programAALowCpp, (ValuationContext) null);
        ValuationResult urResult = model.value(rewardsUR, programAALowCpp, (ValuationContext) null);

        assertEquals(new BigDecimal("800.00"), aaResult.earnedValueUsd(), "100k * 0.008 = 800");
        assertEquals(new BigDecimal("864.00"), urResult.earnedValueUsd(), "80k * 0.018 * 0.6 = 864");
        assertTrue(urResult.earnedValueUsd().compareTo(aaResult.earnedValueUsd()) > 0,
            "With AA override 0.008, UR (864) beats AA (800)");
    }

    /** AssumptionEvidence includes cpp for USD_CASH and relevant currency, and penalty multipliers. */
    @Test
    void spec_assumptionEvidenceCompleteness() {
        ValuationModelV1 model = new ValuationModelV1(catalog);
        UserGoal programAA = new UserGoal(GoalType.PROGRAM_POINTS, Optional.of(RewardCurrencyType.AA_MILES), List.of(), Map.of());
        UserGoal flexUr = new UserGoal(GoalType.FLEX_POINTS, Optional.empty(), List.of(RewardCurrencyType.BANK_UR), Map.of());

        AssumptionEvidence programEvidence = model.assumptions(programAA, (ValuationContext) null);
        AssumptionEvidence flexEvidence = model.assumptions(flexUr, (ValuationContext) null);

        assertEquals(GoalType.PROGRAM_POINTS, programEvidence.goalType());
        assertEquals("AA_MILES", programEvidence.primaryCurrencyOrNull());
        assertTrue(programEvidence.cppUsedByCurrency().containsKey("USD_CASH"));
        assertTrue(programEvidence.cppUsedByCurrency().containsKey("AA_MILES"));
        assertTrue(programEvidence.penaltiesUsedByCurrency().containsKey("BANK_UR"),
            "Non-primary currency should have penalty in evidence");

        assertEquals(GoalType.FLEX_POINTS, flexEvidence.goalType());
        assertTrue(flexEvidence.cppUsedByCurrency().containsKey("USD_CASH"));
        assertTrue(flexEvidence.cppUsedByCurrency().containsKey("BANK_UR"));
        assertTrue(flexEvidence.penaltiesUsedByCurrency().containsKey("AA_MILES"),
            "Non-preferred AA should have penalty in evidence");
    }
}
