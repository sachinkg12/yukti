package io.yukti.core.explainability.evidence;

import io.yukti.core.domain.Category;
import io.yukti.core.domain.Period;

import java.math.BigDecimal;
import java.util.Objects;

public record CapHitEvidence(
    String cardId,
    Category cat,
    BigDecimal capAmountUsd,
    Period capPeriod,
    BigDecimal spendAppliedToCapUsd,
    BigDecimal remainingSpendUsd,
    String fallbackCardIdOrNull
) implements EvidenceBlock {
    public CapHitEvidence {
        Objects.requireNonNull(cardId);
        Objects.requireNonNull(cat);
        Objects.requireNonNull(capAmountUsd);
        Objects.requireNonNull(capPeriod);
        Objects.requireNonNull(spendAppliedToCapUsd);
        Objects.requireNonNull(remainingSpendUsd);
    }

    @Override
    public String type() { return "CAP_HIT"; }

    @Override
    public String category() { return cat.name(); }

    @Override
    public String content() {
        return String.format("%s: cap $%.2f on %s, applied $%.2f, remaining $%.2f%s",
            cardId, capAmountUsd, cat.name(), spendAppliedToCapUsd, remainingSpendUsd,
            fallbackCardIdOrNull != null ? ", fallback " + fallbackCardIdOrNull : "");
    }
}
