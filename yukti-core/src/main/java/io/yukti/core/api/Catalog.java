package io.yukti.core.api;

import io.yukti.core.domain.RewardCurrency;
import io.yukti.core.domain.GoalType;

import java.util.Collection;
import java.util.Optional;

/**
 * Card catalog. Provides cards and valuation policies.
 */
public interface Catalog {
    String version();
    Collection<? extends Card> cards();
    Collection<? extends ValuationPolicy> valuationPolicies();
    Optional<ValuationPolicy> valuationPolicy(RewardCurrency currency, GoalType goal);
}
