package io.yukti.explain.core.claims;

import io.yukti.explain.core.evidence.graph.EvidenceGraph;
import io.yukti.explain.core.evidence.graph.EvidenceNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Claim verification v1: hard rejection rules. Every citedEvidenceId must exist in graph,
 * citedEntities ⊆ allowedEntities, citedNumbers ⊆ allowedNumbers ∪ allowlist (0,1,2,3),
 * and ClaimTypeRules enforced. Strict mode: if any claim fails, report fails.
 */
public final class ClaimVerifier {

    /** Numbers always allowed in addition to graph.allowedNumbers. */
    private static final Set<String> NUMBER_ALLOWLIST = Set.of("0", "1", "2", "3");

    /**
     * Verify claims against the evidence graph. Returns report with per-claim errors and overall pass/fail.
     * Strict: if any claim has errors, report.passed() is false.
     */
    public VerificationReport verify(EvidenceGraph graph, List<Claim> claims) {
        return verifyInternal(graph, claims, new ClaimAdapter<>() {
            @Override public String claimId(Claim c) { return c.claimId(); }
            @Override public ClaimType claimType(Claim c) { return c.claimType(); }
            @Override public List<String> citedEvidenceIds(Claim c) { return c.citedEvidenceIds(); }
            @Override public List<String> citedEntities(Claim c) { return c.citedEntities(); }
            @Override public List<String> citedNumbers(Claim c) { return c.citedNumbers(); }
        });
    }

    /**
     * Verify NormalizedClaims against the evidence graph.
     */
    public VerificationReport verifyNormalized(EvidenceGraph graph, List<NormalizedClaim> claims) {
        return verifyInternal(graph, claims, new ClaimAdapter<>() {
            @Override public String claimId(NormalizedClaim c) { return c.claimId(); }
            @Override public ClaimType claimType(NormalizedClaim c) { return c.claimType(); }
            @Override public List<String> citedEvidenceIds(NormalizedClaim c) { return c.citedEvidenceIds(); }
            @Override public List<String> citedEntities(NormalizedClaim c) { return c.citedEntities(); }
            @Override public List<String> citedNumbers(NormalizedClaim c) { return c.citedNumbers(); }
        });
    }

    private <C> VerificationReport verifyInternal(EvidenceGraph graph, List<C> claims, ClaimAdapter<C> adapter) {
        if (claims == null || claims.isEmpty()) {
            return VerificationReport.pass();
        }
        Set<String> validEvidenceIds = graph.getNodes().stream()
            .map(EvidenceNode::evidenceId)
            .collect(Collectors.toSet());
        Map<String, String> evidenceIdToType = graph.getNodes().stream()
            .collect(Collectors.toMap(EvidenceNode::evidenceId, EvidenceNode::type, (a, b) -> a));
        Set<String> typesPresentInGraph = evidenceIdToType.values().stream().collect(Collectors.toSet());
        Set<String> allowedEntities = graph.getAllowedEntities();
        Set<String> allowedNumbers = new HashSet<>(graph.getAllowedNumbers());
        allowedNumbers.addAll(NUMBER_ALLOWLIST);

        List<ClaimVerificationFailure> claimErrors = new ArrayList<>();
        for (C c : claims) {
            String claimId = adapter.claimId(c);
            ClaimType claimType = adapter.claimType(c);
            List<String> citedEvidenceIds = adapter.citedEvidenceIds(c);
            List<String> citedEntities = adapter.citedEntities(c);
            List<String> citedNumbers = adapter.citedNumbers(c);

            List<String> errors = new ArrayList<>();
            for (String eid : citedEvidenceIds) {
                if (!validEvidenceIds.contains(eid)) {
                    errors.add("citedEvidenceId not in graph: " + eid);
                }
            }
            for (String entity : citedEntities) {
                if (entity != null && !entity.isEmpty() && !allowedEntities.contains(entity)) {
                    errors.add("citedEntity not allowed: " + entity);
                }
            }
            for (String num : citedNumbers) {
                if (num != null && !num.isEmpty() && !allowedNumbers.contains(num)) {
                    errors.add("citedNumber not allowed: " + num);
                }
            }
            // ClaimTypeRules: at least one of required types
            Set<String> requiredTypes = ClaimTypeRules.requiredEvidenceTypes(claimType);
            if (!requiredTypes.isEmpty() && !citesEvidenceOfTypes(citedEvidenceIds, evidenceIdToType, requiredTypes)) {
                errors.add(claimType + " must cite " + requiredTypes + " evidence");
            }
            // ClaimTypeRules: must cite all of (e.g. CAP_SWITCH: CAP_HIT and ALLOCATION_SEGMENT) when present in graph
            Set<String> requiredAll = ClaimTypeRules.requiredEvidenceTypesAll(claimType);
            if (!requiredAll.isEmpty()) {
                for (String reqType : requiredAll) {
                    if (typesPresentInGraph.contains(reqType) && !citesEvidenceOfTypes(citedEvidenceIds, evidenceIdToType, Set.of(reqType))) {
                        errors.add(claimType + " must cite " + reqType + " evidence");
                    }
                }
            }
            if (!errors.isEmpty()) {
                claimErrors.add(new ClaimVerificationFailure(claimId, errors));
            }
        }
        return claimErrors.isEmpty() ? VerificationReport.pass() : VerificationReport.fail(claimErrors);
    }

    private boolean citesEvidenceOfTypes(List<String> citedEvidenceIds, Map<String, String> evidenceIdToType, Set<String> types) {
        for (String eid : citedEvidenceIds) {
            String type = evidenceIdToType.get(eid);
            if (type != null && types.contains(type)) return true;
        }
        return false;
    }

    private interface ClaimAdapter<C> {
        String claimId(C c);
        ClaimType claimType(C c);
        List<String> citedEvidenceIds(C c);
        List<String> citedEntities(C c);
        List<String> citedNumbers(C c);
    }
}
