package io.yukti.engine.valuation;

import io.yukti.core.domain.RewardCurrencyType;
import io.yukti.core.valuation.ValuationContext;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * Default USD-per-point table. Units: USD per point (0.013 = 1.3 cents per point).
 * Load via DefaultCppTableLoader.load() or use fallbackDefaults().
 */
public final class DefaultCppTable implements ValuationContext {
    private final Map<RewardCurrencyType, BigDecimal> usdPerPoint;

    public DefaultCppTable() {
        this(DefaultCppTableLoader.load().getUsdPerPointMap());
    }

    public DefaultCppTable(Map<RewardCurrencyType, BigDecimal> usdPerPoint) {
        this.usdPerPoint = Map.copyOf(usdPerPoint);
    }

    public BigDecimal getUsdPerPoint(RewardCurrencyType currency) {
        return usdPerPoint.getOrDefault(currency, BigDecimal.ZERO);
    }

    Map<RewardCurrencyType, BigDecimal> getUsdPerPointMap() {
        return Map.copyOf(usdPerPoint);
    }

    /** @deprecated Use getUsdPerPoint; returns cents = usdPerPoint * 100 for backward compat. */
    @Deprecated
    public BigDecimal getDefaultCpp(RewardCurrencyType currency) {
        return getUsdPerPoint(currency).multiply(BigDecimal.valueOf(100));
    }

    /** @deprecated Penalty now in PenaltyPolicyV1; returns 0.6 for PROGRAM non-primary. */
    @Deprecated
    public BigDecimal getProgramPrimaryPenalty() {
        return new BigDecimal("0.6");
    }

    /** Fallback when v1 config unavailable. Units: USD per point. */
    public static DefaultCppTable fallbackDefaults() {
        Map<RewardCurrencyType, BigDecimal> m = new EnumMap<>(RewardCurrencyType.class);
        m.put(RewardCurrencyType.USD_CASH, new BigDecimal("1.000"));
        m.put(RewardCurrencyType.BANK_UR, new BigDecimal("0.018"));
        m.put(RewardCurrencyType.BANK_MR, new BigDecimal("0.017"));
        m.put(RewardCurrencyType.BANK_TY, new BigDecimal("0.016"));
        m.put(RewardCurrencyType.BANK_C1, new BigDecimal("0.017"));
        m.put(RewardCurrencyType.AA_MILES, new BigDecimal("0.013"));
        m.put(RewardCurrencyType.AVIOS, new BigDecimal("0.017"));
        m.put(RewardCurrencyType.UNITED_MILES, new BigDecimal("0.013"));
        m.put(RewardCurrencyType.DELTA_SKYMILES, new BigDecimal("0.012"));
        m.put(RewardCurrencyType.MARRIOTT_POINTS, new BigDecimal("0.008"));
        m.put(RewardCurrencyType.HILTON_POINTS, new BigDecimal("0.005"));
        m.put(RewardCurrencyType.HYATT_POINTS, new BigDecimal("0.017"));
        m.put(RewardCurrencyType.SOUTHWEST_RR, new BigDecimal("0.014"));
        m.put(RewardCurrencyType.IHG_POINTS, new BigDecimal("0.005"));
        m.put(RewardCurrencyType.WYNDHAM_POINTS, new BigDecimal("0.009"));
        m.put(RewardCurrencyType.JETBLUE_POINTS, new BigDecimal("0.013"));
        m.put(RewardCurrencyType.BILT_POINTS, new BigDecimal("0.015"));
        m.put(RewardCurrencyType.AEROPLAN, new BigDecimal("0.015"));
        m.put(RewardCurrencyType.WF_REWARDS, new BigDecimal("0.010"));
        return new DefaultCppTable(m);
    }
}
