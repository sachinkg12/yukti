package io.yukti.explain.core.claims;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Result of claim verification v1: overall pass/fail and per-claim errors.
 * Strict mode: if any claim fails, report fails (passed = false).
 */
public record VerificationReport(
    boolean passed,
    List<ClaimVerificationFailure> claimErrors
) {
    public VerificationReport {
        claimErrors = claimErrors != null ? List.copyOf(claimErrors) : List.of();
    }

    public static VerificationReport pass() {
        return new VerificationReport(true, List.of());
    }

    public static VerificationReport fail(List<ClaimVerificationFailure> claimErrors) {
        return new VerificationReport(false, Objects.requireNonNull(claimErrors));
    }

    /** All violation messages flattened (for backward compatibility). */
    public List<String> allViolations() {
        return claimErrors.stream()
            .flatMap(f -> f.errors().stream())
            .toList();
    }
}
