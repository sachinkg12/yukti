package io.yukti.core.domain;

import java.util.Map;
import java.util.Objects;

/**
 * Parsed preferences from free-text (or structured input).
 * Phase-1: deterministic parser; optional AI later.
 */
public final class ParsedPreferences {
    private final UserGoal userGoal;
    private final UserConstraints userConstraints;
    private final Map<String, String> raw;

    public ParsedPreferences(UserGoal userGoal, UserConstraints userConstraints, Map<String, String> raw) {
        this.userGoal = Objects.requireNonNull(userGoal);
        this.userConstraints = Objects.requireNonNull(userConstraints);
        this.raw = raw != null ? Map.copyOf(raw) : Map.of();
    }

    public UserGoal getUserGoal() {
        return userGoal;
    }

    public UserConstraints getUserConstraints() {
        return userConstraints;
    }

    public Map<String, String> getRaw() {
        return raw;
    }
}
