package io.yukti.api.dto;

import java.util.List;
import java.util.Map;

/**
 * API DTO for optimize response. Separate from domain.
 */
public record OptimizeResponseDto(
    List<String> portfolio,
    Map<String, String> allocation,
    BreakdownDto breakdown,
    List<String> switchingNotes,
    ExplanationDto explanation
) {
    public record BreakdownDto(double earnValueUsd, double creditsValueUsd, double feesUsd, double netUsd) {}
    public record ExplanationDto(String narrative, List<EvidenceBlockDto> evidenceBlocks) {}
    public record EvidenceBlockDto(String type, String cardId, String category, String content) {}
}
