package io.yukti.engine.math;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoundingRulesTest {

    @Test
    void roundUsd_halfUp_scale2() {
        assertEquals(new BigDecimal("6.67"), RoundingRules.roundUsd(new BigDecimal("6.6666")));
        assertEquals(new BigDecimal("160.00"), RoundingRules.roundUsd(new BigDecimal("160.0")));
    }

    @Test
    void roundPoints_down_scale0() {
        assertEquals(new BigDecimal("999"), RoundingRules.roundPoints(new BigDecimal("999.99")));
        assertEquals(new BigDecimal("20000"), RoundingRules.roundPoints(new BigDecimal("20000.0")));
    }
}
