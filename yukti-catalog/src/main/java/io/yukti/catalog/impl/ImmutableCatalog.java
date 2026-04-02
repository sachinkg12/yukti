package io.yukti.catalog.impl;

import io.yukti.core.api.Card;
import io.yukti.core.api.Catalog;
import io.yukti.core.api.ValuationPolicy;
import io.yukti.core.domain.GoalType;
import io.yukti.core.domain.RewardCurrency;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public record ImmutableCatalog(
    String version,
    List<Card> cards,
    List<ValuationPolicy> valuationPolicies
) implements Catalog {

    @Override
    public Optional<ValuationPolicy> valuationPolicy(RewardCurrency currency, GoalType goal) {
        return valuationPolicies.stream()
            .filter(v -> v.currency().getType().equals(currency.getType()) && v.goalType() == goal)
            .findFirst();
    }
}
