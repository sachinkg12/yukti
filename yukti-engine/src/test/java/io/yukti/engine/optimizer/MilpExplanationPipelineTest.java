package io.yukti.engine.optimizer;

import io.yukti.catalog.ClasspathCatalogSource;
import io.yukti.core.api.Catalog;
import io.yukti.core.api.Optimizer;
import io.yukti.core.domain.*;
import io.yukti.core.explainability.NarrativeExplanation;
import io.yukti.core.explainability.StructuredExplanation;
import io.yukti.engine.explainability.DeterministicExplanationGeneratorV1;
import io.yukti.engine.explainability.EvidenceGraphBuilder;
import io.yukti.engine.explainability.StructuredExplanationBuilder;
import io.yukti.explain.core.claims.ClaimVerifier;
import io.yukti.explain.core.claims.VerificationReport;
import io.yukti.explain.core.evidence.graph.EvidenceGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test proving OCP: the existing explanation pipeline works
 * unchanged with MILP-produced OptimizationResult.
 *
 * <p>Flow: MilpOptimizer → StructuredExplanationBuilder → EvidenceGraphBuilder
 * → DeterministicExplanationGeneratorV1 → ClaimVerifier.
 */
class MilpExplanationPipelineTest {

    private Catalog catalog;
    private Optimizer milp;

    @BeforeEach
    void setUp() throws Exception {
        catalog = new ClasspathCatalogSource("catalog/catalog-v1.json").load("1.0");
        milp = new MilpOptimizer();
    }

    @Test
    void milpResult_passesVerification_cashback() {
        verifyPipeline(
            Map.of(Category.GROCERIES, 6000.0, Category.DINING, 3000.0,
                Category.GAS, 2400.0, Category.OTHER, 5000.0),
            GoalType.CASHBACK);
    }

    @Test
    void milpResult_passesVerification_flexPoints() {
        verifyPipeline(
            Map.of(Category.GROCERIES, 6000.0, Category.DINING, 3000.0,
                Category.TRAVEL, 5000.0, Category.OTHER, 4000.0),
            GoalType.FLEX_POINTS);
    }

    @Test
    void milpResult_passesVerification_programPoints() {
        Map<Category, Money> amounts = new EnumMap<>(Category.class);
        Map.of(Category.TRAVEL, 12000.0, Category.DINING, 6000.0, Category.OTHER, 3000.0)
            .forEach((cat, amt) -> amounts.put(cat, Money.usd(amt)));

        UserGoal goal = new UserGoal(GoalType.PROGRAM_POINTS,
            java.util.Optional.of(RewardCurrencyType.AVIOS), java.util.List.of(), Map.of());

        OptimizationRequest request = new OptimizationRequest(
            new SpendProfile(Period.ANNUAL, amounts),
            goal, UserConstraints.defaults(), Map.of());

        verifyPipelineForRequest(request);
    }

    @Test
    void milpResult_evidenceGraphHasValidDigest() {
        OptimizationResult result = runMilp(
            Map.of(Category.GROCERIES, 6000.0, Category.OTHER, 5000.0),
            GoalType.CASHBACK);

        EvidenceGraph graph = new EvidenceGraphBuilder().build(result);

        assertNotNull(graph.getDigest());
        assertFalse(graph.getDigest().isEmpty());
        assertEquals(64, graph.getDigest().length(), "SHA-256 digest should be 64 hex chars");
    }

    @Test
    void milpResult_claimsAreGrounded() {
        OptimizationResult result = runMilp(
            Map.of(Category.GROCERIES, 6000.0, Category.DINING, 3000.0, Category.OTHER, 5000.0),
            GoalType.CASHBACK);

        EvidenceGraph graph = new EvidenceGraphBuilder().build(result);
        StructuredExplanationBuilder structBuilder = new StructuredExplanationBuilder();
        StructuredExplanation structured = structBuilder.build(result, catalog.version(), GoalType.CASHBACK, null);
        DeterministicExplanationGeneratorV1 generator = new DeterministicExplanationGeneratorV1();
        NarrativeExplanation explanation = generator.generate(structured);

        assertFalse(explanation.claims().isEmpty(), "Should produce claims");

        // All cited evidence IDs should exist in the graph
        var graphIds = graph.getEvidenceIds();
        for (var claim : explanation.claims()) {
            for (String evidenceId : claim.citedEvidenceIds()) {
                assertTrue(graphIds.contains(evidenceId) || "result".equals(evidenceId),
                    "Claim " + claim.claimId() + " cites unknown evidence: " + evidenceId);
            }
        }
    }

    @Test
    void milpResult_narrativeIsNonEmpty() {
        OptimizationResult result = runMilp(
            Map.of(Category.GROCERIES, 6000.0, Category.OTHER, 5000.0),
            GoalType.CASHBACK);

        StructuredExplanationBuilder structBuilder = new StructuredExplanationBuilder();
        StructuredExplanation structured = structBuilder.build(result, catalog.version(), GoalType.CASHBACK, null);
        DeterministicExplanationGeneratorV1 generator = new DeterministicExplanationGeneratorV1();
        NarrativeExplanation explanation = generator.generate(structured);

        assertNotNull(explanation.fullText());
        assertFalse(explanation.fullText().isBlank(), "Narrative should be non-empty");
        assertTrue(explanation.claimCount() > 0, "Should have at least one claim");
    }

    // --- Private helpers ---

    private void verifyPipeline(Map<Category, Double> spend, GoalType goal) {
        Map<Category, Money> amounts = new EnumMap<>(Category.class);
        spend.forEach((cat, amt) -> amounts.put(cat, Money.usd(amt)));
        OptimizationRequest request = new OptimizationRequest(
            new SpendProfile(Period.ANNUAL, amounts),
            UserGoal.of(goal), UserConstraints.defaults(), Map.of());
        verifyPipelineForRequest(request);
    }

    private void verifyPipelineForRequest(OptimizationRequest request) {
        OptimizationResult result = milp.optimize(request, catalog);

        assertFalse(result.getPortfolioIds().isEmpty(), "MILP should produce non-empty portfolio");
        assertFalse(result.getEvidenceBlocks().isEmpty(), "MILP should produce evidence blocks");

        // Build evidence graph
        EvidenceGraph graph = new EvidenceGraphBuilder().build(result);
        assertNotNull(graph);

        // Build structured explanation
        StructuredExplanationBuilder structBuilder = new StructuredExplanationBuilder();
        GoalType goalType = request.getUserGoal().getGoalType();
        String primary = request.getUserGoal().getPrimaryCurrency().map(Enum::name).orElse(null);
        StructuredExplanation structured = structBuilder.build(result, catalog.version(), goalType, primary);

        // Generate deterministic claims
        DeterministicExplanationGeneratorV1 generator = new DeterministicExplanationGeneratorV1();
        NarrativeExplanation explanation = generator.generate(structured);
        assertFalse(explanation.claims().isEmpty(), "Should produce claims from MILP evidence");

        // Verify claims against evidence graph (fail-closed: must pass)
        ClaimVerifier verifier = new ClaimVerifier();
        VerificationReport report = verifier.verify(graph, explanation.claims());
        assertTrue(report.passed(),
            "Deterministic claims from MILP evidence should pass verification. Violations: "
            + report.allViolations());
    }

    private OptimizationResult runMilp(Map<Category, Double> spend, GoalType goal) {
        Map<Category, Money> amounts = new EnumMap<>(Category.class);
        spend.forEach((cat, amt) -> amounts.put(cat, Money.usd(amt)));
        return milp.optimize(
            new OptimizationRequest(
                new SpendProfile(Period.ANNUAL, amounts),
                UserGoal.of(goal), UserConstraints.defaults(), Map.of()),
            catalog);
    }
}
