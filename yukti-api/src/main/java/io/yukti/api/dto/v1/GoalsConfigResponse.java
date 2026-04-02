package io.yukti.api.dto.v1;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * API v1 config goals response.
 */
public record GoalsConfigResponse(
    List<SupportedGoalDto> supportedGoals,
    Map<String, BigDecimal> defaultCppByCurrency
) {
    public record SupportedGoalDto(
        String goalType,
        boolean requiresPrimaryCurrency,
        List<String> allowedCurrencies
    ) {}
}
