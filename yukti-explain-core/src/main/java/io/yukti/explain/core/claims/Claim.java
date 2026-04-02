package io.yukti.explain.core.claims;

import java.util.List;
import java.util.Objects;

/**
 * Machine-readable claim that cites evidence. All claims must be grounded (verified) before use.
 */
public record Claim(
    String claimId,
    ClaimType claimType,
    String text,
    List<String> citedEvidenceIds,
    List<String> citedEntities,
    List<String> citedNumbers
) {
    public Claim {
        Objects.requireNonNull(claimId);
        Objects.requireNonNull(claimType);
        Objects.requireNonNull(text);
        citedEvidenceIds = citedEvidenceIds != null ? List.copyOf(citedEvidenceIds) : List.of();
        citedEntities = citedEntities != null ? List.copyOf(citedEntities) : List.of();
        citedNumbers = citedNumbers != null ? List.copyOf(citedNumbers) : List.of();
    }
}
