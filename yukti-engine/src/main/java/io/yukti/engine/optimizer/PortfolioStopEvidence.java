package io.yukti.engine.optimizer;

import java.util.Objects;

/**
 * Evidence: why portfolio selection stopped.
 */
public record PortfolioStopEvidence(String reason) {
    public PortfolioStopEvidence {
        Objects.requireNonNull(reason);
    }
}
