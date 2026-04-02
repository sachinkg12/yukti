package io.yukti.engine.valuation;

import io.yukti.core.domain.GoalType;
import io.yukti.core.domain.RewardCurrencyType;
import io.yukti.core.domain.UserGoal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * PenaltyPolicyV1: deterministic penalty multipliers by goal and currency.
 * CASHBACK: USD_CASH => 1.0, others => 0.0 (strict).
 * FLEX_POINTS: preferred (or BANK_*) => 1.0, else 0.8.
 * PROGRAM_POINTS: primary => 1.0, preferred => 1.0, else 0.6 (strict) or 0.85 (soft).
 * When primary is not set, default is AVIOS (RewardsBench v1; paper §2.3.1, §6).
 * Ablation: set strictPenalty=false for soft (non-primary = 0.85). See docs/reproducibility.md.
 */
public final class PenaltyPolicyV1 {

    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal FLEX_NON_PREFERRED = new BigDecimal("0.8");
    private static final BigDecimal PROGRAM_NON_PRIMARY_STRICT = new BigDecimal("0.6");
    private static final BigDecimal PROGRAM_NON_PRIMARY_SOFT = new BigDecimal("0.85");

    /** When true (default), PROGRAM_POINTS non-primary uses 0.6; when false (ablation soft), uses 0.85. */
    private static volatile boolean strictPenalty = true;

    private PenaltyPolicyV1() {}

    public static boolean isStrictPenalty() {
        return strictPenalty;
    }

    /** Set false for ablation: soft penalty (non-primary 0.85). Default true. */
    public static void setStrictPenalty(boolean strict) {
        strictPenalty = strict;
    }

    public static BigDecimal penalty(RewardCurrencyType currency, UserGoal goal) {
        GoalType g = goal.getGoalType();
        return switch (g) {
            case CASHBACK -> currency == RewardCurrencyType.USD_CASH ? ONE : ZERO;
            case FLEX_POINTS -> {
                if (currency == RewardCurrencyType.USD_CASH) yield ZERO;
                List<RewardCurrencyType> pref = goal.getPreferredCurrencies();
                if (pref.isEmpty() && isBankPoints(currency)) yield ONE;
                if (pref.contains(currency)) yield ONE;
                yield FLEX_NON_PREFERRED;
            }
            case PROGRAM_POINTS -> {
                Optional<RewardCurrencyType> primary = goal.getPrimaryCurrency();
                // RewardsBench v1 uses AVIOS (BenchmarkHarness sets it explicitly; fallback for API/callers that omit primary).
                RewardCurrencyType primaryType = primary.orElse(RewardCurrencyType.AVIOS);
                if (currency == primaryType) yield ONE;
                if (goal.getPreferredCurrencies().contains(currency)) yield ONE;
                yield strictPenalty ? PROGRAM_NON_PRIMARY_STRICT : PROGRAM_NON_PRIMARY_SOFT;
            }
        };
    }

    private static boolean isBankPoints(RewardCurrencyType c) {
        return c == RewardCurrencyType.BANK_UR || c == RewardCurrencyType.BANK_MR
            || c == RewardCurrencyType.BANK_TY || c == RewardCurrencyType.BANK_C1
            || c == RewardCurrencyType.BILT_POINTS || c == RewardCurrencyType.WF_REWARDS;
    }
}
