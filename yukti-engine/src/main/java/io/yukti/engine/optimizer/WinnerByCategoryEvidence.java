package io.yukti.engine.optimizer;

import io.yukti.core.domain.Money;

import java.util.Objects;

/**
 * Evidence: category winner vs runner-up and delta.
 */
public record WinnerByCategoryEvidence(
    String category,
    String winnerCard,
    String runnerUpCard,
    Money deltaUSD
) {
    public WinnerByCategoryEvidence {
        Objects.requireNonNull(category);
        Objects.requireNonNull(winnerCard);
        Objects.requireNonNull(deltaUSD);
    }
}
