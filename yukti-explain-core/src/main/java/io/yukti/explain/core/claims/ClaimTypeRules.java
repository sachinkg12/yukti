package io.yukti.explain.core.claims;

import java.util.Collections;
import java.util.Set;

/**
 * For each claim type, defines the evidence types that must be cited by at least one
 * citedEvidenceId. Used by ClaimVerifier and any consumer that needs type-specific rules
 * without hardcoding them.
 */
public final class ClaimTypeRules {

    private ClaimTypeRules() {}

    /** Evidence type for winner-by-category comparisons. */
    public static final String WINNER_BY_CATEGORY = "WINNER_BY_CATEGORY";
    /** Evidence type for cap hit / segment boundary. */
    public static final String CAP_HIT = "CAP_HIT";
    /** Evidence type for allocation segment (category/card/spend). */
    public static final String ALLOCATION_SEGMENT = "ALLOCATION_SEGMENT";
    /** Evidence type for fee break-even justification. */
    public static final String FEE_BREAK_EVEN = "FEE_BREAK_EVEN";
    /** Evidence type for valuation/goal assumption. */
    public static final String ASSUMPTION = "ASSUMPTION";

    /**
     * Returns the set of evidence types that a claim of the given type must cite
     * (at least one of). Empty set means no type-specific requirement.
     */
    public static Set<String> requiredEvidenceTypes(ClaimType claimType) {
        return switch (claimType) {
            case COMPARISON -> Set.of(WINNER_BY_CATEGORY);
            case THRESHOLD, ALLOCATION -> Set.of();
            case ASSUMPTION -> Set.of(ASSUMPTION);
            case FEE_JUSTIFICATION -> Set.of(FEE_BREAK_EVEN);
            case CAP_SWITCH -> Set.of(CAP_HIT);
        };
    }

    /**
     * Returns the set of evidence types that a claim of the given type must cite
     * <em>all of</em> (at least one evidence of each type). Empty set means no "all of" requirement.
     * Used for CAP_SWITCH: must cite both CAP_HIT and ALLOCATION_SEGMENT.
     */
    public static Set<String> requiredEvidenceTypesAll(ClaimType claimType) {
        return switch (claimType) {
            case CAP_SWITCH -> Set.of(CAP_HIT, ALLOCATION_SEGMENT);
            default -> Set.of();
        };
    }

    /**
     * Returns an unmodifiable set of all evidence type names that appear in any rule.
     */
    public static Set<String> allEvidenceTypesInRules() {
        return Set.of(WINNER_BY_CATEGORY, CAP_HIT, ALLOCATION_SEGMENT, FEE_BREAK_EVEN, ASSUMPTION);
    }
}
