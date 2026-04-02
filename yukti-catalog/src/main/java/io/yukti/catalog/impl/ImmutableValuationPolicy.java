package io.yukti.catalog.impl;

import io.yukti.core.api.ValuationPolicy;
import io.yukti.core.domain.GoalType;
import io.yukti.core.domain.RewardCurrency;

import java.math.BigDecimal;

public record ImmutableValuationPolicy(
    String id,
    RewardCurrency currency,
    GoalType goalType,
    BigDecimal centsPerUnit
) implements ValuationPolicy {}
