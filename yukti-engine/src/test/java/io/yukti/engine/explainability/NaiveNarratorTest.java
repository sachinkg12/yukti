package io.yukti.engine.explainability;

import io.yukti.core.domain.Category;
import io.yukti.core.domain.EvidenceBlock;
import io.yukti.core.domain.GoalType;
import io.yukti.core.domain.Money;
import io.yukti.core.domain.ObjectiveBreakdown;
import io.yukti.core.domain.OptimizationResult;
import io.yukti.core.explainability.NarrativeExplanation;
import io.yukti.core.explainability.StructuredExplanation;
import io.yukti.explain.core.claims.ClaimVerifier;
import io.yukti.explain.core.evidence.graph.EvidenceGraph;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NaiveNarratorTest {

    @Test
    void naiveNarrator_producesClaimsThatFailVerification() {
        OptimizationResult result = minimalResult();
        StructuredExplanation structured = new StructuredExplanationBuilder().build(result, "v1", GoalType.CASHBACK, null);
        EvidenceGraph graph = new EvidenceGraphBuilder().build(result);
        NaiveNarrator narrator = new NaiveNarrator();
        NarrativeExplanation explanation = narrator.generate(structured);

        assertFalse(explanation.claims().isEmpty());
        var report = new ClaimVerifier().verify(graph, explanation.claims());
        assertFalse(report.passed(), "NaiveNarrator must produce at least one claim that fails verification");
        assertTrue(
            report.allViolations().stream().anyMatch(v -> v.contains("fake-card-naive") || v.contains("99.99")),
            "Violations should mention the injected fake entity or number"
        );
    }

    private static OptimizationResult minimalResult() {
        Map<Category, String> allocation = new LinkedHashMap<>();
        allocation.put(Category.GROCERIES, "amex-bcp");
        allocation.put(Category.OTHER, "citi-double-cash");
        ObjectiveBreakdown breakdown = new ObjectiveBreakdown(
            Money.usd("100"),
            Money.usd("0"),
            Money.usd("95")
        );
        List<EvidenceBlock> blocks = List.of(
            new EvidenceBlock("WINNER_BY_CATEGORY", "amex-bcp", "GROCERIES", "amex-bcp wins GROCERIES: delta $60.00"),
            new EvidenceBlock("ASSUMPTION", "valuation", "valuation", "Goal: CASHBACK, cpp: {USD=1.0}")
        );
        return new OptimizationResult(
            List.of("amex-bcp", "citi-double-cash"),
            allocation,
            breakdown,
            blocks,
            "Narrative.",
            List.of()
        );
    }
}
