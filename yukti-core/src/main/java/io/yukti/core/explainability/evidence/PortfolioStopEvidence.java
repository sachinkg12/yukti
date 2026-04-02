package io.yukti.core.explainability.evidence;

import java.util.Objects;

public record PortfolioStopEvidence(
    String reasonCode,
    String message
) implements EvidenceBlock {
    public PortfolioStopEvidence {
        Objects.requireNonNull(reasonCode);
        Objects.requireNonNull(message);
    }

    @Override
    public String type() { return "PORTFOLIO_STOP"; }

    @Override
    public String content() {
        return reasonCode + ": " + message;
    }
}
