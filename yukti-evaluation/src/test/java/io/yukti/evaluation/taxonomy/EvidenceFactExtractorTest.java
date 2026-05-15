package io.yukti.evaluation.taxonomy;

import io.yukti.core.domain.Category;
import io.yukti.core.domain.GoalType;
import io.yukti.core.explainability.StructuredExplanation;
import io.yukti.core.explainability.evidence.EvidenceBlock;
import io.yukti.core.explainability.evidence.LegacyEvidenceBlock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceFactExtractorTest {

    private StructuredExplanation buildEvidence(List<EvidenceBlock> blocks) {
        return new StructuredExplanation(
            "v1.0.0",
            GoalType.CASHBACK,
            "USD_CASH",
            Map.of(Category.GROCERIES, "amex-bcp"),
            List.of("amex-bcp", "usbank-cash-plus"),
            new StructuredExplanation.Breakdown(
                new BigDecimal("855.00"),
                new BigDecimal("100.00"),
                new BigDecimal("95.00"),
                new BigDecimal("860.00")
            ),
            blocks,
            "digest",
            List.of("ev-1", "ev-2")
        );
    }

    @Test
    void allowedNumbersIncludesBreakdownTotals() {
        var ev = buildEvidence(List.of());
        var nums = EvidenceFactExtractor.allowedNumbers(ev);
        assertTrue(nums.contains("855.00"), "earn value");
        assertTrue(nums.contains("100.00"), "credits");
        assertTrue(nums.contains("95.00"), "fees");
        assertTrue(nums.contains("860.00"), "net");
    }

    @Test
    void allowedNumbersIncludesEvidenceBlockNumbers() {
        var ev = buildEvidence(List.of(
            new LegacyEvidenceBlock("CAP_HIT", "amex-bcp", "GROCERIES",
                "amex-bcp: cap $6000.00 on GROCERIES, applied $6000.00, remaining $0.00")
        ));
        var nums = EvidenceFactExtractor.allowedNumbers(ev);
        assertTrue(nums.contains("6000.00"), "cap should be extracted");
        assertTrue(nums.contains("0.00"), "remaining should be extracted");
    }

    @Test
    void allowedNumbersIncludesMultipleEvidenceBlocks() {
        var ev = buildEvidence(List.of(
            new LegacyEvidenceBlock("CAP_HIT", "amex-bcp", "GROCERIES",
                "cap $6000.00, remaining $0.00"),
            new LegacyEvidenceBlock("WINNER_BY_CATEGORY", "amex-bcp", "GROCERIES",
                "amex-bcp wins GROCERIES over usbank: delta $45.60"),
            new LegacyEvidenceBlock("EARN_RATE", "amex-bcp", "GROCERIES",
                "multiplier=6.0 fallback=1.0")
        ));
        var nums = EvidenceFactExtractor.allowedNumbers(ev);
        assertTrue(nums.contains("6000.00"));
        assertTrue(nums.contains("0.00"));
        assertTrue(nums.contains("45.60"));
        assertTrue(nums.contains("6.00"));
        assertTrue(nums.contains("1.00"));
    }

    @Test
    void allowedEntitiesIncludesPortfolioCards() {
        var ev = buildEvidence(List.of());
        var entities = EvidenceFactExtractor.allowedEntities(ev);
        assertTrue(entities.contains("amex-bcp"));
        assertTrue(entities.contains("usbank-cash-plus"));
    }

    @Test
    void allowedEntitiesIncludesAllSixCategories() {
        var ev = buildEvidence(List.of());
        var entities = EvidenceFactExtractor.allowedEntities(ev);
        assertTrue(entities.contains("GROCERIES"));
        assertTrue(entities.contains("DINING"));
        assertTrue(entities.contains("GAS"));
        assertTrue(entities.contains("TRAVEL"));
        assertTrue(entities.contains("ONLINE"));
        assertTrue(entities.contains("OTHER"));
    }

    @Test
    void allowedEntitiesIncludesPrimaryCurrency() {
        var ev = buildEvidence(List.of());
        var entities = EvidenceFactExtractor.allowedEntities(ev);
        assertTrue(entities.contains("USD_CASH"));
    }

    @Test
    void allowedEntitiesIncludesEvidenceBlockCardIds() {
        var ev = buildEvidence(List.of(
            new LegacyEvidenceBlock("WINNER_BY_CATEGORY", "chase-sapphire-reserve", "TRAVEL", "csr wins")
        ));
        var entities = EvidenceFactExtractor.allowedEntities(ev);
        assertTrue(entities.contains("chase-sapphire-reserve"));
    }

    @Test
    void isAllowedNumberHandlesFormattingVariants() {
        Set<String> allowed = Set.of("6000.00", "45.60", "0.00");
        assertTrue(EvidenceFactExtractor.isAllowedNumber("$6,000.00", allowed));
        assertTrue(EvidenceFactExtractor.isAllowedNumber("6000", allowed));
        assertTrue(EvidenceFactExtractor.isAllowedNumber("6000.0", allowed));
        assertTrue(EvidenceFactExtractor.isAllowedNumber("$6000.00", allowed));
        assertTrue(EvidenceFactExtractor.isAllowedNumber("45.6", allowed));
        assertFalse(EvidenceFactExtractor.isAllowedNumber("99.99", allowed));
        assertFalse(EvidenceFactExtractor.isAllowedNumber(null, allowed));
        assertFalse(EvidenceFactExtractor.isAllowedNumber("not a number", allowed));
    }

    @Test
    void knownCategoriesHasSixValues() {
        assertEquals(6, EvidenceFactExtractor.knownCategories().size());
    }
}
