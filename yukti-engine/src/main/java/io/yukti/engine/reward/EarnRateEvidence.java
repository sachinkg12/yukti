package io.yukti.engine.reward;

import io.yukti.core.domain.Money;
import io.yukti.core.domain.Period;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Evidence: earn rate for a category. Captured during reward computation.
 * No USD valuations (that belongs to ValuationModel).
 * effectiveReturnSummary: "CAP_APPLIED" | "NO_CAP" | "DEFAULT_BASE"
 */
public record EarnRateEvidence(
    String cardId,
    String category,
    BigDecimal multiplierUsedForCappedPortion,
    BigDecimal fallbackMultiplierUsed,
    Money capAmountUsdOrNull,
    Period capPeriodOrNull,
    String effectiveReturnSummary
) {
    public EarnRateEvidence {
        Objects.requireNonNull(cardId);
        Objects.requireNonNull(category);
        Objects.requireNonNull(multiplierUsedForCappedPortion);
        Objects.requireNonNull(fallbackMultiplierUsed);
        Objects.requireNonNull(effectiveReturnSummary);
    }
}
