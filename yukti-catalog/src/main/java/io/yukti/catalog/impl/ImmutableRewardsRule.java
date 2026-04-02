package io.yukti.catalog.impl;

import io.yukti.core.api.RewardsRule;
import io.yukti.core.domain.Cap;
import io.yukti.core.domain.Category;
import io.yukti.core.domain.Period;
import io.yukti.core.domain.RewardCurrency;

import java.math.BigDecimal;
import java.util.Optional;

public record ImmutableRewardsRule(
    String id,
    Category category,
    BigDecimal rate,
    Optional<Cap> cap,
    Optional<BigDecimal> fallbackMultiplier,
    RewardCurrency currency
) implements RewardsRule {}
