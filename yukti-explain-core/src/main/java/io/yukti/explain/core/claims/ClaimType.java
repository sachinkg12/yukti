package io.yukti.explain.core.claims;

/**
 * Claim types for the explanation claim schema v1. Type-specific verification rules apply.
 */
public enum ClaimType {
    /** Compares winner vs runner-up (must cite WINNER_BY_CATEGORY evidence). */
    COMPARISON,
    /** States a threshold (e.g. net value, portfolio size). */
    THRESHOLD,
    /** Describes category-to-card allocation. */
    ALLOCATION,
    /** Valuation or goal assumption (must cite ASSUMPTION evidence). */
    ASSUMPTION,
    /** Justifies a fee (must cite FEE_BREAK_EVEN evidence). */
    FEE_JUSTIFICATION,
    /** Cap hit and segment boundary (must cite CAP_HIT evidence). */
    CAP_SWITCH
}
