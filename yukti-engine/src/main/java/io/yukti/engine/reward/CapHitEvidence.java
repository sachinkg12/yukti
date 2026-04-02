package io.yukti.engine.reward;

import io.yukti.core.domain.Money;
import io.yukti.core.domain.Period;

import java.util.Objects;

/**
 * Evidence: cap was hit for a category. Captured during reward computation.
 * fallbackCardIdOrNull: null at Day 3 (optimizer sets fallback card later).
 */
public record CapHitEvidence(
    String cardId,
    String category,
    Money capAmount,
    Period capPeriod,
    Money spendAppliedToCap,
    Money remainingSpend,
    String fallbackCardIdOrNull
) {
    public CapHitEvidence {
        Objects.requireNonNull(cardId);
        Objects.requireNonNull(category);
        Objects.requireNonNull(capAmount);
        Objects.requireNonNull(capPeriod);
        Objects.requireNonNull(spendAppliedToCap);
        Objects.requireNonNull(remainingSpend);
        // fallbackCardIdOrNull may be null
    }
}
