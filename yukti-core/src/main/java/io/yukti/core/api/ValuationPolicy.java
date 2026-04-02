package io.yukti.core.api;

import io.yukti.core.domain.GoalType;
import io.yukti.core.domain.RewardCurrency;

import java.math.BigDecimal;

/**
 * Goal-aware valuation: cents per point/unit.
 */
public interface ValuationPolicy {
    String id();
    RewardCurrency currency();
    GoalType goalType();
    BigDecimal centsPerUnit();
}
