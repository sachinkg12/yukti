package io.yukti.engine.valuation;

import io.yukti.core.api.Catalog;
import io.yukti.core.api.ValuationModel;
import io.yukti.core.domain.*;
import io.yukti.core.explainability.evidence.AssumptionEvidence;
import io.yukti.core.valuation.ValuationContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * ValuationModelV1: goal-aware utility function.
 * Valuation depends only on reward currency and user goal (no cardId/issuer).
 * CASHBACK: USD only (others 0). FLEX: preferred/BANK_* 1.0, else 0.8. PROGRAM: primary/preferred 1.0, else 0.6.
 * totalValueUsd = earnedValueUsd + creditValueUsd (fees handled by optimizer).
 */
public final class ValuationModelV1 implements ValuationModel {
    private static final int USD_SCALE = 2;
    private static final RoundingMode USD_ROUNDING = RoundingMode.HALF_UP;

    private final CppResolver cppResolver;
    private final ValuationContext defaultContext;

    public ValuationModelV1(Catalog catalog) {
        this.defaultContext = DefaultCppTableLoader.load();
        this.cppResolver = new CppResolver(defaultContext);
    }

    public ValuationModelV1(ValuationContext context) {
        this.defaultContext = context;
        this.cppResolver = new CppResolver(context);
    }

    @Override
    public String id() {
        return "valuation-v1";
    }

    @Override
    public ValuationResult value(RewardsBreakdown rewards, UserGoal goal, ValuationContext ctx) {
        ValuationContext c = ctx != null ? ctx : defaultContext;
        CppResolver resolver = (c == defaultContext) ? cppResolver : new CppResolver(c);

        BigDecimal earnedValueUsd = BigDecimal.ZERO;
        for (Map.Entry<RewardCurrency, Points> e : rewards.getByCurrency().entrySet()) {
            RewardCurrency curr = e.getKey();
            Points pts = e.getValue();
            BigDecimal usdPerPoint = resolver.usdPerPoint(curr, goal);
            if (usdPerPoint.compareTo(BigDecimal.ZERO) <= 0) continue;
            if (curr.getType() == RewardCurrencyType.USD_CASH) {
                earnedValueUsd = earnedValueUsd.add(pts.getAmount());
            } else {
                BigDecimal usd = pts.getAmount().multiply(usdPerPoint);
                earnedValueUsd = earnedValueUsd.add(usd.setScale(USD_SCALE, USD_ROUNDING));
            }
        }
        earnedValueUsd = earnedValueUsd.setScale(USD_SCALE, USD_ROUNDING);

        BigDecimal creditValueUsd = rewards.getCreditsUSD() != null
            ? rewards.getCreditsUSD().getAmount()
            : BigDecimal.ZERO;
        creditValueUsd = creditValueUsd.setScale(USD_SCALE, USD_ROUNDING);

        BigDecimal totalValueUsd = earnedValueUsd.add(creditValueUsd);
        return new ValuationResult(earnedValueUsd, creditValueUsd, totalValueUsd);
    }

    @Override
    public AssumptionEvidence assumptions(UserGoal goal, ValuationContext ctx) {
        ValuationContext c = ctx != null ? ctx : defaultContext;
        CppResolver resolver = (c == defaultContext) ? cppResolver : new CppResolver(c);
        return resolver.buildAssumptionEvidence(goal, null);
    }

    @Override
    public Money value(RewardsBreakdown breakdown, GoalType goal, Catalog catalog) {
        return Money.usd(value(breakdown, UserGoal.of(goal), (ValuationContext) null).totalValueUsd());
    }

    @Override
    public Money value(RewardsBreakdown breakdown, UserGoal userGoal, Catalog catalog) {
        return Money.usd(value(breakdown, userGoal, (ValuationContext) null).totalValueUsd());
    }
}
