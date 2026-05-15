package io.yukti.evaluation.verifier;

import io.yukti.explain.core.claims.ClaimVerificationFailure;
import io.yukti.explain.core.claims.VerificationReport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerifierReportFactoryTest {

    @Test
    void allPassFromPassingReport() {
        VerifierReport got = VerifierReportFactory.from(VerificationReport.pass(), 5);
        assertTrue(got.passed());
        assertEquals(5, got.totalClaims());
        assertEquals(0, got.failingClaims());
        assertTrue(got.failuresByGate().isEmpty());
    }

    @Test
    void mapsAllFourGateErrors() {
        VerificationReport prod = VerificationReport.fail(List.of(
            new ClaimVerificationFailure("c1", List.of("citedEvidenceId not in graph: xyz")),
            new ClaimVerificationFailure("c2", List.of("citedEntity not allowed: GHOST_CARD")),
            new ClaimVerificationFailure("c3", List.of("citedNumber not allowed: 9999")),
            new ClaimVerificationFailure("c4", List.of("COMPARISON must cite [WINNER_BY_CATEGORY] evidence"))
        ));
        VerifierReport got = VerifierReportFactory.from(prod, 4);
        assertEquals(false, got.passed());
        assertEquals(4, got.totalClaims());
        assertEquals(4, got.failingClaims());
        assertEquals(1, got.failuresByGate().get(VerifierGate.EVIDENCE_EXISTENCE));
        assertEquals(1, got.failuresByGate().get(VerifierGate.ENTITY_ALLOWLIST));
        assertEquals(1, got.failuresByGate().get(VerifierGate.NUMBER_BINDING));
        assertEquals(1, got.failuresByGate().get(VerifierGate.TYPE_RULES));
    }

    @Test
    void multipleErrorsOnOneClaimCountAsOneFailingClaim() {
        VerificationReport prod = VerificationReport.fail(List.of(
            new ClaimVerificationFailure("c1", List.of(
                "citedEvidenceId not in graph: xyz",
                "citedEntity not allowed: GHOST_CARD"))
        ));
        VerifierReport got = VerifierReportFactory.from(prod, 1);
        assertEquals(1, got.failingClaims(), "one claim with two errors is one failing claim");
        assertEquals(1, got.failuresByGate().get(VerifierGate.EVIDENCE_EXISTENCE));
        assertEquals(1, got.failuresByGate().get(VerifierGate.ENTITY_ALLOWLIST));
    }

    @Test
    void multipleErrorsOfSameGateOnOneClaimCountOnce() {
        // Per-claim counting: a claim that fires the same gate twice should only
        // contribute 1 to that gate's count.
        VerificationReport prod = VerificationReport.fail(List.of(
            new ClaimVerificationFailure("c1", List.of(
                "citedEvidenceId not in graph: a",
                "citedEvidenceId not in graph: b"))
        ));
        VerifierReport got = VerifierReportFactory.from(prod, 1);
        assertEquals(1, got.failingClaims());
        assertEquals(1, got.failuresByGate().get(VerifierGate.EVIDENCE_EXISTENCE),
            "two evidence-existence errors on one claim = one claim count for that gate");
    }

    @Test
    void nullReportYieldsAllPass() {
        VerifierReport got = VerifierReportFactory.from(null, 3);
        assertTrue(got.passed());
        assertEquals(3, got.totalClaims());
    }
}
