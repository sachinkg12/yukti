package io.yukti.engine.optimizer;

import io.yukti.core.domain.Money;

import java.util.Objects;

/**
 * Evidence: fee break-even analysis for a card.
 */
public record FeeBreakEvenEvidence(
    String cardId,
    Money feeUSD,
    Money creditsAssumedUSD,
    Money netDeltaUSD
) {
    public FeeBreakEvenEvidence {
        Objects.requireNonNull(cardId);
        Objects.requireNonNull(feeUSD);
        Objects.requireNonNull(creditsAssumedUSD);
        Objects.requireNonNull(netDeltaUSD);
    }
}
