package io.yukti.api.dto.v1;

import java.util.Map;

/**
 * API v1 optimize request.
 */
public record OptimizeRequest(
    String catalogVersion,
    String period,
    Map<String, Double> spendByCategoryUsd,
    GoalDto goal,
    ConstraintsDto constraints,
    String goalPrompt,
    String optimizerId
) {
    public record GoalDto(
        String goalType,
        String primaryCurrency,
        java.util.List<String> preferredCurrencies,
        Map<String, Double> cppOverrides
    ) {}

    public record ConstraintsDto(
        int maxCards,
        double maxAnnualFeeUsd,
        boolean allowBusinessCards
    ) {}
}
