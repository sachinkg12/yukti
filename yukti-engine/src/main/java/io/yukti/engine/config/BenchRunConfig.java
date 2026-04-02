package io.yukti.engine.config;

import io.yukti.core.api.Card;
import io.yukti.core.domain.Money;

/**
 * Bench/ablation run configuration. Used by the benchmark harness and allocation
 * solver to switch credits in objective (ablations: credits enabled vs disabled).
 * Default: credits included. Set via {@link #setCreditsInObjective(boolean)} before
 * running the harness for ablation runs.
 */
public final class BenchRunConfig {

    private static volatile boolean creditsInObjective = true;
    private static volatile double creditUtilizationMultiplier = 1.0;

    private BenchRunConfig() {}

    /** Default true. When false, statement credits EV is treated as 0 in the objective (ablation: credits disabled). */
    public static boolean isCreditsInObjective() {
        return creditsInObjective;
    }

    public static void setCreditsInObjective(boolean value) {
        creditsInObjective = value;
    }

    /**
     * Credit utilization multiplier: fraction of statement credits actually realized.
     * Default 1.0 (full utilization). Set lower for sensitivity analysis modeling
     * the risk that cardholders may not fully utilize all statement credits.
     */
    public static double getCreditUtilizationMultiplier() {
        return creditUtilizationMultiplier;
    }

    public static void setCreditUtilizationMultiplier(double multiplier) {
        if (multiplier < 0 || multiplier > 1) {
            throw new IllegalArgumentException("creditUtilizationMultiplier must be in [0,1], got " + multiplier);
        }
        creditUtilizationMultiplier = multiplier;
    }

    /** Returns card's statement credits annual or zero when credits are disabled, scaled by utilization multiplier. */
    public static Money effectiveCredits(Card card) {
        if (!creditsInObjective) return Money.zeroUsd();
        Money base = card.statementCreditsAnnual();
        if (creditUtilizationMultiplier == 1.0) return base;
        return Money.usd(base.getAmount().doubleValue() * creditUtilizationMultiplier);
    }
}
