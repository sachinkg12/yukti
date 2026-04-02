package io.yukti.core.domain;

import java.util.Map;
import java.util.Objects;

/**
 * Request for portfolio optimization.
 */
public final class OptimizationRequest {
    private final SpendProfile spendProfile;
    private final UserGoal userGoal;
    private final UserConstraints userConstraints;
    private final Map<String, String> preferences;

    public OptimizationRequest(
        SpendProfile spendProfile,
        UserGoal userGoal,
        UserConstraints userConstraints,
        Map<String, String> preferences
    ) {
        this.spendProfile = Objects.requireNonNull(spendProfile);
        this.userGoal = Objects.requireNonNull(userGoal);
        this.userConstraints = Objects.requireNonNull(userConstraints);
        this.preferences = preferences != null ? Map.copyOf(preferences) : Map.of();
    }

    public SpendProfile getSpendProfile() {
        return spendProfile;
    }

    public UserGoal getUserGoal() {
        return userGoal;
    }

    public UserConstraints getUserConstraints() {
        return userConstraints;
    }

    public Map<String, String> getPreferences() {
        return preferences;
    }
}
