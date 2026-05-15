package io.yukti.evaluation.taxonomy;

import io.yukti.core.domain.Category;
import io.yukti.core.domain.GoalType;
import io.yukti.core.explainability.StructuredExplanation;
import io.yukti.core.explainability.evidence.EvidenceBlock;
import io.yukti.core.explainability.evidence.LegacyEvidenceBlock;
import io.yukti.explain.core.claims.Claim;
import io.yukti.explain.core.claims.ClaimType;
import io.yukti.explain.core.evidence.graph.EvidenceIdHelper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleBasedFailureClassifierTest {

    private final RuleBasedFailureClassifier classifier = new RuleBasedFailureClassifier();

    private StructuredExplanation realisticEvidence() {
        // Mirrors what a real MILP run for a moderate profile looks like:
        // portfolio of amex-bcp + usbank-cash-plus, a grocery cap hit at $6000,
        // and an annual fee of $95 with a delta over runner up.
        return new StructuredExplanation(
            "v1.0.0",
            GoalType.CASHBACK,
            "USD_CASH",
            Map.of(Category.GROCERIES, "amex-bcp"),
            List.of("amex-bcp", "usbank-cash-plus"),
            new StructuredExplanation.Breakdown(
                new BigDecimal("855.00"),
                new BigDecimal("0.00"),
                new BigDecimal("95.00"),
                new BigDecimal("760.00")
            ),
            List.of(
                new LegacyEvidenceBlock("CAP_HIT", "amex-bcp", "GROCERIES",
                    "amex-bcp: cap $6000.00 on GROCERIES, applied $6000.00, remaining $2000.00"),
                new LegacyEvidenceBlock("WINNER_BY_CATEGORY", "amex-bcp", "GROCERIES",
                    "amex-bcp wins GROCERIES over usbank-cash-plus: delta $45.60"),
                new LegacyEvidenceBlock("FEE_BREAK_EVEN", "amex-bcp", "",
                    "amex-bcp fee $95.00 breaks even at $1583 spend")
            ),
            "digest",
            List.of("ev-cap", "ev-winner", "ev-fee")
        );
    }

    private Claim claim(String text, List<String> evidenceIds, List<String> entities, List<String> numbers) {
        return new Claim("c1", ClaimType.COMPARISON, text, evidenceIds, entities, numbers);
    }

    @Test
    void capValueFromEvidenceIsNoLongerFlagged() {
        // This is the false positive that we saw in the May 13 eval run.
        var c = claim("amex-bcp has a cap of $6000.00 on GROCERIES",
            List.of("ev-cap"), List.of("amex-bcp", "GROCERIES"), List.of("$6000.00"));
        var cats = classifier.classify(c, realisticEvidence());
        assertFalse(cats.contains(FailureCategory.VALUE_DISTORTION),
            "cap $6000 from evidence should not be flagged");
    }

    @Test
    void earnRateDifferenceFromEvidenceIsNoLongerFlagged() {
        var c = claim("delta is $45.60",
            List.of("ev-winner"), List.of("amex-bcp", "usbank-cash-plus"), List.of("45.60"));
        var cats = classifier.classify(c, realisticEvidence());
        assertEquals(0, cats.size(), "winner delta from evidence should pass");
    }

    @Test
    void feeFromEvidenceIsNoLongerFlagged() {
        var c = claim("annual fee is $95.00",
            List.of("ev-fee"), List.of("amex-bcp"), List.of("95.00"));
        var cats = classifier.classify(c, realisticEvidence());
        assertEquals(0, cats.size(), "annual fee from evidence should pass");
    }

    @Test
    void breakdownNetIsAllowed() {
        var c = claim("net is $760.00",
            List.of("ev-fee"), List.of("amex-bcp"), List.of("760.00"));
        var cats = classifier.classify(c, realisticEvidence());
        assertEquals(0, cats.size());
    }

    @Test
    void numberFormattingVariantsAreAllAllowed() {
        // All four formats refer to the same allowed value
        for (String formatted : List.of("$6000.00", "6000", "$6,000.00", "6000.0")) {
            var c = claim("cap is " + formatted,
                List.of("ev-cap"), List.of("amex-bcp"), List.of(formatted));
            var cats = classifier.classify(c, realisticEvidence());
            assertFalse(cats.contains(FailureCategory.VALUE_DISTORTION),
                "format " + formatted + " should match canonical 6000.00");
        }
    }

    @Test
    void fabricatedDollarAmountStillFlagged() {
        var c = claim("the advantage is $999.99",
            List.of("ev-winner"), List.of("amex-bcp"), List.of("999.99"));
        var cats = classifier.classify(c, realisticEvidence());
        assertTrue(cats.contains(FailureCategory.VALUE_DISTORTION),
            "$999.99 is not in any evidence and should be flagged");
    }

    @Test
    void fabricatedCardStillFlagged() {
        var c = claim("the chase-sapphire-magic card wins",
            List.of("ev-winner"), List.of("chase-sapphire-magic"), List.of());
        var cats = classifier.classify(c, realisticEvidence());
        assertTrue(cats.contains(FailureCategory.ENTITY_FABRICATION));
    }

    @Test
    void fabricatedEvidenceIdStillFlagged() {
        var c = claim("amex-bcp wins",
            List.of("ev-fake"), List.of("amex-bcp"), List.of());
        var cats = classifier.classify(c, realisticEvidence());
        assertTrue(cats.contains(FailureCategory.EVIDENCE_FABRICATION));
    }

    @Test
    void scopeViolationsStillFlagged() {
        var c = claim("the sign up bonus is great",
            List.of("ev-fee"), List.of("amex-bcp"), List.of());
        var cats = classifier.classify(c, realisticEvidence());
        assertTrue(cats.contains(FailureCategory.SCOPE_VIOLATION));
    }

    @Test
    void crossCurrencyMixCaught() {
        var c = claim("uses CHASE_POINTS",
            List.of("ev-fee"), List.of("CHASE_POINTS"), List.of());
        var cats = classifier.classify(c, realisticEvidence());
        assertTrue(cats.contains(FailureCategory.CROSS_CURRENCY_CONFUSION));
    }

    @Test
    void smallIntegerExceptionAllowed() {
        var c = claim("3 cards selected",
            List.of("ev-fee"), List.of("amex-bcp"), List.of("3"));
        var cats = classifier.classify(c, realisticEvidence());
        assertFalse(cats.contains(FailureCategory.VALUE_DISTORTION));
    }

    @Test
    void zeroIsAllowedAsSmallInteger() {
        var c = claim("zero losses",
            List.of("ev-fee"), List.of("amex-bcp"), List.of("0"));
        var cats = classifier.classify(c, realisticEvidence());
        assertFalse(cats.contains(FailureCategory.VALUE_DISTORTION));
    }

    @Test
    void emptyCitedListsPass() {
        var c = claim("good portfolio",
            List.of(), List.of(), List.of());
        var cats = classifier.classify(c, realisticEvidence());
        assertEquals(0, cats.size());
    }

    @Test
    void nullClaimReturnsEmpty() {
        var cats = classifier.classify(null, realisticEvidence());
        assertEquals(0, cats.size());
    }

    @Test
    void multipleHallucinationsInOneClaim() {
        // Claim cites a fake card AND a fake number
        var c = claim("ghost-card wins by $999.99",
            List.of("ev-winner"), List.of("ghost-card"), List.of("999.99"));
        var cats = classifier.classify(c, realisticEvidence());
        assertTrue(cats.contains(FailureCategory.ENTITY_FABRICATION));
        assertTrue(cats.contains(FailureCategory.VALUE_DISTORTION));
    }

    @Test
    void interestChargeMentionFlagsScopeButNotApr() {
        // "interest charge" triggers scope; bare "apr" alone does not
        var c = claim("watch interest charges",
            List.of("ev-fee"), List.of("amex-bcp"), List.of());
        var cats = classifier.classify(c, realisticEvidence());
        assertTrue(cats.contains(FailureCategory.SCOPE_VIOLATION));
    }

    /**
     * Helper: build a StructuredExplanation that uses real SHA-256 evidence ids so
     * the TYPE_MISMATCH check can resolve cited ids to evidence types.
     */
    private StructuredExplanation evidenceWithRealIds() {
        String winnerId = EvidenceIdHelper.compute("WINNER_BY_CATEGORY", "amex-bcp", "GROCERIES", "");
        String capId = EvidenceIdHelper.compute("CAP_HIT", "amex-bcp", "GROCERIES", "");
        String feeId = EvidenceIdHelper.compute("FEE_BREAK_EVEN", "amex-bcp", "", "");
        return new StructuredExplanation(
            "v1.0.0",
            GoalType.CASHBACK,
            "USD_CASH",
            Map.of(Category.GROCERIES, "amex-bcp"),
            List.of("amex-bcp"),
            new StructuredExplanation.Breakdown(
                new BigDecimal("855.00"),
                new BigDecimal("0.00"),
                new BigDecimal("95.00"),
                new BigDecimal("760.00")
            ),
            List.of(
                new LegacyEvidenceBlock("WINNER_BY_CATEGORY", "amex-bcp", "GROCERIES", ""),
                new LegacyEvidenceBlock("CAP_HIT", "amex-bcp", "GROCERIES", ""),
                new LegacyEvidenceBlock("FEE_BREAK_EVEN", "amex-bcp", "", "")
            ),
            "digest",
            List.of(winnerId, capId, feeId)
        );
    }

    @Test
    void comparisonCitingWinnerEvidencePassesGate4() {
        String winnerId = EvidenceIdHelper.compute("WINNER_BY_CATEGORY", "amex-bcp", "GROCERIES", "");
        var c = new Claim("c1", ClaimType.COMPARISON, "amex-bcp wins",
            List.of(winnerId), List.of("amex-bcp"), List.of());
        var cats = classifier.classify(c, evidenceWithRealIds());
        assertFalse(cats.contains(FailureCategory.TYPE_MISMATCH));
    }

    @Test
    void comparisonCitingOnlyFeeEvidenceFlagsTypeMismatch() {
        String feeId = EvidenceIdHelper.compute("FEE_BREAK_EVEN", "amex-bcp", "", "");
        var c = new Claim("c1", ClaimType.COMPARISON, "amex-bcp wins",
            List.of(feeId), List.of("amex-bcp"), List.of());
        var cats = classifier.classify(c, evidenceWithRealIds());
        assertTrue(cats.contains(FailureCategory.TYPE_MISMATCH),
            "COMPARISON requires WINNER_BY_CATEGORY; only FEE_BREAK_EVEN cited");
    }

    @Test
    void feeJustificationCitingFeeBreakEvenPasses() {
        String feeId = EvidenceIdHelper.compute("FEE_BREAK_EVEN", "amex-bcp", "", "");
        var c = new Claim("c1", ClaimType.FEE_JUSTIFICATION, "fee earns back",
            List.of(feeId), List.of("amex-bcp"), List.of());
        var cats = classifier.classify(c, evidenceWithRealIds());
        assertFalse(cats.contains(FailureCategory.TYPE_MISMATCH));
    }

    @Test
    void typeMismatchNotFiredWhenCitedIdsAreUnresolvable() {
        // All cited ids are fabricated. Gate 1 (EVIDENCE_FABRICATION) catches it.
        // Gate 4 should not double-fire on the same claim.
        var c = new Claim("c1", ClaimType.COMPARISON, "amex-bcp wins",
            List.of("bogus-id"), List.of("amex-bcp"), List.of());
        var cats = classifier.classify(c, evidenceWithRealIds());
        assertTrue(cats.contains(FailureCategory.EVIDENCE_FABRICATION));
        assertFalse(cats.contains(FailureCategory.TYPE_MISMATCH));
    }

    /** Fixture with CAP_HIT and ALLOCATION_SEGMENT in the graph, for the "all of" rule. */
    private StructuredExplanation evidenceWithCapSwitchTypes() {
        String capId = EvidenceIdHelper.compute("CAP_HIT", "amex-bcp", "GROCERIES", "");
        String allocId = EvidenceIdHelper.compute("ALLOCATION_SEGMENT", "amex-bcp", "GROCERIES", "");
        return new StructuredExplanation(
            "v1.0.0",
            GoalType.CASHBACK,
            "USD_CASH",
            Map.of(Category.GROCERIES, "amex-bcp"),
            List.of("amex-bcp"),
            new StructuredExplanation.Breakdown(
                new BigDecimal("100.00"), new BigDecimal("0.00"),
                new BigDecimal("0.00"), new BigDecimal("100.00")),
            List.of(
                new LegacyEvidenceBlock("CAP_HIT", "amex-bcp", "GROCERIES", ""),
                new LegacyEvidenceBlock("ALLOCATION_SEGMENT", "amex-bcp", "GROCERIES", "")
            ),
            "digest",
            List.of(capId, allocId)
        );
    }

    @Test
    void capSwitchCitingOnlyCapHitFlagsTypeMismatchWhenBothTypesInGraph() {
        // CAP_SWITCH's requiredEvidenceTypesAll is {CAP_HIT, ALLOCATION_SEGMENT};
        // both are present in the graph, so the claim must cite each.
        String capId = EvidenceIdHelper.compute("CAP_HIT", "amex-bcp", "GROCERIES", "");
        var c = new Claim("c1", ClaimType.CAP_SWITCH, "switch at cap",
            List.of(capId), List.of("amex-bcp"), List.of());
        var cats = classifier.classify(c, evidenceWithCapSwitchTypes());
        assertTrue(cats.contains(FailureCategory.TYPE_MISMATCH),
            "CAP_SWITCH must cite BOTH CAP_HIT and ALLOCATION_SEGMENT (gate 4b)");
    }

    @Test
    void capSwitchCitingBothRequiredTypesPasses() {
        String capId = EvidenceIdHelper.compute("CAP_HIT", "amex-bcp", "GROCERIES", "");
        String allocId = EvidenceIdHelper.compute("ALLOCATION_SEGMENT", "amex-bcp", "GROCERIES", "");
        var c = new Claim("c1", ClaimType.CAP_SWITCH, "switch at cap",
            List.of(capId, allocId), List.of("amex-bcp"), List.of());
        var cats = classifier.classify(c, evidenceWithCapSwitchTypes());
        assertFalse(cats.contains(FailureCategory.TYPE_MISMATCH));
    }

    @Test
    void thresholdHasNoTypeRule() {
        // THRESHOLD has no required evidence type rule; never flag TYPE_MISMATCH.
        String feeId = EvidenceIdHelper.compute("FEE_BREAK_EVEN", "amex-bcp", "", "");
        var c = new Claim("c1", ClaimType.THRESHOLD, "cap is at $6000",
            List.of(feeId), List.of("amex-bcp"), List.of());
        var cats = classifier.classify(c, evidenceWithRealIds());
        assertFalse(cats.contains(FailureCategory.TYPE_MISMATCH));
    }
}
