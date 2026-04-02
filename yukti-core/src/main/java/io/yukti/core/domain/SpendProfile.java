package io.yukti.core.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * User spend by category. Amounts in Money; period indicates whether values are monthly or annual.
 */
public final class SpendProfile {
    private final Period period;
    private final Map<Category, Money> amounts;

    public SpendProfile(Period period, Map<Category, Money> amounts) {
        this.period = Objects.requireNonNull(period);
        this.amounts = Collections.unmodifiableMap(new EnumMap<>(Objects.requireNonNull(amounts)));
    }

    public Period getPeriod() {
        return period;
    }

    public Map<Category, Money> getAmounts() {
        return amounts;
    }

    /** Annual spend for the given category. */
    public Money annualSpend(Category cat) {
        Money m = amounts.getOrDefault(cat, Money.zeroUsd());
        return period == Period.MONTHLY ? m.multiply(java.math.BigDecimal.valueOf(12)) : m;
    }

    public boolean isEmpty() {
        return amounts.values().stream().allMatch(m -> m.getAmount().compareTo(java.math.BigDecimal.ZERO) == 0);
    }
}
