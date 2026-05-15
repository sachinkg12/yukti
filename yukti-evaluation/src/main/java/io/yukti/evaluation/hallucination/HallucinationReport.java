package io.yukti.evaluation.hallucination;

import io.yukti.evaluation.taxonomy.FailureCategory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Aggregate hallucination report for one explanation. Counts and instances of each
 * failure category encountered.
 *
 * <p>A claim is considered hallucinated if it cites an evidence id, entity, or number
 * that does not exist in the optimizer's emitted evidence graph for this instance.
 */
public record HallucinationReport(
    int totalClaims,
    int hallucinatedClaims,
    Map<FailureCategory, Integer> countByCategory,
    List<HallucinationInstance> instances
) {
    public HallucinationReport {
        if (totalClaims < 0) throw new IllegalArgumentException("totalClaims must be >= 0");
        if (hallucinatedClaims < 0) throw new IllegalArgumentException("hallucinatedClaims must be >= 0");
        Objects.requireNonNull(countByCategory);
        Objects.requireNonNull(instances);
        countByCategory = Map.copyOf(countByCategory);
        instances = List.copyOf(instances);
    }

    /** Hallucination rate as a fraction. Returns 0.0 if there are no claims. */
    public double rate() {
        if (totalClaims == 0) return 0.0;
        return (double) hallucinatedClaims / (double) totalClaims;
    }
}
