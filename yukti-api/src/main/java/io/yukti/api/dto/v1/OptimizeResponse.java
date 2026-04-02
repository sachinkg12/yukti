package io.yukti.api.dto.v1;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * API v1 optimize response. Includes evidence graph digest, claims digest, and verification status.
 * goalInterpretation is present only when the user provided goalPrompt (AI assist); null otherwise.
 */
public record OptimizeResponse(
    String requestId,
    String catalogVersion,
    String optimizerId,
    List<PortfolioCardDto> portfolio,
    List<AllocationEntryDto> allocation,
    BreakdownDto breakdown,
    ExplanationDto explanation,
    List<EvidenceBlockDto> evidence,
    String evidenceGraphDigest,
    List<String> evidenceIds,
    String claimsDigest,
    String verificationStatus,
    int claimCount,
    int verifierErrorCount,
    GoalInterpretationDto goalInterpretation
) {
    /** Present only when user provided goalPrompt; explains what we interpreted and why. */
    public record GoalInterpretationDto(
        String userPrompt,
        String interpretedGoalType,
        String primaryCurrency,
        String rationale
    ) {}
    public record PortfolioCardDto(
        String cardId,
        String name,
        String issuer,
        BigDecimal annualFeeUsd,
        String rewardCurrency
    ) {}

    /** category, cardId, and optional display: earn rate % and earn value USD for that (category, card). */
    public record AllocationEntryDto(
        String category,
        String cardId,
        Double earnRatePercent,
        Double earnValueUsd
    ) {
        /** Backward-compatible: allocation without display fields. */
        public AllocationEntryDto(String category, String cardId) {
            this(category, cardId, null, null);
        }
    }

    public record BreakdownDto(
        BigDecimal totalEarnValueUsd,
        BigDecimal totalCreditValueUsd,
        BigDecimal totalFeesUsd,
        BigDecimal netValueUsd
    ) {}

    public record ExplanationDto(
        String summary,
        String allocationTable,
        String details,
        String assumptions,
        String fullText
    ) {}

    public record EvidenceBlockDto(String type, String cardId, String category, String content) {}
}
