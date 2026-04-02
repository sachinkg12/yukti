package io.yukti.engine.reward;

import io.yukti.engine.math.RoundingRules;

import java.math.BigDecimal;

/**
 * Delegates to RoundingRules. Kept for backward compatibility.
 * @deprecated Use {@link RoundingRules} directly.
 */
@Deprecated
public final class RewardRounding {

    private RewardRounding() {}

    /** Round points to integer (DOWN). */
    public static BigDecimal roundPoints(BigDecimal value) {
        return RoundingRules.roundPoints(value);
    }
}
