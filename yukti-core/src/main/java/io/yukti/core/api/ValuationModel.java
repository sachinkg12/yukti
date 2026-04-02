package io.yukti.core.api;

import io.yukti.core.domain.GoalType;
import io.yukti.core.domain.Money;
import io.yukti.core.domain.RewardsBreakdown;
import io.yukti.core.domain.UserGoal;
import io.yukti.core.domain.ValuationResult;
import io.yukti.core.explainability.evidence.AssumptionEvidence;
import io.yukti.core.valuation.ValuationContext;

import java.math.BigDecimal;

/**
 * Valuates rewards breakdown in USD given a goal.
 * Valuation depends only on reward currency and user goal (no cardId/issuer).
 * Deterministic: no randomness, no network calls.
 */
public interface ValuationModel {
    String id();

    /**
     * Value rewards using context for default cpp. Returns earned, credit, and total USD.
     * ctx may be null to use implementation default (e.g. loaded default-cpp table).
     */
    ValuationResult value(RewardsBreakdown rewards, UserGoal goal, ValuationContext ctx);

    /**
     * Export assumptions used for explainability (cpp, penalties, goal).
     * ctx may be null to use implementation default.
     */
    AssumptionEvidence assumptions(UserGoal goal, ValuationContext ctx);

    /** Backward compat: value using catalog (implementation uses default context). */
    default Money value(RewardsBreakdown breakdown, GoalType goal, Catalog catalog) {
        ValuationResult r = value(breakdown, UserGoal.of(goal), (ValuationContext) null);
        return Money.usd(r.totalValueUsd());
    }

    /** Backward compat: value with UserGoal using catalog. */
    default Money value(RewardsBreakdown breakdown, UserGoal userGoal, Catalog catalog) {
        ValuationResult r = value(breakdown, userGoal, (ValuationContext) null);
        return Money.usd(r.totalValueUsd());
    }

    /** Backward compat: build assumption evidence (delegates to assumptions(goal, null)). */
    default AssumptionEvidence buildAssumptionEvidence(UserGoal userGoal, ValuationContext ctx) {
        return assumptions(userGoal, ctx);
    }
}
