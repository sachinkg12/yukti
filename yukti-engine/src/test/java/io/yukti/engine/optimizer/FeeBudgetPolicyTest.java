package io.yukti.engine.optimizer;

import io.yukti.catalog.impl.ImmutableCard;
import io.yukti.catalog.impl.ImmutableCatalog;
import io.yukti.catalog.impl.ImmutableRewardsRule;
import io.yukti.core.api.Card;
import io.yukti.core.api.Catalog;
import io.yukti.core.domain.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FeeBudgetPolicyTest {

    private static final RewardCurrency USD = new RewardCurrency(RewardCurrencyType.USD_CASH, "USD");

    @Test
    void enforce_returnsTrueWhenPortfolioWithinBudget() {
        Card card1 = new ImmutableCard("card-a", "Card A", "Issuer", Money.usd(50),
            List.of(new ImmutableRewardsRule("r1", Category.OTHER, new BigDecimal("0.02"), Optional.empty(), Optional.empty(), USD)),
            Money.zeroUsd());
        Card card2 = new ImmutableCard("card-b", "Card B", "Issuer", Money.usd(50),
            List.of(new ImmutableRewardsRule("r2", Category.OTHER, new BigDecimal("0.02"), Optional.empty(), Optional.empty(), USD)),
            Money.zeroUsd());
        Catalog catalog = new ImmutableCatalog("1.0", List.of(card1, card2), List.of());
        UserConstraints constraints = new UserConstraints(3, Money.usd(150), true);
        FeeBudgetPolicy policy = new FeeBudgetPolicy();

        assertTrue(policy.enforce(List.of("card-a"), constraints, catalog));
        assertTrue(policy.enforce(List.of("card-a", "card-b"), constraints, catalog));
    }

    @Test
    void enforce_returnsFalseWhenPortfolioExceedsBudget() {
        Card card1 = new ImmutableCard("card-a", "Card A", "Issuer", Money.usd(100),
            List.of(new ImmutableRewardsRule("r1", Category.OTHER, new BigDecimal("0.02"), Optional.empty(), Optional.empty(), USD)),
            Money.zeroUsd());
        Card card2 = new ImmutableCard("card-b", "Card B", "Issuer", Money.usd(100),
            List.of(new ImmutableRewardsRule("r2", Category.OTHER, new BigDecimal("0.02"), Optional.empty(), Optional.empty(), USD)),
            Money.zeroUsd());
        Catalog catalog = new ImmutableCatalog("1.0", List.of(card1, card2), List.of());
        UserConstraints constraints = new UserConstraints(3, Money.usd(150), true);
        FeeBudgetPolicy policy = new FeeBudgetPolicy();

        assertTrue(policy.enforce(List.of("card-a"), constraints, catalog));
        assertFalse(policy.enforce(List.of("card-a", "card-b"), constraints, catalog));
    }

    @Test
    void enforce_deterministicSameInputSameOutput() {
        Card card = new ImmutableCard("c1", "C1", "I", Money.usd(95),
            List.of(new ImmutableRewardsRule("r", Category.OTHER, BigDecimal.ONE, Optional.empty(), Optional.empty(), USD)),
            Money.zeroUsd());
        Catalog catalog = new ImmutableCatalog("1.0", List.of(card), List.of());
        UserConstraints constraints = new UserConstraints(1, Money.usd(100), true);
        FeeBudgetPolicy policy = new FeeBudgetPolicy();

        boolean a = policy.enforce(List.of("c1"), constraints, catalog);
        boolean b = policy.enforce(List.of("c1"), constraints, catalog);
        assertEquals(a, b);
        assertTrue(a);
    }
}
