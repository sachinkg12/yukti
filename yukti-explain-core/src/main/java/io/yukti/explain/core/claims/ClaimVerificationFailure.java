package io.yukti.explain.core.claims;

import java.util.List;
import java.util.Objects;

/**
 * Per-claim verification failures: claim id and list of error messages.
 */
public record ClaimVerificationFailure(
    String claimId,
    List<String> errors
) {
    public ClaimVerificationFailure {
        Objects.requireNonNull(claimId);
        errors = errors != null ? List.copyOf(errors) : List.of();
    }
}
