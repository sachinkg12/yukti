package io.yukti.evaluation.fluency;

import java.util.Objects;

public record FluencyScore(String metricId, double value) {
    public FluencyScore {
        Objects.requireNonNull(metricId);
        if (Double.isNaN(value)) throw new IllegalArgumentException("value cannot be NaN");
    }
}
