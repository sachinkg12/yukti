package io.yukti.core.explainability.evidence;

/**
 * Typed evidence block. All evidence MUST implement this interface.
 * Sealed for v1 minimal set; extend by adding new permitted types in same package.
 */
public sealed interface EvidenceBlock
    permits AssumptionEvidence, WinnerByCategoryEvidence, CapHitEvidence, FeeBreakEvenEvidence,
            PortfolioStopEvidence, LegacyEvidenceBlock, EarnRateEvidence {
    String type();

    /** For API/display compatibility. */
    default String cardId() { return ""; }

    /** For API/display compatibility. */
    default String category() { return ""; }

    /** Deterministic formatted content for display. */
    String content();
}
