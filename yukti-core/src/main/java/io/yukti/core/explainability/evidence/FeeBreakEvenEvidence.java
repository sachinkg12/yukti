package io.yukti.core.explainability.evidence;

import java.math.BigDecimal;
import java.util.Objects;

public record FeeBreakEvenEvidence(
    String cardId,
    BigDecimal annualFeeUsd,
    BigDecimal expectedCreditsUsd,
    BigDecimal incrementalEarnUsd,
    BigDecimal netDeltaUsd
) implements EvidenceBlock {
    public FeeBreakEvenEvidence {
        Objects.requireNonNull(cardId);
        Objects.requireNonNull(annualFeeUsd);
        Objects.requireNonNull(expectedCreditsUsd);
        Objects.requireNonNull(incrementalEarnUsd);
        Objects.requireNonNull(netDeltaUsd);
    }

    @Override
    public String type() { return "FEE_BREAK_EVEN"; }

    @Override
    public String cardId() { return cardId; }

    @Override
    public String content() {
        return String.format("%s: fee $%.2f, credits $%.2f, incremental earn $%.2f, net delta $%.2f",
            cardId, annualFeeUsd, expectedCreditsUsd, incrementalEarnUsd, netDeltaUsd);
    }
}
