package io.yukti.core.explainability.evidence;

import io.yukti.core.domain.Period;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Evidence: earn rate used for a category (v1). No USD valuations; that belongs to ValuationModel.
 * mode: "CAP_APPLIED" | "NO_CAP" | "DEFAULT_BASE"
 */
public record EarnRateEvidence(
    String cardId,
    String category,
    BigDecimal multiplierUsedForCappedPortion,
    BigDecimal fallbackMultiplierUsed,
    BigDecimal capAmountUsdOrNull,
    Period capPeriodOrNull,
    String effectiveReturnSummary
) implements EvidenceBlock {
    public EarnRateEvidence {
        Objects.requireNonNull(cardId);
        Objects.requireNonNull(category);
        Objects.requireNonNull(multiplierUsedForCappedPortion);
        Objects.requireNonNull(fallbackMultiplierUsed);
        Objects.requireNonNull(effectiveReturnSummary);
    }

    @Override
    public String type() {
        return "EARN_RATE";
    }

    @Override
    public String content() {
        return String.format("multiplier=%s fallback=%s mode=%s",
            multiplierUsedForCappedPortion, fallbackMultiplierUsed, effectiveReturnSummary);
    }
}
