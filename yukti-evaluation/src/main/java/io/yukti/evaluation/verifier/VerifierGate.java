package io.yukti.evaluation.verifier;

/**
 * The four production verifier gates, in canonical reporting order.
 *
 * <p>Each error string emitted by the production {@code ClaimVerifier} maps to
 * exactly one gate. The mapping lives in {@link VerifierGateClassifier}; this enum
 * is the closed value set that downstream analysis joins against.
 */
public enum VerifierGate {
    /** Gate 1: cited evidenceId must exist in the graph. */
    EVIDENCE_EXISTENCE,
    /** Gate 2: cited entities must be in the graph's allowedEntities. */
    ENTITY_ALLOWLIST,
    /** Gate 3: cited numbers must be in the graph's allowedNumbers (small ints allowed). */
    NUMBER_BINDING,
    /** Gate 4: claim type must cite evidence of the required type. */
    TYPE_RULES,
    /** Anything that does not map to one of the four gates. */
    OTHER
}
