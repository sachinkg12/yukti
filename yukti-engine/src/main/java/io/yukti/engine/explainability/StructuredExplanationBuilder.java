package io.yukti.engine.explainability;

import io.yukti.core.domain.*;
import io.yukti.core.explainability.StructuredExplanation;
import io.yukti.explain.core.evidence.graph.EvidenceGraph;
import io.yukti.core.explainability.evidence.EvidenceBlock;
import io.yukti.core.explainability.evidence.LegacyEvidenceBlock;

import java.math.RoundingMode;
import java.util.List;

/**
 * Builds StructuredExplanation from OptimizationResult. No recomputation. Includes evidence graph digest and evidenceIds.
 */
public final class StructuredExplanationBuilder {

    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private final EvidenceGraphBuilder graphBuilder = new EvidenceGraphBuilder();

    public StructuredExplanation build(OptimizationResult result, String catalogVersion, GoalType goalType, String primaryCurrencyOrNull) {
        var b = result.getBreakdown();
        StructuredExplanation.Breakdown breakdown = new StructuredExplanation.Breakdown(
            b.getEarnValue().getAmount().setScale(2, ROUNDING),
            b.getCreditsValue().getAmount().setScale(2, ROUNDING),
            b.getFees().getAmount().setScale(2, ROUNDING),
            b.getEarnValue().add(b.getCreditsValue()).subtract(b.getFees()).getAmount().setScale(2, ROUNDING)
        );
        List<EvidenceBlock> evidence = result.getEvidenceBlocks().stream()
            .map(eb -> (EvidenceBlock) new LegacyEvidenceBlock(eb.getType(), eb.getCardId(), eb.getCategory(), eb.getContent()))
            .toList();
        EvidenceGraph graph = graphBuilder.build(result);
        return new StructuredExplanation(
            catalogVersion,
            goalType,
            primaryCurrencyOrNull,
            result.getAllocation(),
            result.getPortfolioIds(),
            breakdown,
            evidence,
            graph.getDigest(),
            graph.getEvidenceIds()
        );
    }
}
