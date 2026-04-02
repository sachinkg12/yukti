package io.yukti.core.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A segment of spend allocated to a card within a category.
 * Supports cap-aware switching: use card A up to cap, then switch to card B.
 */
public record AllocationSegment(
    String cardId,
    BigDecimal spendUsd,
    String reasonCode
) {
    public AllocationSegment {
        Objects.requireNonNull(cardId);
        Objects.requireNonNull(spendUsd);
        Objects.requireNonNull(reasonCode);
    }
}
