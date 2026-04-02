package io.yukti.core.domain;

import java.util.Objects;

/**
 * User constraints for portfolio optimization.
 */
public final class UserConstraints {
    private final int maxCards;
    private final Money maxAnnualFee;
    private final boolean allowBusinessCards;

    public UserConstraints(int maxCards, Money maxAnnualFee, boolean allowBusinessCards) {
        this.maxCards = maxCards;
        this.maxAnnualFee = Objects.requireNonNull(maxAnnualFee);
        this.allowBusinessCards = allowBusinessCards;
    }

    public static UserConstraints defaults() {
        return new UserConstraints(3, Money.usd(1000), true);
    }

    public int getMaxCards() {
        return maxCards;
    }

    public Money getMaxAnnualFee() {
        return maxAnnualFee;
    }

    public boolean isAllowBusinessCards() {
        return allowBusinessCards;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserConstraints that = (UserConstraints) o;
        return maxCards == that.maxCards
            && allowBusinessCards == that.allowBusinessCards
            && maxAnnualFee.equals(that.maxAnnualFee);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxCards, maxAnnualFee, allowBusinessCards);
    }
}
