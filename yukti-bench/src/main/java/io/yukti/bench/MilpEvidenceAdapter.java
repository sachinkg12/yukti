package io.yukti.bench;

import io.yukti.core.domain.Category;
import io.yukti.core.domain.EvidenceBlock;
import io.yukti.core.domain.Money;
import io.yukti.core.domain.ObjectiveBreakdown;
import io.yukti.core.domain.OptimizationResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps MILP run with evidence to OptimizationResult so the existing explanation
 * pipeline (EvidenceGraphBuilder → ClaimGenerator → ClaimVerifier) works unchanged.
 * OCP: adapter is extension; Optimizer and explanation pipeline closed for modification.
 */
public final class MilpEvidenceAdapter {

    private MilpEvidenceAdapter() {}

    /**
     * Convert one MILP run (from milp_evidence.json) to OptimizationResult.
     */
    public static OptimizationResult fromMilpRun(MilpRunWithEvidence run) {
        List<String> portfolio = run.portfolio();
        Map<Category, String> allocation = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : run.allocation().entrySet()) {
            try {
                allocation.put(Category.valueOf(e.getKey()), e.getValue());
            } catch (IllegalArgumentException ignored) {
                // skip unknown category names
            }
        }
        MilpRunWithEvidence.BreakdownDto b = run.breakdown();
        ObjectiveBreakdown breakdown = new ObjectiveBreakdown(
            Money.usd(BigDecimal.valueOf(b.earnUsd())),
            Money.usd(BigDecimal.valueOf(b.creditsUsd())),
            Money.usd(BigDecimal.valueOf(b.feesUsd()))
        );
        List<EvidenceBlock> blocks = new ArrayList<>();
        for (MilpRunWithEvidence.EvidenceBlockDto dto : run.evidenceBlocks()) {
            blocks.add(new EvidenceBlock(
                nullToEmpty(dto.type()),
                nullToEmpty(dto.cardId()),
                nullToEmpty(dto.category()),
                nullToEmpty(dto.content())
            ));
        }
        return new OptimizationResult(
            portfolio,
            allocation,
            breakdown,
            blocks,
            "MILP-derived narrative.",
            List.of()
        );
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
