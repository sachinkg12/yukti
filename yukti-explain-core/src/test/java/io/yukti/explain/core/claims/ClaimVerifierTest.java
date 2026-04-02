package io.yukti.explain.core.claims;

import io.yukti.explain.core.evidence.graph.EvidenceGraph;
import io.yukti.explain.core.evidence.graph.EvidenceNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ClaimVerifierTest {

    @Test
    void unknownEvidenceIdRejected() {
        EvidenceGraph graph = graphWithNode("e1", "WINNER_BY_CATEGORY");
        Claim claim = new Claim("c1", ClaimType.COMPARISON, "Card A wins", List.of("e1", "e-nonexistent"), List.of("card-a"), List.of());
        VerificationReport r = new ClaimVerifier().verify(graph, List.of(claim));
        assertFalse(r.passed());
        assertTrue(r.allViolations().stream().anyMatch(v -> v.contains("not in graph") && v.contains("e-nonexistent")));
        assertFalse(r.claimErrors().isEmpty());
    }

    @Test
    void unknownEntityRejected() {
        EvidenceGraph graph = graphWithNode("e1", "WINNER_BY_CATEGORY");
        graph = graphWithEntities(graph, Set.of("amex-bcp", "citi-double-cash"));
        Claim claim = new Claim("c1", ClaimType.COMPARISON, "Card X wins", List.of("e1"), List.of("unknown-card"), List.of());
        VerificationReport r = new ClaimVerifier().verify(graph, List.of(claim));
        assertFalse(r.passed());
        assertTrue(r.allViolations().stream().anyMatch(v -> v.contains("not allowed") && v.contains("unknown-card")));
    }

    @Test
    void extraNumberRejected() {
        EvidenceGraph graph = graphWithNode("e1", "FEE_BREAK_EVEN");
        graph = graphWithNumbers(graph, Set.of("95.00", "155.00"));
        Claim claim = new Claim("c1", ClaimType.FEE_JUSTIFICATION, "Fee $95", List.of("e1"), List.of(), List.of("95.00", "9999"));
        VerificationReport r = new ClaimVerifier().verify(graph, List.of(claim));
        assertFalse(r.passed());
        assertTrue(r.allViolations().stream().anyMatch(v -> v.contains("not allowed") && v.contains("9999")));
    }

    @Test
    void allowlistNumbersZeroOneTwoThreeAccepted() {
        EvidenceGraph graph = graphWithNode("e1", "FEE_BREAK_EVEN");
        graph = graphWithNumbers(graph, Set.of("95")); // no 0,1,2,3 in graph
        Claim claim = new Claim("c1", ClaimType.FEE_JUSTIFICATION, "Fee", List.of("e1"), List.of(), List.of("95", "0", "1", "2", "3"));
        VerificationReport r = new ClaimVerifier().verify(graph, List.of(claim));
        assertTrue(r.passed());
    }

    @Test
    void missingRequiredEvidenceTypeRejected() {
        EvidenceGraph graph = graphWithNode("e1", "CAP_HIT"); // wrong type for COMPARISON
        Claim claim = new Claim("c1", ClaimType.COMPARISON, "A wins", List.of("e1"), List.of(), List.of());
        VerificationReport r = new ClaimVerifier().verify(graph, List.of(claim));
        assertFalse(r.passed());
        assertTrue(r.allViolations().stream().anyMatch(v -> v.contains("COMPARISON") && v.contains("WINNER_BY_CATEGORY")));
    }

    @Test
    void capSwitchMustCiteCapHitAndAllocationSegment() {
        EvidenceGraph graph = graphWithNodes(List.of(
            new EvidenceNode("e1", "WINNER_BY_CATEGORY", "card1", "GROCERIES", "c"),
            new EvidenceNode("e2", "CAP_HIT", "card1", "GROCERIES", "c"),
            new EvidenceNode("e3", ClaimTypeRules.ALLOCATION_SEGMENT, "card1", "GROCERIES", "c")
        ));
        graph = graphWithEntities(graph, Set.of("card1"));
        graph = graphWithNumbers(graph, Set.of());
        // Cites only CAP_HIT -> missing ALLOCATION_SEGMENT
        Claim claimOnlyCap = new Claim("c1", ClaimType.CAP_SWITCH, "Cap", List.of("e2"), List.of("card1"), List.of());
        VerificationReport r1 = new ClaimVerifier().verify(graph, List.of(claimOnlyCap));
        assertFalse(r1.passed());
        assertTrue(r1.allViolations().stream().anyMatch(v -> v.contains("ALLOCATION_SEGMENT")));
        // Cites both CAP_HIT and ALLOCATION_SEGMENT -> pass
        Claim claimBoth = new Claim("c2", ClaimType.CAP_SWITCH, "Cap", List.of("e2", "e3"), List.of("card1"), List.of());
        VerificationReport r2 = new ClaimVerifier().verify(graph, List.of(claimBoth));
        assertTrue(r2.passed());
    }

    @Test
    void verify_rejectsUnknownCardIds() {
        EvidenceGraph graph = graphWithNode("e1", "WINNER_BY_CATEGORY");
        graph = graphWithEntities(graph, Set.of("amex-bcp", "citi-double-cash"));
        Claim claim = new Claim("c1", ClaimType.COMPARISON, "Card X wins", List.of("e1"), List.of("unknown-card"), List.of());
        VerificationReport r = new ClaimVerifier().verify(graph, List.of(claim));
        assertFalse(r.passed());
        assertTrue(r.allViolations().stream().anyMatch(v -> v.contains("not allowed") && v.contains("unknown-card")));
    }

    @Test
    void verify_feeJustificationMustCiteFeeBreakEvenEvidence() {
        EvidenceGraph graph = graphWithNode("e1", "WINNER_BY_CATEGORY");
        Claim claim = new Claim("c1", ClaimType.FEE_JUSTIFICATION, "Fee ok", List.of("e1"), List.of(), List.of());
        VerificationReport r = new ClaimVerifier().verify(graph, List.of(claim));
        assertFalse(r.passed());
        assertTrue(r.allViolations().stream().anyMatch(v -> v.contains("FEE_JUSTIFICATION") && v.contains("FEE_BREAK_EVEN")));
    }

    @Test
    void verify_passWhenAllChecksSatisfied() {
        EvidenceGraph graph = graphWithNode("e1", "WINNER_BY_CATEGORY");
        graph = graphWithEntities(graph, Set.of("amex-bcp"));
        graph = graphWithNumbers(graph, Set.of("60.00"));
        Claim claim = new Claim("c1", ClaimType.COMPARISON, "amex-bcp wins", List.of("e1"), List.of("amex-bcp"), List.of("60.00"));
        VerificationReport r = new ClaimVerifier().verify(graph, List.of(claim));
        assertTrue(r.passed());
        assertTrue(r.claimErrors().isEmpty());
    }

    /** Paper §6.2 verifier negative (mutation) test: mutated claims are rejected. */
    @Test
    void mutationTest_mutatedCitedNumberOrEntity_rejected() {
        EvidenceGraph graph = graphWithNode("e1", "WINNER_BY_CATEGORY");
        graph = graphWithEntities(graph, Set.of("amex-bcp", "GROCERIES"));
        graph = graphWithNumbers(graph, Set.of("60.00"));
        Claim valid = new Claim("c1", ClaimType.COMPARISON, "amex-bcp wins", List.of("e1"), List.of("amex-bcp", "GROCERIES"), List.of("60.00"));
        assertTrue(new ClaimVerifier().verify(graph, List.of(valid)).passed());
        // Mutate citedNumber: replace 60.00 with 99.99 (not in allowedNumbers)
        Claim mutatedNumber = new Claim("c2", ClaimType.COMPARISON, "amex-bcp wins", List.of("e1"), List.of("amex-bcp", "GROCERIES"), List.of("99.99"));
        assertFalse(new ClaimVerifier().verify(graph, List.of(mutatedNumber)).passed());
        // Mutate entity: add extraneous entity not in allowedEntities
        Claim mutatedEntity = new Claim("c3", ClaimType.COMPARISON, "amex-bcp wins", List.of("e1"), List.of("amex-bcp", "GROCERIES", "extraneous-entity"), List.of("60.00"));
        assertFalse(new ClaimVerifier().verify(graph, List.of(mutatedEntity)).passed());
    }

    /** Soundness: claim that cites an evidenceId not in the graph is rejected (two-node graph to avoid confusion). */
    @Test
    void wrongEvidenceId_rejected() {
        List<EvidenceNode> nodes = List.of(
            new EvidenceNode(EvidenceGraph.rootEvidenceId(), "RESULT", "", "", ""),
            new EvidenceNode("e1", "WINNER_BY_CATEGORY", "card-a", "GROCERIES", "c1"),
            new EvidenceNode("e2", "WINNER_BY_CATEGORY", "card-b", "DINING", "c2")
        );
        EvidenceGraph graph = new EvidenceGraph(nodes, List.of(), Set.of("card-a", "card-b"), Set.of("60.00", "30.00"), "digest");
        // Claim cites e1 with valid entity/number for e1 -> would pass. Here we cite e1 but use number from e2's context (30.00).
        // Verifier rejects only if evidenceId not in graph; so cite non-existent id to assert rejection.
        Claim claimWrongId = new Claim("c1", ClaimType.COMPARISON, "card-a wins", List.of("e1", "e-nonexistent"), List.of("card-a"), List.of("60.00"));
        VerificationReport r = new ClaimVerifier().verify(graph, List.of(claimWrongId));
        assertFalse(r.passed());
        assertTrue(r.allViolations().stream().anyMatch(v -> v.contains("e-nonexistent")));
    }

    /** Completeness: valid claim for each claim type passes when graph and allowlist satisfy type rules. */
    @Test
    void validClaim_eachType_passes() {
        ClaimVerifier verifier = new ClaimVerifier();
        // COMPARISON: must cite WINNER_BY_CATEGORY
        EvidenceGraph gComp = graphWithNode("e1", "WINNER_BY_CATEGORY");
        gComp = graphWithEntities(gComp, Set.of("card-a"));
        gComp = graphWithNumbers(gComp, Set.of("60.00"));
        assertTrue(verifier.verify(gComp, List.of(new Claim("c1", ClaimType.COMPARISON, "card-a wins", List.of("e1"), List.of("card-a"), List.of("60.00")))).passed());
        // THRESHOLD: no required evidence type
        EvidenceGraph gThr = graphWithNode("e1", "RESULT");
        assertTrue(verifier.verify(gThr, List.of(new Claim("c2", ClaimType.THRESHOLD, "Net above 0", List.of("e1"), List.of(), List.of()))).passed());
        // ALLOCATION: no required evidence type
        assertTrue(verifier.verify(gThr, List.of(new Claim("c3", ClaimType.ALLOCATION, "Allocated", List.of("e1"), List.of(), List.of()))).passed());
        // ASSUMPTION: must cite ASSUMPTION
        EvidenceGraph gAss = graphWithNode("e1", ClaimTypeRules.ASSUMPTION);
        assertTrue(verifier.verify(gAss, List.of(new Claim("c4", ClaimType.ASSUMPTION, "Goal CASHBACK", List.of("e1"), List.of(), List.of()))).passed());
        // FEE_JUSTIFICATION: must cite FEE_BREAK_EVEN
        EvidenceGraph gFee = graphWithNode("e1", "FEE_BREAK_EVEN");
        gFee = graphWithNumbers(gFee, Set.of("95.00"));
        assertTrue(verifier.verify(gFee, List.of(new Claim("c5", ClaimType.FEE_JUSTIFICATION, "Fee justified", List.of("e1"), List.of(), List.of("95.00")))).passed());
        // CAP_SWITCH: must cite CAP_HIT and ALLOCATION_SEGMENT (both present)
        EvidenceGraph gCap = graphWithNodes(List.of(
            new EvidenceNode("e2", "CAP_HIT", "card1", "GROCERIES", "c"),
            new EvidenceNode("e3", ClaimTypeRules.ALLOCATION_SEGMENT, "card1", "GROCERIES", "c")
        ));
        gCap = graphWithEntities(gCap, Set.of("card1"));
        assertTrue(verifier.verify(gCap, List.of(new Claim("c6", ClaimType.CAP_SWITCH, "Cap", List.of("e2", "e3"), List.of("card1"), List.of()))).passed());
    }

    @Test
    void strictMode_anyClaimFails_reportFails() {
        EvidenceGraph graph = graphWithNode("e1", "WINNER_BY_CATEGORY");
        graph = graphWithEntities(graph, Set.of("amex-bcp"));
        graph = graphWithNumbers(graph, Set.of("60.00"));
        Claim good = new Claim("c1", ClaimType.COMPARISON, "ok", List.of("e1"), List.of("amex-bcp"), List.of("60.00"));
        Claim bad = new Claim("c2", ClaimType.COMPARISON, "bad", List.of("e-missing"), List.of(), List.of());
        VerificationReport r = new ClaimVerifier().verify(graph, List.of(good, bad));
        assertFalse(r.passed());
        assertEquals(1, r.claimErrors().size());
        assertEquals("c2", r.claimErrors().get(0).claimId());
    }

    private static EvidenceGraph graphWithNode(String evidenceId, String type) {
        List<EvidenceNode> nodes = List.of(
            new EvidenceNode(EvidenceGraph.rootEvidenceId(), "RESULT", "", "", ""),
            new EvidenceNode(evidenceId, type, "card1", "GROCERIES", "content")
        );
        return new EvidenceGraph(nodes, List.of(), Set.of("card1"), Set.of(), "digest");
    }

    private static EvidenceGraph graphWithNodes(List<EvidenceNode> evidenceNodes) {
        List<EvidenceNode> nodes = new java.util.ArrayList<>();
        nodes.add(new EvidenceNode(EvidenceGraph.rootEvidenceId(), "RESULT", "", "", ""));
        nodes.addAll(evidenceNodes);
        return new EvidenceGraph(nodes, List.of(), Set.of(), Set.of(), "digest");
    }

    private static EvidenceGraph graphWithEntities(EvidenceGraph g, Set<String> entities) {
        return new EvidenceGraph(g.getNodes(), g.getEdges(), entities, g.getAllowedNumbers(), g.getDigest());
    }

    private static EvidenceGraph graphWithNumbers(EvidenceGraph g, Set<String> numbers) {
        return new EvidenceGraph(g.getNodes(), g.getEdges(), g.getAllowedEntities(), numbers, g.getDigest());
    }
}
