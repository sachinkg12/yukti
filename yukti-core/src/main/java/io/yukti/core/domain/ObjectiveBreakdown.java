package io.yukti.core.domain;

import java.util.Objects;

/**
 * Objective breakdown: earn value, credits value, fees, net.
 */
public final class ObjectiveBreakdown {
    private final Money earnValue;
    private final Money creditsValue;
    private final Money fees;

    public ObjectiveBreakdown(Money earnValue, Money creditsValue, Money fees) {
        this.earnValue = Objects.requireNonNull(earnValue);
        this.creditsValue = Objects.requireNonNull(creditsValue);
        this.fees = Objects.requireNonNull(fees);
    }

    public Money getEarnValue() {
        return earnValue;
    }

    public Money getCreditsValue() {
        return creditsValue;
    }

    public Money getFees() {
        return fees;
    }

    public Money getNet() {
        return earnValue.add(creditsValue).subtract(fees);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ObjectiveBreakdown that = (ObjectiveBreakdown) o;
        return earnValue.equals(that.earnValue)
            && creditsValue.equals(that.creditsValue)
            && fees.equals(that.fees);
    }

    @Override
    public int hashCode() {
        return Objects.hash(earnValue, creditsValue, fees);
    }
}
