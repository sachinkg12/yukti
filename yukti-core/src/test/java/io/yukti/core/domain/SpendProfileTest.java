package io.yukti.core.domain;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SpendProfileTest {

    @Test
    void annualSpend_monthly_multipliesBy12() {
        var p = new SpendProfile(Period.MONTHLY, Map.of(Category.GROCERIES, Money.usd(500)));
        assertEquals(Money.usd(6000).getAmount(), p.annualSpend(Category.GROCERIES).getAmount());
    }

    @Test
    void annualSpend_annual_returnsAsIs() {
        var p = new SpendProfile(Period.ANNUAL, Map.of(Category.GROCERIES, Money.usd(6000)));
        assertEquals(Money.usd(6000).getAmount(), p.annualSpend(Category.GROCERIES).getAmount());
    }

    @Test
    void annualSpend_missingReturnsZero() {
        var p = new SpendProfile(Period.ANNUAL, Map.of(Category.GROCERIES, Money.usd(1000)));
        assertEquals(Money.zeroUsd().getAmount(), p.annualSpend(Category.DINING).getAmount());
    }
}
