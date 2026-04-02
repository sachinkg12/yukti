package io.yukti.core.domain;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * User goal: goalType, primary currency (optional), preferred currencies, cpp overrides.
 */
public final class UserGoal {
    private final GoalType goalType;
    private final Optional<RewardCurrencyType> primaryCurrency;
    private final List<RewardCurrencyType> preferredCurrencies;
    private final Map<RewardCurrencyType, java.math.BigDecimal> cppOverrides;

    public UserGoal(
        GoalType goalType,
        Optional<RewardCurrencyType> primaryCurrency,
        List<RewardCurrencyType> preferredCurrencies,
        Map<RewardCurrencyType, java.math.BigDecimal> cppOverrides
    ) {
        this.goalType = Objects.requireNonNull(goalType);
        this.primaryCurrency = primaryCurrency != null ? primaryCurrency : Optional.empty();
        this.preferredCurrencies = preferredCurrencies != null ? List.copyOf(preferredCurrencies) : List.of();
        this.cppOverrides = cppOverrides != null ? Map.copyOf(cppOverrides) : Map.of();
    }

    public static UserGoal of(GoalType goalType) {
        return new UserGoal(goalType, Optional.empty(), List.of(), Map.of());
    }

    public GoalType getGoalType() {
        return goalType;
    }

    public Optional<RewardCurrencyType> getPrimaryCurrency() {
        return primaryCurrency;
    }

    public List<RewardCurrencyType> getPreferredCurrencies() {
        return preferredCurrencies;
    }

    public Map<RewardCurrencyType, java.math.BigDecimal> getCppOverrides() {
        return cppOverrides;
    }

    public Optional<java.math.BigDecimal> getCppOverride(RewardCurrencyType currency) {
        return Optional.ofNullable(cppOverrides.get(currency));
    }
}
