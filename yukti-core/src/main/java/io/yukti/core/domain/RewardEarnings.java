package io.yukti.core.domain;

import java.util.Objects;

/**
 * Reward earnings from a single rule evaluation.
 */
public final class RewardEarnings {
    private final RewardCurrency currency;
    private final Points points;

    public RewardEarnings(RewardCurrency currency, Points points) {
        this.currency = Objects.requireNonNull(currency);
        this.points = Objects.requireNonNull(points);
    }

    public RewardCurrency getCurrency() {
        return currency;
    }

    public Points getPoints() {
        return points;
    }
}
