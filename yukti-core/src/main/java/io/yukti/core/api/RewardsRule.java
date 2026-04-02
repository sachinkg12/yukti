package io.yukti.core.api;

import io.yukti.core.domain.Cap;
import io.yukti.core.domain.Category;
import io.yukti.core.domain.RewardCurrency;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Rewards rule: category, rate (multiplier), optional cap, optional fallback multiplier, currency.
 * Matches paper §2.2 and DSL: multiplier m_{i,c}, cap K_{i,c}, fallback b_{i,c}.
 */
public interface RewardsRule {
    String id();
    Category category();
    /** Earn rate / multiplier (e.g., 0.06 = 6%) for cap segment. */
    BigDecimal rate();
    /** Empty = no cap (unlimited). */
    Optional<Cap> cap();
    /** Fallback multiplier after cap (b_{i,c}). Empty = engine infers (e.g. from another rule or default). */
    Optional<BigDecimal> fallbackMultiplier();
    RewardCurrency currency();
}
