package io.yukti.evaluation.taxonomy;

import io.yukti.core.explainability.StructuredExplanation;
import io.yukti.core.explainability.evidence.EvidenceBlock;
import io.yukti.explain.core.claims.Claim;
import io.yukti.explain.core.claims.ClaimTypeRules;
import io.yukti.explain.core.evidence.graph.EvidenceIdHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rule based classifier that maps hallucinated claims to {@link FailureCategory} using
 * the structured explanation's allowed evidence ids, entities, and numbers.
 *
 * <p>This classifier is deterministic. The allowed sets are derived from
 * {@link EvidenceFactExtractor}, which mirrors the canonical evidence graph builder.
 */
public final class RuleBasedFailureClassifier implements FailureCategoryClassifier {

    @Override
    public List<FailureCategory> classify(Claim claim, StructuredExplanation evidence) {
        if (claim == null) return List.of();
        List<FailureCategory> out = new ArrayList<>();
        Set<String> allowedEvidenceIds = new HashSet<>(evidence.evidenceIds());
        Set<String> allowedEntities = EvidenceFactExtractor.allowedEntities(evidence);
        Set<String> allowedNumbers = EvidenceFactExtractor.allowedNumbers(evidence);
        Map<String, String> evidenceIdToType = buildEvidenceIdToType(evidence);

        for (String evId : claim.citedEvidenceIds()) {
            if (!allowedEvidenceIds.contains(evId)) {
                out.add(FailureCategory.EVIDENCE_FABRICATION);
                break;
            }
        }
        for (String entity : claim.citedEntities()) {
            if (!allowedEntities.contains(entity)) {
                if (looksLikeCurrencyConfusion(entity)) {
                    out.add(FailureCategory.CROSS_CURRENCY_CONFUSION);
                } else {
                    out.add(FailureCategory.ENTITY_FABRICATION);
                }
                break;
            }
        }
        for (String num : claim.citedNumbers()) {
            if (!EvidenceFactExtractor.isAllowedNumber(num, allowedNumbers) && !isSmallInteger(num)) {
                out.add(FailureCategory.VALUE_DISTORTION);
                break;
            }
        }
        if (violatesTypeRules(claim, evidenceIdToType)) {
            out.add(FailureCategory.TYPE_MISMATCH);
        }
        if (looksOutOfScope(claim.text())) {
            out.add(FailureCategory.SCOPE_VIOLATION);
        }
        return out;
    }

    /**
     * Map evidenceId to evidence type using the same canonical recipe as
     * {@code EvidenceGraphBuilder}: SHA-256 over (type|cardId|category). This
     * lets the eval-side classifier reconstruct types without depending on the
     * engine builder directly.
     */
    private static Map<String, String> buildEvidenceIdToType(StructuredExplanation evidence) {
        Map<String, String> map = new HashMap<>();
        for (EvidenceBlock eb : evidence.evidenceBlocks()) {
            String cardId = eb.cardId() == null ? "" : eb.cardId();
            String category = eb.category() == null ? "" : eb.category();
            String id = EvidenceIdHelper.compute(eb.type(), cardId, category, eb.content());
            map.put(id, eb.type());
        }
        return map;
    }

    /**
     * Gate 4 (TYPE_RULES) — mirrors both production rules:
     * <ul>
     *   <li>{@code requiredEvidenceTypes}: claim must cite at least one evidence of
     *       any required type.
     *   <li>{@code requiredEvidenceTypesAll}: claim must cite evidence of each
     *       required type, but only when that type is actually present in the
     *       graph (matches {@code ClaimVerifier.verifyInternal}).
     * </ul>
     *
     * <p>We only emit TYPE_MISMATCH when at least one cited id resolves to a known
     * type (otherwise the claim is already an EVIDENCE_FABRICATION case and we
     * avoid double-firing across gates 1 and 4).
     */
    private static boolean violatesTypeRules(Claim claim, Map<String, String> evidenceIdToType) {
        if (claim.claimType() == null) return false;

        // Set of types resolved from this claim's citations, and types present in
        // the whole graph (used only for the "all of" rule, per production logic).
        Set<String> citedResolvedTypes = new HashSet<>();
        for (String eid : claim.citedEvidenceIds()) {
            String type = evidenceIdToType.get(eid);
            if (type != null) citedResolvedTypes.add(type);
        }
        if (citedResolvedTypes.isEmpty()) return false;

        // Built from StructuredExplanation evidence blocks. This excludes the
        // synthetic graph root node ("RESULT"), which is never a target of any
        // ClaimTypeRules rule, so the production gate-4b semantics are
        // preserved for current rules. Adding a rule that references a graph-
        // only node type would be a divergence to revisit here.
        Set<String> typesPresentInGraph = new HashSet<>(evidenceIdToType.values());

        // Rule A: at least one of `required`.
        Set<String> required = ClaimTypeRules.requiredEvidenceTypes(claim.claimType());
        if (!required.isEmpty()) {
            boolean anyMatch = false;
            for (String t : citedResolvedTypes) {
                if (required.contains(t)) { anyMatch = true; break; }
            }
            if (!anyMatch) return true;
        }

        // Rule B: all of `requiredAll`, but only types actually present in the graph
        // (e.g. CAP_SWITCH must cite both CAP_HIT and ALLOCATION_SEGMENT when both
        // appear in the graph).
        Set<String> requiredAll = ClaimTypeRules.requiredEvidenceTypesAll(claim.claimType());
        for (String reqType : requiredAll) {
            if (typesPresentInGraph.contains(reqType) && !citedResolvedTypes.contains(reqType)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSmallInteger(String s) {
        if (s == null) return false;
        try {
            int n = Integer.parseInt(s.trim());
            return n >= 0 && n <= 3;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean looksLikeCurrencyConfusion(String entity) {
        if (entity == null) return false;
        String e = entity.toUpperCase();
        return e.contains("POINTS") || e.contains("MILES") || e.contains("REWARDS") || e.contains("CASHBACK");
    }

    private static boolean looksOutOfScope(String text) {
        if (text == null) return false;
        String t = text.toLowerCase();
        return t.contains("sign up bonus") || t.contains("sign-up bonus")
            || t.contains("apr") || t.contains("interest charge")
            || t.contains("credit score") || t.contains("approval probability")
            || t.contains("approval odds");
    }
}
