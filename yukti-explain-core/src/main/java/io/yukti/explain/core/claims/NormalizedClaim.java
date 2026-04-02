package io.yukti.explain.core.claims;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Claim schema v1: machine-readable claim with normalizedFields for deterministic rendering.
 * claimId = sha256(canonical(claimType, normalizedFields, citedEvidenceIds)).
 */
public record NormalizedClaim(
    String claimId,
    ClaimType claimType,
    Map<String, Object> normalizedFields,
    List<String> citedEvidenceIds,
    List<String> citedEntities,
    List<String> citedNumbers,
    String renderTemplateId
) {
    public NormalizedClaim {
        Objects.requireNonNull(claimId);
        Objects.requireNonNull(claimType);
        normalizedFields = normalizedFields != null ? Map.copyOf(normalizedFields) : Map.of();
        citedEvidenceIds = citedEvidenceIds != null ? List.copyOf(citedEvidenceIds) : List.of();
        citedEntities = citedEntities != null ? List.copyOf(citedEntities) : List.of();
        citedNumbers = citedNumbers != null ? List.copyOf(citedNumbers) : List.of();
    }

    public NormalizedClaim(String claimId, ClaimType claimType, Map<String, Object> normalizedFields,
                          List<String> citedEvidenceIds, List<String> citedEntities, List<String> citedNumbers) {
        this(claimId, claimType, normalizedFields, citedEvidenceIds, citedEntities, citedNumbers, null);
    }
}
