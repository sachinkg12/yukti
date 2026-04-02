package io.yukti.engine.optimizer;

import io.yukti.core.domain.AllocationPlan;
import io.yukti.core.domain.Category;
import io.yukti.core.domain.EvidenceBlock;
import io.yukti.core.domain.Money;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result of per-category allocation for a fixed portfolio.
 * v1: one card per category. v1.1: segment-based, allocationByCategory = first segment winner.
 */
public record AllocationResult(
    Map<Category, String> allocationByCategory,
    Money earnedValueUsd,
    Money creditValueUsd,
    Money feesUsd,
    Money netValueUsd,
    List<EvidenceBlock> evidenceBlocks,
    AllocationPlan allocationPlan
) {
    public AllocationResult {
        Objects.requireNonNull(allocationByCategory);
        Objects.requireNonNull(earnedValueUsd);
        Objects.requireNonNull(creditValueUsd);
        Objects.requireNonNull(feesUsd);
        Objects.requireNonNull(netValueUsd);
        Objects.requireNonNull(evidenceBlocks);
        allocationPlan = allocationPlan != null ? allocationPlan : new AllocationPlan(Map.of());
    }

    /** Backward-compatible constructor for v1 solver (no segment plan). */
    public AllocationResult(
        Map<Category, String> allocationByCategory,
        Money earnedValueUsd,
        Money creditValueUsd,
        Money feesUsd,
        Money netValueUsd,
        List<EvidenceBlock> evidenceBlocks
    ) {
        this(allocationByCategory, earnedValueUsd, creditValueUsd, feesUsd, netValueUsd, evidenceBlocks, null);
    }
}
