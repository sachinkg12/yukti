package io.yukti.core.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Segment-based allocation plan per category.
 * Enables cap-aware switching: spend can be split across multiple cards in a category.
 */
public record AllocationPlan(
    Map<Category, List<AllocationSegment>> segmentsByCategory
) {
    public AllocationPlan {
        segmentsByCategory = segmentsByCategory != null
            ? Map.copyOf(Objects.requireNonNull(segmentsByCategory))
            : Map.of();
    }
}
