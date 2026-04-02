package io.yukti.core.domain;

import java.util.Objects;

/**
 * Reward currency value object (type + display name).
 */
public final class RewardCurrency {
    private final RewardCurrencyType type;
    private final String displayName;

    public RewardCurrency(RewardCurrencyType type, String displayName) {
        this.type = Objects.requireNonNull(type);
        this.displayName = displayName != null ? displayName : type.name();
    }

    public RewardCurrencyType getType() {
        return type;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getId() {
        return type.name();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RewardCurrency that = (RewardCurrency) o;
        return type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type);
    }
}
