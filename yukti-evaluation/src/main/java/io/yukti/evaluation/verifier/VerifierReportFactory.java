package io.yukti.evaluation.verifier;

import io.yukti.explain.core.claims.ClaimVerificationFailure;
import io.yukti.explain.core.claims.VerificationReport;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adapter from production {@link VerificationReport} into eval-side {@link VerifierReport}
 * with per gate breakdown.
 *
 * <p>This is the only place that knows about both the production verifier output
 * format and the eval gate enum. Add new gate categories here.
 */
public final class VerifierReportFactory {

    private VerifierReportFactory() {}

    public static VerifierReport from(VerificationReport prodReport, int totalClaims) {
        if (prodReport == null) {
            return VerifierReport.allPass(totalClaims);
        }
        // Per-claim counting: each (claim, gate) increments byGate at most once,
        // even if the production verifier emitted multiple errors of the same
        // gate for that claim. A single claim can still appear in multiple gates
        // if it has errors mapped to different gates. This matches the
        // "how many claims failed each gate" framing used by downstream tables.
        Map<VerifierGate, Integer> byGate = new EnumMap<>(VerifierGate.class);
        List<String> allErrors = new ArrayList<>();
        int failingClaims = 0;
        for (ClaimVerificationFailure f : prodReport.claimErrors()) {
            if (f.errors().isEmpty()) continue;
            failingClaims++;
            Set<VerifierGate> gatesForClaim = EnumSet.noneOf(VerifierGate.class);
            for (String err : f.errors()) {
                allErrors.add(err);
                gatesForClaim.add(VerifierGateClassifier.classify(err));
            }
            for (VerifierGate g : gatesForClaim) {
                byGate.merge(g, 1, Integer::sum);
            }
        }
        return new VerifierReport(prodReport.passed(), totalClaims, failingClaims, byGate, allErrors);
    }
}
