package io.yukti.core.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Result of valuing a rewards breakdown in USD for a given goal.
 * earnedValueUsd = native rewards converted to USD (cpp × penalty).
 * creditValueUsd = credits EV already in USD.
 * totalValueUsd = earnedValueUsd + creditValueUsd (fees handled by optimizer).
 */
public record ValuationResult(
    BigDecimal earnedValueUsd,
    BigDecimal creditValueUsd,
    BigDecimal totalValueUsd
) {
    public ValuationResult {
        Objects.requireNonNull(earnedValueUsd);
        Objects.requireNonNull(creditValueUsd);
        Objects.requireNonNull(totalValueUsd);
    }
}
