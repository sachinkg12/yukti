package io.yukti.core.valuation;

import io.yukti.core.domain.RewardCurrencyType;

import java.math.BigDecimal;

/**
 * Context for valuation: provides default USD-per-point (cpp) by currency.
 * Single unit across system: USD per point (e.g. 0.013 = 1.3 cents per point).
 * Implementations load from config (e.g. default-cpp.v1.json) or provide overrides.
 */
public interface ValuationContext {
    /**
     * Default cpp for the given currency. USD_CASH must be 1.0.
     * For points/miles: USD per point (e.g. 0.013 for AA_MILES).
     */
    BigDecimal getUsdPerPoint(RewardCurrencyType currency);
}
