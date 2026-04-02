package io.yukti.core.explainability;

import io.yukti.explain.core.claims.Claim;

import java.util.List;
import java.util.Objects;

/**
 * Human-readable narrative derived from verified claims. Includes claims list, evidence graph digest,
 * claims digest, verification status and counts for auditable evaluation.
 */
public record NarrativeExplanation(
    List<Claim> claims,
    String summary,
    String allocationTable,
    String details,
    String assumptions,
    String fullText,
    String evidenceGraphDigest,
    List<String> evidenceIds,
    String claimsDigest,
    String verificationStatus,
    int claimCount,
    int verifierErrorCount
) {
    public NarrativeExplanation {
        claims = claims != null ? List.copyOf(claims) : List.of();
        Objects.requireNonNull(summary);
        Objects.requireNonNull(allocationTable);
        Objects.requireNonNull(details);
        Objects.requireNonNull(assumptions);
        Objects.requireNonNull(fullText);
        evidenceGraphDigest = evidenceGraphDigest != null ? evidenceGraphDigest : "";
        evidenceIds = evidenceIds != null ? List.copyOf(evidenceIds) : List.of();
        claimsDigest = claimsDigest != null ? claimsDigest : "";
        verificationStatus = verificationStatus != null ? verificationStatus : "PASS";
    }
}
