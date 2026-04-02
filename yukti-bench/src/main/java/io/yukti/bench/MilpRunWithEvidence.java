package io.yukti.bench;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * DTO for one MILP run with evidence (from milp_evidence.json).
 * Load via ObjectMapper; convert to OptimizationResult via MilpEvidenceAdapter.
 */
public record MilpRunWithEvidence(
    @JsonProperty("profileId") String profileId,
    @JsonProperty("goal") String goal,
    @JsonProperty("portfolio") List<String> portfolio,
    @JsonProperty("allocation") Map<String, String> allocation,
    @JsonProperty("breakdown") BreakdownDto breakdown,
    @JsonProperty("evidenceBlocks") List<EvidenceBlockDto> evidenceBlocks
) {
    @JsonCreator
    public MilpRunWithEvidence {
        portfolio = portfolio != null ? List.copyOf(portfolio) : List.of();
        allocation = allocation != null ? Map.copyOf(allocation) : Map.of();
        evidenceBlocks = evidenceBlocks != null ? List.copyOf(evidenceBlocks) : List.of();
    }

    public record BreakdownDto(
        @JsonProperty("earnUsd") double earnUsd,
        @JsonProperty("creditsUsd") double creditsUsd,
        @JsonProperty("feesUsd") double feesUsd,
        @JsonProperty("netUsd") double netUsd
    ) {}

    public record EvidenceBlockDto(
        @JsonProperty("type") String type,
        @JsonProperty("cardId") String cardId,
        @JsonProperty("category") String category,
        @JsonProperty("content") String content
    ) {}
}
