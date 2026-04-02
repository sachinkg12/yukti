package io.yukti.core.explainability;

import io.yukti.core.domain.Category;
import io.yukti.core.domain.GoalType;
import io.yukti.core.explainability.evidence.EvidenceBlock;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Machine-readable explanation derived from OptimizationResult. Includes evidence graph digest and
 * evidenceIds for verifiable narration (claims can reference evidenceIds and be checked against the graph).
 */
public record StructuredExplanation(
    String catalogVersion,
    GoalType goalType,
    String primaryCurrencyOrNull,
    Map<Category, String> allocationByCategory,
    List<String> portfolioCardIds,
    Breakdown breakdown,
    List<EvidenceBlock> evidenceBlocks,
    String evidenceGraphDigest,
    List<String> evidenceIds
) {
    public StructuredExplanation {
        Objects.requireNonNull(catalogVersion);
        Objects.requireNonNull(goalType);
        allocationByCategory = allocationByCategory != null ? Map.copyOf(allocationByCategory) : Map.of();
        portfolioCardIds = portfolioCardIds != null ? List.copyOf(portfolioCardIds) : List.of();
        Objects.requireNonNull(breakdown);
        evidenceBlocks = evidenceBlocks != null ? List.copyOf(evidenceBlocks) : List.of();
        evidenceGraphDigest = evidenceGraphDigest != null ? evidenceGraphDigest : "";
        evidenceIds = evidenceIds != null ? List.copyOf(evidenceIds) : List.of();
    }

    /**
     * Breakdown from optimizer (no recomputation).
     */
    public record Breakdown(
        java.math.BigDecimal totalEarnValueUsd,
        java.math.BigDecimal totalCreditValueUsd,
        java.math.BigDecimal totalFeesUsd,
        java.math.BigDecimal netValueUsd
    ) {
        public Breakdown {
            Objects.requireNonNull(totalEarnValueUsd);
            Objects.requireNonNull(totalCreditValueUsd);
            Objects.requireNonNull(totalFeesUsd);
            Objects.requireNonNull(netValueUsd);
        }
    }
}
