package io.yukti.core.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Points value (reward units). Immutable.
 */
public final class Points {
    private final BigDecimal amount;

    public Points(BigDecimal amount) {
        this.amount = Objects.requireNonNull(amount);
    }

    public static Points of(long amount) {
        return new Points(BigDecimal.valueOf(amount));
    }

    public static Points of(BigDecimal amount) {
        return new Points(amount);
    }

    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Points points = (Points) o;
        return amount.compareTo(points.amount) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount);
    }
}
