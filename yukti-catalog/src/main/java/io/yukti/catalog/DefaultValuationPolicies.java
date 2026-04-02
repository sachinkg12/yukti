package io.yukti.catalog;

import io.yukti.catalog.impl.ImmutableValuationPolicy;
import io.yukti.catalog.util.CurrencyMapping;
import io.yukti.core.api.ValuationPolicy;
import io.yukti.core.domain.GoalType;

import java.math.BigDecimal;
import java.util.List;

/**
 * Default valuation policies for goal-aware optimization.
 */
public final class DefaultValuationPolicies {
    private DefaultValuationPolicies() {}

    public static List<ValuationPolicy> defaults() {
        return List.of(
            new ImmutableValuationPolicy("usd-cashback", CurrencyMapping.fromJsonId("USD"), GoalType.CASHBACK, new BigDecimal("100")),
            new ImmutableValuationPolicy("chase-ur-flex", CurrencyMapping.fromJsonId("CHASE_UR"), GoalType.FLEX_POINTS, new BigDecimal("1.25")),
            new ImmutableValuationPolicy("amex-mr-flex", CurrencyMapping.fromJsonId("AMEX_MR"), GoalType.FLEX_POINTS, new BigDecimal("1.2")),
            new ImmutableValuationPolicy("citi-ty-flex", CurrencyMapping.fromJsonId("CITI_TYP"), GoalType.FLEX_POINTS, new BigDecimal("1.25")),
            new ImmutableValuationPolicy("cap1-venture-flex", CurrencyMapping.fromJsonId("CAP1_VENTURE"), GoalType.FLEX_POINTS, new BigDecimal("1")),
            new ImmutableValuationPolicy("avios-program", CurrencyMapping.fromJsonId("AVIOS"), GoalType.PROGRAM_POINTS, new BigDecimal("1.3"))
        );
    }
}
