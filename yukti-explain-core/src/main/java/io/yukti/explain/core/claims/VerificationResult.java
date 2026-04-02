package io.yukti.explain.core.claims;

import java.util.List;
import java.util.Objects;

/**
 * Result of claim verification: pass/fail and optional violation messages.
 */
public record VerificationResult(
    boolean passed,
    List<String> violations
) {
    public VerificationResult {
        violations = violations != null ? List.copyOf(violations) : List.of();
    }

    public static VerificationResult pass() {
        return new VerificationResult(true, List.of());
    }

    public static VerificationResult fail(List<String> violations) {
        return new VerificationResult(false, Objects.requireNonNull(violations));
    }

    /** Backward compatibility: build from VerificationReport. */
    public static VerificationResult fromReport(VerificationReport report) {
        return report.passed() ? pass() : fail(report.allViolations());
    }
}
