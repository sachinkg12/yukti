package io.yukti.core.domain;

import java.util.Objects;

/**
 * Cap: amount + period (e.g., $6000 annual).
 */
public final class Cap {
    private final Money amount;
    private final Period period;

    public Cap(Money amount, Period period) {
        this.amount = Objects.requireNonNull(amount);
        this.period = Objects.requireNonNull(period);
    }

    public Money getAmount() {
        return amount;
    }

    public Period getPeriod() {
        return period;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Cap cap = (Cap) o;
        return amount.equals(cap.amount) && period == cap.period;
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, period);
    }
}
