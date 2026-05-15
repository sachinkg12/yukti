package io.yukti.evaluation.verifier;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Eval side wrapper around the production {@code ClaimVerifier} output.
 *
 * <p>Carries the strict pass/fail flag, the total number of failing claims, and a
 * per-gate breakdown. Per-gate counts let downstream analysis report which of
 * the four gates rejected the most claims.
 *
 * <p>This is intentionally separate from {@code io.yukti.evaluation.hallucination.HallucinationReport}.
 * The {@code HallucinationReport} uses the taxonomy classifier (heuristic, eval-only).
 * The {@code VerifierReport} comes from the deployable production verifier. Both are
 * reported: production pass rate is the primary deployment metric; the taxonomy
 * report is the per-category analysis.
 */
public record VerifierReport(
    boolean passed,
    int totalClaims,
    int failingClaims,
    Map<VerifierGate, Integer> failuresByGate,
    List<String> allErrors
) {
    public VerifierReport {
        Objects.requireNonNull(failuresByGate);
        Objects.requireNonNull(allErrors);
        if (totalClaims < 0) throw new IllegalArgumentException("totalClaims must be >= 0");
        if (failingClaims < 0) throw new IllegalArgumentException("failingClaims must be >= 0");
        failuresByGate = Map.copyOf(failuresByGate);
        allErrors = List.copyOf(allErrors);
    }

    public static VerifierReport allPass(int totalClaims) {
        if (totalClaims < 0) totalClaims = 0;
        return new VerifierReport(true, totalClaims, 0, Map.of(), List.of());
    }

    /** Rate of failing claims (failingClaims / totalClaims), or 0 if no claims. */
    public double failureRate() {
        return totalClaims == 0 ? 0.0 : (double) failingClaims / totalClaims;
    }

    /**
     * Claims that the deployable permissive-emission policy would ship to the user
     * (verifier-accepted claims = totalClaims - failingClaims). 0 for schema-failed
     * instances since they have no parseable claims. Co-primary deployment metric.
     */
    public int shippedClaims() {
        return Math.max(0, totalClaims - failingClaims);
    }
}
