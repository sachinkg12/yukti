package io.yukti.engine.math;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Centralized rounding rules for reward computation.
 * USD amounts: scale 2, HALF_UP (standard currency rounding).
 * Points: scale 0 (integer), DOWN (issuers often floor points; keep consistent).
 */
public final class RoundingRules {

    public static final int USD_SCALE = 2;
    public static final RoundingMode USD_ROUNDING = RoundingMode.HALF_UP;

    public static final int POINTS_SCALE = 0;
    public static final RoundingMode POINTS_ROUNDING = RoundingMode.DOWN;

    private RoundingRules() {}

    /** Round USD amount to 2 decimals, HALF_UP. */
    public static BigDecimal roundUsd(BigDecimal value) {
        return value.setScale(USD_SCALE, USD_ROUNDING);
    }

    /** Round points to integer, DOWN (floor). */
    public static BigDecimal roundPoints(BigDecimal value) {
        return value.setScale(POINTS_SCALE, POINTS_ROUNDING);
    }
}
