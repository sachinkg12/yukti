package io.yukti.engine.reward;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RewardRoundingTest {

    @Test
    void roundPoints_scale0Down() {
        assertEquals(new BigDecimal("100"), RewardRounding.roundPoints(new BigDecimal("100.99")));
        assertEquals(new BigDecimal("999"), RewardRounding.roundPoints(new BigDecimal("999.99")));
    }

    @Test
    void roundPoints_deterministic() {
        BigDecimal v = new BigDecimal("123.456789");
        assertEquals(RewardRounding.roundPoints(v), RewardRounding.roundPoints(v));
    }
}
