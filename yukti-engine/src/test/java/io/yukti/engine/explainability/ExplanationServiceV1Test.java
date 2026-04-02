package io.yukti.engine.explainability;

import io.yukti.core.domain.*;
import io.yukti.core.explainability.NarrativeExplanation;
import io.yukti.explain.core.evidence.graph.EvidenceGraph;
import io.yukti.explain.core.evidence.graph.EvidenceNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExplanationServiceV1Test {

    @Test
    void explain_aiNarratorFallback_whenProviderThrows_returnsDeterministic() {
        LlmNarrator narrator = new LlmNarrator(new StubLlmProvider(new RuntimeException("stub failure")));
        ExplanationServiceV1 service = new ExplanationServiceV1(
            new DeterministicExplanationGeneratorV1(),
            narrator,
            new StructuredExplanationBuilder()
        );

        OptimizationResult result = sampleResult();
        NarrativeExplanation out = service.explain(result, "1.0", GoalType.CASHBACK, null);

        assertNotNull(out);
        assertFalse(out.fullText().isBlank());
        assertTrue(out.fullText().contains("Net value"));
    }

    @Test
    void explain_aiNarratorValidation_rejectsInvalidClaimJson_andFallsBack() {
        // Stub returns free text, not valid claim JSON -> parse fails, fallback to deterministic
        StubLlmProvider stub = new StubLlmProvider("Your portfolio is great! You'll save $9999 annually.");
        LlmNarrator narrator = new LlmNarrator(stub);
        OptimizationResult result = sampleResult();
        var structured = new StructuredExplanationBuilder().build(result, "1.0", GoalType.CASHBACK, null);

        io.yukti.core.explainability.NarrationException ex = assertThrows(
            io.yukti.core.explainability.NarrationException.class,
            () -> narrator.narrate(structured)
        );
        assertNotNull(ex.getMessage());
        // Service fallback: when narrate throws, explain() returns deterministic
        NarrativeExplanation fallback = new ExplanationServiceV1(
            new DeterministicExplanationGeneratorV1(), narrator, new StructuredExplanationBuilder()
        ).explain(result, "1.0", GoalType.CASHBACK, null, true);
        assertTrue(fallback.fullText().contains("Net value"));
    }

    @Test
    void explain_noOpNarrator_returnsDeterministic() {
        ExplanationServiceV1 service = new ExplanationServiceV1(
            new DeterministicExplanationGeneratorV1(),
            new NoOpNarrator(),
            new StructuredExplanationBuilder()
        );
        OptimizationResult result = sampleResult();
        NarrativeExplanation out = service.explain(result, "1.0", GoalType.CASHBACK, null);
        assertNotNull(out);
        assertTrue(out.fullText().contains("Net value"));
    }

    @Test
    void explain_fakeNarrator_withLlmMode_embedsAiSection() {
        ExplanationServiceV1 service = new ExplanationServiceV1(
            new DeterministicExplanationGeneratorV1(),
            new FakeNarrator(),
            new StructuredExplanationBuilder()
        );
        OptimizationResult result = sampleResult();
        NarrativeExplanation out = service.explain(result, "1.0", GoalType.CASHBACK, null, true);
        assertNotNull(out);
        assertTrue(out.fullText().contains("AI_NARRATION_OK"), "FakeNarrator output must appear when useLlmNarration=true");
        assertTrue(out.fullText().contains("## Narrative (AI)"));
    }

    // --- LLM claim path: schema + verification gate; all failures fallback to deterministic ---

    @Test
    void explain_llmClaimGenerator_malformedJson_fallsBackToDeterministic() {
        LlmClaimGenerator llm = new LlmClaimGeneratorImpl(new StubLlmProvider("not valid json at all {{{"));
        ExplanationServiceV1 service = new ExplanationServiceV1(
            new DeterministicExplanationGeneratorV1(),
            new NoOpNarrator(),
            new StructuredExplanationBuilder(),
            llm,
            true
        );
        OptimizationResult result = sampleResult();
        NarrativeExplanation out = service.explain(result, "1.0", GoalType.CASHBACK, null, true);
        assertNotNull(out);
        assertTrue(out.fullText().contains("Net value"), "Must fallback to deterministic narrative");
    }

    @Test
    void explain_llmClaimGenerator_extraNumber_failsVerification_fallsBack() {
        String winnerEid = firstWinnerByCategoryEvidenceId(sampleResult());
        String claimsJson = "[{\"claimId\":\"c1\",\"claimType\":\"COMPARISON\",\"normalizedFields\":{},"
            + "\"citedEvidenceIds\":[\"" + winnerEid + "\"],\"citedEntities\":[],\"citedNumbers\":[\"999\"]}]";
        LlmClaimGenerator llm = new LlmClaimGeneratorImpl(new StubLlmProvider(claimsJson));
        ExplanationServiceV1 service = new ExplanationServiceV1(
            new DeterministicExplanationGeneratorV1(),
            new NoOpNarrator(),
            new StructuredExplanationBuilder(),
            llm,
            true
        );
        OptimizationResult result = sampleResult();
        NarrativeExplanation out = service.explain(result, "1.0", GoalType.CASHBACK, null, true);
        assertNotNull(out);
        assertTrue(out.fullText().contains("Net value"), "Extra number 999 must fail verification and fallback");
    }

    @Test
    void explain_llmClaimGenerator_unknownEntity_failsVerification_fallsBack() {
        String winnerEid = firstWinnerByCategoryEvidenceId(sampleResult());
        String claimsJson = "[{\"claimId\":\"c1\",\"claimType\":\"COMPARISON\",\"normalizedFields\":{},"
            + "\"citedEvidenceIds\":[\"" + winnerEid + "\"],\"citedEntities\":[\"UNKNOWN_ENTITY\"],\"citedNumbers\":[]}]";
        LlmClaimGenerator llm = new LlmClaimGeneratorImpl(new StubLlmProvider(claimsJson));
        ExplanationServiceV1 service = new ExplanationServiceV1(
            new DeterministicExplanationGeneratorV1(),
            new NoOpNarrator(),
            new StructuredExplanationBuilder(),
            llm,
            true
        );
        OptimizationResult result = sampleResult();
        NarrativeExplanation out = service.explain(result, "1.0", GoalType.CASHBACK, null, true);
        assertNotNull(out);
        assertTrue(out.fullText().contains("Net value"), "Unknown entity must fail verification and fallback");
    }

    private static String firstWinnerByCategoryEvidenceId(OptimizationResult result) {
        EvidenceGraph graph = new EvidenceGraphBuilder().build(result);
        return graph.getNodes().stream()
            .filter(n -> "WINNER_BY_CATEGORY".equals(n.type()))
            .map(EvidenceNode::evidenceId)
            .findFirst()
            .orElseThrow(() -> new AssertionError("No WINNER_BY_CATEGORY node in graph"));
    }

    private OptimizationResult sampleResult() {
        Map<Category, Money> amounts = Map.of(Category.GROCERIES, Money.usd(6000), Category.DINING, Money.usd(3000));
        SpendProfile profile = new SpendProfile(Period.ANNUAL, amounts);
        OptimizationRequest req = new OptimizationRequest(
            profile, UserGoal.of(GoalType.CASHBACK), UserConstraints.defaults(), Map.of());
        var catalog = new io.yukti.catalog.ClasspathCatalogSource("catalog/catalog-v1.json");
        io.yukti.core.api.Catalog cat;
        try {
            cat = catalog.load("1.0");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        var opt = new io.yukti.engine.optimizer.GreedyPortfolioOptimizerV1();
        return opt.optimize(req, cat);
    }
}
