package io.yukti.evaluation.taxonomy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NumberNormalizerTest {

    @Test
    void plainIntegerNormalizesToTwoDecimals() {
        assertEquals("6000.00", NumberNormalizer.normalize("6000"));
    }

    @Test
    void oneDecimalGetsPaddedToTwo() {
        assertEquals("6000.00", NumberNormalizer.normalize("6000.0"));
    }

    @Test
    void twoDecimalsStayUnchanged() {
        assertEquals("6000.00", NumberNormalizer.normalize("6000.00"));
    }

    @Test
    void dollarSignStripped() {
        assertEquals("6000.00", NumberNormalizer.normalize("$6000"));
        assertEquals("6000.00", NumberNormalizer.normalize("$6000.00"));
    }

    @Test
    void commasStripped() {
        assertEquals("6000.00", NumberNormalizer.normalize("6,000"));
        assertEquals("6000.00", NumberNormalizer.normalize("6,000.00"));
        assertEquals("1234567.00", NumberNormalizer.normalize("1,234,567"));
    }

    @Test
    void dollarAndCommaTogether() {
        assertEquals("6000.00", NumberNormalizer.normalize("$6,000.00"));
    }

    @Test
    void percentageStripped() {
        assertEquals("6.00", NumberNormalizer.normalize("6%"));
        assertEquals("6.50", NumberNormalizer.normalize("6.5%"));
    }

    @Test
    void wholeFractionalRoundsHalfUp() {
        assertEquals("6000.46", NumberNormalizer.normalize("6000.455"));
        assertEquals("6000.45", NumberNormalizer.normalize("6000.454"));
    }

    @Test
    void negativeNumberPreservesSign() {
        assertEquals("-100.00", NumberNormalizer.normalize("-100"));
        assertEquals("-1234.56", NumberNormalizer.normalize("-$1,234.56"));
    }

    @Test
    void zeroVariationsAllNormalizeToSame() {
        assertEquals("0.00", NumberNormalizer.normalize("0"));
        assertEquals("0.00", NumberNormalizer.normalize("0.00"));
        assertEquals("0.00", NumberNormalizer.normalize("$0"));
    }

    @Test
    void nonNumericReturnsNull() {
        assertNull(NumberNormalizer.normalize("abc"));
        assertNull(NumberNormalizer.normalize("not a number"));
        assertNull(NumberNormalizer.normalize(""));
        assertNull(NumberNormalizer.normalize("   "));
        assertNull(NumberNormalizer.normalize(null));
    }

    @Test
    void extractAllFromText() {
        List<String> nums = NumberNormalizer.extractAll(
            "amex-bcp: cap $6000.00 on GROCERIES, applied $6000.00, remaining $0.00");
        assertEquals(List.of("6000.00", "6000.00", "0.00"), nums);
    }

    @Test
    void extractAllHandlesRatesAndDeltas() {
        List<String> nums = NumberNormalizer.extractAll(
            "Delta is $1.03 between MILP and SA. Rate 6.0 multiplier.");
        assertTrue(nums.contains("1.03"));
        assertTrue(nums.contains("6.00"));
    }

    @Test
    void extractAllHandlesCommas() {
        List<String> nums = NumberNormalizer.extractAll("Net value is $1,495.33 across 450 instances");
        assertTrue(nums.contains("1495.33"));
        assertTrue(nums.contains("450.00"));
    }

    @Test
    void extractAllOnEmptyOrNull() {
        assertEquals(List.of(), NumberNormalizer.extractAll(""));
        assertEquals(List.of(), NumberNormalizer.extractAll(null));
    }
}
