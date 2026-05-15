package io.yukti.evaluation.hallucination;

import io.yukti.core.domain.Category;
import io.yukti.core.domain.GoalType;
import io.yukti.core.explainability.StructuredExplanation;
import io.yukti.core.explainability.evidence.LegacyEvidenceBlock;
import io.yukti.evaluation.taxonomy.FailureCategory;
import io.yukti.explain.core.claims.Claim;
import io.yukti.explain.core.claims.ClaimType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceBoundDetectorTest {

    private StructuredExplanation evidence() {
        return new StructuredExplanation(
            "v1.0.0",
            GoalType.CASHBACK,
            "USD_CASH",
            Map.of(Category.GROCERIES, "amex-bcp"),
            List.of("amex-bcp"),
            new StructuredExplanation.Breakdown(
                new BigDecimal("100.00"),
                new BigDecimal("50.00"),
                new BigDecimal("0.00"),
                new BigDecimal("150.00")
            ),
            List.of(
                new LegacyEvidenceBlock("CAP_HIT", "amex-bcp", "GROCERIES",
                    "amex-bcp: cap $6000.00, applied $4000.00, remaining $2000.00")
            ),
            "digest-hash",
            List.of("ev-1")
        );
    }

    @Test
    void cleanClaimsHaveZeroRate() {
        var detector = new EvidenceBoundDetector();
        var claims = List.of(
            new Claim("c1", ClaimType.COMPARISON, "amex-bcp wins", List.of("ev-1"), List.of("amex-bcp"), List.of())
        );
        var report = detector.detect(evidence(), claims);
        assertEquals(1, report.totalClaims());
        assertEquals(0, report.hallucinatedClaims());
        assertEquals(0.0, report.rate());
    }

    @Test
    void capValueFromEvidenceBlockIsPassThroughNotFlagged() {
        var detector = new EvidenceBoundDetector();
        var claims = List.of(
            new Claim("c1", ClaimType.COMPARISON, "cap is $6000", List.of("ev-1"),
                List.of("amex-bcp"), List.of("6000"))
        );
        var report = detector.detect(evidence(), claims);
        assertEquals(0, report.hallucinatedClaims(), "$6000 cap from evidence should not be flagged");
    }

    @Test
    void mixedClaimsTrackHallucinations() {
        var detector = new EvidenceBoundDetector();
        var claims = List.of(
            new Claim("c1", ClaimType.COMPARISON, "amex-bcp wins", List.of("ev-1"), List.of("amex-bcp"), List.of()),
            new Claim("c2", ClaimType.COMPARISON, "fake-card wins", List.of("ev-1"), List.of("fake-card"), List.of())
        );
        var report = detector.detect(evidence(), claims);
        assertEquals(2, report.totalClaims());
        assertEquals(1, report.hallucinatedClaims());
        assertEquals(0.5, report.rate());
        assertTrue(report.countByCategory().containsKey(FailureCategory.ENTITY_FABRICATION));
    }

    @Test
    void emptyClaimsListIsHandled() {
        var report = new EvidenceBoundDetector().detect(evidence(), List.of());
        assertEquals(0, report.totalClaims());
        assertEquals(0.0, report.rate());
    }

    @Test
    void instanceRecordsCarryDetail() {
        var detector = new EvidenceBoundDetector();
        var claims = List.of(
            new Claim("c-bad", ClaimType.COMPARISON, "ghost wins by $999.99",
                List.of("ev-1"), List.of("ghost"), List.of("999.99"))
        );
        var report = detector.detect(evidence(), claims);
        assertTrue(report.instances().size() >= 1);
        assertEquals("c-bad", report.instances().get(0).claimId());
    }
}
