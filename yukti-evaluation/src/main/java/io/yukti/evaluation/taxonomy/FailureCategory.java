package io.yukti.evaluation.taxonomy;

/**
 * Taxonomy of grounding failure categories observed in LLM generated explanations
 * over solver emitted evidence.
 *
 * <p>Categories are designed so that each one maps to one verification gate in the
 * Yukti four gate ClaimVerifier. The gate mapping is documented in each enum
 * value's Javadoc.
 *
 * <p>Open Closed: extending this taxonomy is an empirical research decision, not a
 * software extension point. Add a new enum value only if a new failure mode is
 * observed and cannot be classified under an existing category.
 */
public enum FailureCategory {

    /** Cites an evidence id that does not exist in the graph. Maps to Gate 1. */
    EVIDENCE_FABRICATION,

    /** References a card, category, or currency outside the allowed entity set. Maps to Gate 2. */
    ENTITY_FABRICATION,

    /** Uses a dollar amount, percentage, or count not present in the allowed number set. Maps to Gate 3. */
    VALUE_DISTORTION,

    /** Claim type cites evidence of the wrong type. Maps to Gate 4. */
    TYPE_MISMATCH,

    /** Right facts but invented causal story. Partially caught by Gate 4. */
    CAUSAL_MISATTRIBUTION,

    /** Claim is outside the model's stated competence (sign up bonus, churn, APR). Not caught by the four gates. */
    SCOPE_VIOLATION,

    /** Conflates two different reward currencies. Caught by Gate 2 entity allowlist. */
    CROSS_CURRENCY_CONFUSION
}
