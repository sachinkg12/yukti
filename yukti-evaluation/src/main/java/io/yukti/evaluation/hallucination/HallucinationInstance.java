package io.yukti.evaluation.hallucination;

import io.yukti.evaluation.taxonomy.FailureCategory;

import java.util.Objects;

/**
 * A single hallucination instance found in one claim. Useful for failure analysis and
 * for building the failure taxonomy from empirical data.
 */
public record HallucinationInstance(
    String claimId,
    FailureCategory category,
    String offendingValue,
    String claimText
) {
    public HallucinationInstance {
        Objects.requireNonNull(claimId);
        Objects.requireNonNull(category);
        Objects.requireNonNull(offendingValue);
        Objects.requireNonNull(claimText);
    }
}
