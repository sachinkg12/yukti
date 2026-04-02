package io.yukti.api.dto;

import java.util.Map;

/**
 * API DTO for optimize request. Separate from domain.
 */
public record OptimizeRequestDto(
    boolean monthly,
    Map<String, Double> spend,
    String goal,
    int maxCards
) {}
