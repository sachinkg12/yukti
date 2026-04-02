package io.yukti.api.mapper;

import io.yukti.api.dto.OptimizeRequestDto;
import io.yukti.api.dto.OptimizeResponseDto;
import io.yukti.core.domain.*;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maps between API DTOs and domain.
 */
public final class OptimizationMapper {

    public static OptimizationRequest toRequest(OptimizeRequestDto dto) {
        Period period = dto.monthly() ? Period.MONTHLY : Period.ANNUAL;
        Map<Category, Money> amounts = new EnumMap<>(Category.class);
        for (Category cat : Category.values()) {
            Double v = dto.spend() != null ? dto.spend().get(cat.name()) : null;
            if (v != null && v > 0) amounts.put(cat, Money.usd(v));
        }
        SpendProfile profile = new SpendProfile(period, amounts);
        GoalType goal = goalFromString(dto.goal());
        UserGoal userGoal = UserGoal.of(goal);
        UserConstraints constraints = new UserConstraints(
            dto.maxCards() > 0 ? dto.maxCards() : 3,
            Money.usd(1000),
            true
        );
        return new OptimizationRequest(profile, userGoal, constraints, Map.of());
    }

    public static OptimizeResponseDto toResponse(OptimizationResult result) {
        var b = result.getBreakdown();
        var breakdown = new OptimizeResponseDto.BreakdownDto(
            b.getEarnValue().getAmount().doubleValue(),
            b.getCreditsValue().getAmount().doubleValue(),
            b.getFees().getAmount().doubleValue(),
            b.getNet().getAmount().doubleValue()
        );
        Map<String, String> allocation = result.getAllocation().entrySet().stream()
            .collect(Collectors.toMap(e -> e.getKey().name(), Map.Entry::getValue));
        List<OptimizeResponseDto.EvidenceBlockDto> blocks = result.getEvidenceBlocks().stream()
            .map(eb -> new OptimizeResponseDto.EvidenceBlockDto(eb.getType(), eb.getCardId(), eb.getCategory(), eb.getContent()))
            .toList();
        return new OptimizeResponseDto(
            result.getPortfolioIds(),
            allocation,
            breakdown,
            result.getSwitchingNotes(),
            new OptimizeResponseDto.ExplanationDto(result.getNarrative(), blocks)
        );
    }

    private static GoalType goalFromString(String s) {
        if (s == null) return GoalType.CASHBACK;
        return switch (s.toUpperCase()) {
            case "FLEX_POINTS" -> GoalType.FLEX_POINTS;
            case "PROGRAM_POINTS" -> GoalType.PROGRAM_POINTS;
            default -> GoalType.CASHBACK;
        };
    }
}
