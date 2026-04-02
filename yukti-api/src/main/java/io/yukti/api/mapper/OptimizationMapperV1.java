package io.yukti.api.mapper;

import io.yukti.api.dto.v1.*;
import io.yukti.core.api.Card;
import io.yukti.core.api.Catalog;
import io.yukti.core.domain.*;
import io.yukti.core.explainability.NarrativeExplanation;
import io.yukti.engine.parser.GoalInterpretation;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Maps v1 API DTOs to/from domain. No domain leakage.
 */
public final class OptimizationMapperV1 {

    /**
     * Build request from DTO. When interpretedGoal is non-null (user provided goalPrompt),
     * use its userGoal and ignore parser for goal; otherwise use parser or explicit goal.
     */
    public static OptimizationRequest toRequest(
        OptimizeRequest dto,
        io.yukti.engine.parser.PreferenceParserV1 parser,
        io.yukti.engine.parser.GoalInterpretation interpretedGoal
    ) {
        Period period = periodFromString(dto.period());
        if (period == null) period = Period.ANNUAL;

        Map<Category, Money> amounts = new EnumMap<>(Category.class);
        Map<String, Double> spend = dto.spendByCategoryUsd() != null ? dto.spendByCategoryUsd() : Map.of();
        for (Category cat : Category.values()) {
            Double v = spend.get(cat.name());
            if (v != null && v >= 0) {
                amounts.put(cat, Money.usd(BigDecimal.valueOf(v)));
            }
        }
        SpendProfile profile = new SpendProfile(period, amounts);

        UserGoal userGoal;
        var goal = dto.goal();
        if (interpretedGoal != null) {
            userGoal = interpretedGoal.userGoal();
        } else if (goal == null) {
            userGoal = UserGoal.of(GoalType.CASHBACK);
        } else {
            if (dto.goalPrompt() != null && !dto.goalPrompt().isBlank()) {
                var parsed = parser.parse(dto.goalPrompt());
                userGoal = parser.mergePreferringParsedGoal(
                    parsed,
                    goal.preferredCurrencies(),
                    goal.cppOverrides()
                );
            } else {
                GoalType gt = goalTypeFromString(goal.goalType());
                Optional<RewardCurrencyType> primary = parsePrimary(goal.primaryCurrency());
                List<RewardCurrencyType> preferred = parseCurrencies(goal.preferredCurrencies());
                Map<RewardCurrencyType, BigDecimal> cppOverrides = parseCppOverrides(goal.cppOverrides());
                userGoal = new UserGoal(gt, primary, preferred, cppOverrides);
            }
        }

        var constraints = dto.constraints() != null ? dto.constraints() : new OptimizeRequest.ConstraintsDto(3, 5000, true);
        int maxCards = Math.max(1, Math.min(3, constraints.maxCards()));
        Money maxFee = Money.usd(BigDecimal.valueOf(Math.max(0, constraints.maxAnnualFeeUsd())));
        UserConstraints uc = new UserConstraints(maxCards, maxFee, constraints.allowBusinessCards());

        return new OptimizationRequest(profile, userGoal, uc, Map.of());
    }

    /** Convenience: toRequest with no pre-computed interpretation (existing behavior). */
    public static OptimizationRequest toRequest(
        OptimizeRequest dto,
        io.yukti.engine.parser.PreferenceParserV1 parser
    ) {
        return toRequest(dto, parser, null);
    }

    /**
     * Build response. When allocationEnriched is non-null, use it for allocation (with earn rate/value);
     * otherwise build allocation from result (category, cardId only).
     */
    public static OptimizeResponse toResponse(
        OptimizationResult result,
        NarrativeExplanation explanation,
        Catalog catalog,
        String requestId,
        String catalogVersion,
        String optimizerId,
        OptimizeResponse.GoalInterpretationDto goalInterpretation,
        List<OptimizeResponse.AllocationEntryDto> allocationEnriched
    ) {
        var b = result.getBreakdown();
        var breakdown = new OptimizeResponse.BreakdownDto(
            b.getEarnValue().getAmount(),
            b.getCreditsValue().getAmount(),
            b.getFees().getAmount(),
            b.getNet().getAmount()
        );

        List<OptimizeResponse.PortfolioCardDto> portfolio = new ArrayList<>();
        Map<String, Card> cardById = new HashMap<>();
        for (Card c : catalog.cards()) cardById.put(c.id(), c);
        for (String id : result.getPortfolioIds()) {
            Card c = cardById.get(id);
            if (c == null) continue;
            String currency = c.rules().isEmpty() ? "USD_CASH" : c.rules().get(0).currency().getType().name();
            portfolio.add(new OptimizeResponse.PortfolioCardDto(
                c.id(),
                c.displayName(),
                c.issuer(),
                c.annualFee().getAmount(),
                currency
            ));
        }

        List<OptimizeResponse.AllocationEntryDto> allocation = allocationEnriched != null
            ? allocationEnriched
            : result.getAllocation().entrySet().stream()
                .map(e -> new OptimizeResponse.AllocationEntryDto(e.getKey().name(), e.getValue()))
                .collect(Collectors.toList());

        var expl = new OptimizeResponse.ExplanationDto(
            explanation.summary(),
            explanation.allocationTable(),
            explanation.details(),
            explanation.assumptions(),
            explanation.fullText()
        );

        List<OptimizeResponse.EvidenceBlockDto> evidence = result.getEvidenceBlocks().stream()
            .map(eb -> new OptimizeResponse.EvidenceBlockDto(eb.getType(), eb.getCardId(), eb.getCategory(), eb.getContent()))
            .toList();

        return new OptimizeResponse(
            requestId, catalogVersion, optimizerId, portfolio, allocation, breakdown, expl, evidence,
            explanation.evidenceGraphDigest(), explanation.evidenceIds(),
            explanation.claimsDigest(), explanation.verificationStatus(),
            explanation.claimCount(), explanation.verifierErrorCount(),
            goalInterpretation
        );
    }

    private static Period periodFromString(String s) {
        if (s == null || s.isBlank()) return null;
        return switch (s.toUpperCase()) {
            case "MONTHLY" -> Period.MONTHLY;
            case "ANNUAL" -> Period.ANNUAL;
            default -> null;
        };
    }

    private static GoalType goalTypeFromString(String s) {
        if (s == null || s.isBlank()) return GoalType.CASHBACK;
        return switch (s.toUpperCase()) {
            case "FLEX_POINTS" -> GoalType.FLEX_POINTS;
            case "PROGRAM_POINTS" -> GoalType.PROGRAM_POINTS;
            default -> GoalType.CASHBACK;
        };
    }

    private static Optional<RewardCurrencyType> parsePrimary(String s) {
        if (s == null || s.isBlank()) return Optional.empty();
        try {
            return Optional.of(RewardCurrencyType.valueOf(s.toUpperCase().replace("-", "_")));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static List<RewardCurrencyType> parseCurrencies(List<String> raw) {
        if (raw == null) return List.of();
        List<RewardCurrencyType> out = new ArrayList<>();
        for (String s : raw) {
            try {
                out.add(RewardCurrencyType.valueOf(s.toUpperCase().replace("-", "_")));
            } catch (IllegalArgumentException ignored) {}
        }
        return out;
    }

    public static OptimizeResponse.GoalInterpretationDto toGoalInterpretationDto(GoalInterpretation g) {
        if (g == null) return null;
        return new OptimizeResponse.GoalInterpretationDto(
            g.userPrompt(),
            g.interpretedGoalType(),
            g.primaryCurrency(),
            g.rationale()
        );
    }

    private static Map<RewardCurrencyType, BigDecimal> parseCppOverrides(Map<String, Double> raw) {
        if (raw == null) return Map.of();
        Map<RewardCurrencyType, BigDecimal> out = new EnumMap<>(RewardCurrencyType.class);
        for (Map.Entry<String, Double> e : raw.entrySet()) {
            try {
                out.put(RewardCurrencyType.valueOf(e.getKey().toUpperCase().replace("-", "_")), BigDecimal.valueOf(e.getValue()));
            } catch (IllegalArgumentException ignored) {}
        }
        return out;
    }
}
