package io.yukti.core.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {

    @Test
    void add_subtract_multiply() {
        Money a = Money.usd(100);
        Money b = Money.usd(50);
        assertEquals(Money.usd(150), a.add(b));
        assertEquals(Money.usd(50), a.subtract(b));
        assertEquals(Money.usd(200), a.multiply(BigDecimal.valueOf(2)));
    }

    @Test
    void rounding_halFUp() {
        Money m = Money.usd("10.125");
        assertEquals(BigDecimal.valueOf(10.13).setScale(2), m.getAmount());
    }

    @Test
    void zeroUsd() {
        assertEquals(BigDecimal.ZERO.setScale(2), Money.zeroUsd().getAmount());
    }
}
