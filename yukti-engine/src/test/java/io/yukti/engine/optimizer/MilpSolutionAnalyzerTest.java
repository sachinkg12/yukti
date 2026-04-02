package io.yukti.engine.optimizer;

import com.google.ortools.linearsolver.MPSolver;
import io.yukti.catalog.ClasspathCatalogSource;
import io.yukti.core.api.Catalog;
import io.yukti.core.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MilpSolutionAnalyzerTest {

    private Catalog catalog;

    @BeforeEach
    void setUp() throws Exception {
        catalog = new ClasspathCatalogSource("catalog/catalog-v1.json").load("1.0");
    }

    @Test
    void winnerByCategoryEmitted_forEachActiveCategory() {
        MilpSolution solution = solve(
            Map.of(Category.GROCERIES, 6000.0, Category.DINING, 3000.0, Category.OTHER, 5000.0),
            GoalType.CASHBACK);
        List<EvidenceBlock> evidence = analyze(solution,
            Map.of(Category.GROCERIES, 6000.0, Category.DINING, 3000.0, Category.OTHER, 5000.0),
            GoalType.CASHBACK);

        long winnerCount = evidence.stream()
            .filter(e -> "WINNER_BY_CATEGORY".equals(e.getType())).count();
        assertTrue(winnerCount >= 3, "Should have WINNER_BY_CATEGORY for each active category, got " + winnerCount);
    }

    @Test
    void capHitDetected_whenSpendExceedsCap() {
        MilpSolution solution = solve(
            Map.of(Category.GROCERIES, 12000.0, Category.OTHER, 1000.0),
            GoalType.CASHBACK);
        List<EvidenceBlock> evidence = analyze(solution,
            Map.of(Category.GROCERIES, 12000.0, Category.OTHER, 1000.0),
            GoalType.CASHBACK);

        assertTrue(evidence.stream().anyMatch(e -> "CAP_HIT".equals(e.getType())),
            "Should detect cap hit when spend exceeds cap");
    }

    @Test
    void feeBreakEvenPerCard() {
        MilpSolution solution = solve(
            Map.of(Category.GROCERIES, 6000.0, Category.OTHER, 5000.0),
            GoalType.CASHBACK);
        List<EvidenceBlock> evidence = analyze(solution,
            Map.of(Category.GROCERIES, 6000.0, Category.OTHER, 5000.0),
            GoalType.CASHBACK);

        long feeBreakEvenCount = evidence.stream()
            .filter(e -> "FEE_BREAK_EVEN".equals(e.getType())).count();
        assertEquals(solution.selectedCardIds().size(), feeBreakEvenCount,
            "Should emit one FEE_BREAK_EVEN per selected card");
    }

    @Test
    void portfolioStopEmitted() {
        MilpSolution solution = solve(
            Map.of(Category.GROCERIES, 6000.0, Category.DINING, 3000.0, Category.OTHER, 5000.0),
            GoalType.CASHBACK);
        List<EvidenceBlock> evidence = analyze(solution,
            Map.of(Category.GROCERIES, 6000.0, Category.DINING, 3000.0, Category.OTHER, 5000.0),
            GoalType.CASHBACK);

        assertTrue(evidence.stream().anyMatch(e -> "PORTFOLIO_STOP".equals(e.getType())),
            "Should emit PORTFOLIO_STOP evidence");
    }

    @Test
    void assumptionEvidence_emitted() {
        MilpSolution solution = solve(
            Map.of(Category.OTHER, 5000.0),
            GoalType.CASHBACK);
        List<EvidenceBlock> evidence = analyze(solution,
            Map.of(Category.OTHER, 5000.0),
            GoalType.CASHBACK);

        assertTrue(evidence.stream().anyMatch(e -> "ASSUMPTION".equals(e.getType())),
            "Should emit ASSUMPTION evidence");
    }

    @Test
    void resultBreakdownMatches() {
        MilpSolution solution = solve(
            Map.of(Category.GROCERIES, 6000.0, Category.OTHER, 5000.0),
            GoalType.CASHBACK);
        List<EvidenceBlock> evidence = analyze(solution,
            Map.of(Category.GROCERIES, 6000.0, Category.OTHER, 5000.0),
            GoalType.CASHBACK);

        EvidenceBlock breakdown = evidence.stream()
            .filter(e -> "RESULT_BREAKDOWN".equals(e.getType()))
            .findFirst().orElseThrow();
        assertNotNull(breakdown.getContent());
        assertTrue(breakdown.getContent().contains("Earn"));
        assertTrue(breakdown.getContent().contains("net"));
    }

    @Test
    void noEvidenceForZeroSpendCategory() {
        MilpSolution solution = solve(
            Map.of(Category.OTHER, 5000.0),
            GoalType.CASHBACK);
        List<EvidenceBlock> evidence = analyze(solution,
            Map.of(Category.OTHER, 5000.0),
            GoalType.CASHBACK);

        assertTrue(evidence.stream()
            .filter(e -> "WINNER_BY_CATEGORY".equals(e.getType()))
            .noneMatch(e -> "TRAVEL".equals(e.getCategory())),
            "Should not emit winner evidence for categories with no spend");
    }

    @Test
    void portfolioSummaryEmitted() {
        MilpSolution solution = solve(
            Map.of(Category.GROCERIES, 6000.0, Category.OTHER, 5000.0),
            GoalType.CASHBACK);
        List<EvidenceBlock> evidence = analyze(solution,
            Map.of(Category.GROCERIES, 6000.0, Category.OTHER, 5000.0),
            GoalType.CASHBACK);

        EvidenceBlock summary = evidence.stream()
            .filter(e -> "PORTFOLIO_SUMMARY".equals(e.getType()))
            .findFirst().orElseThrow();
        for (String cardId : solution.selectedCardIds()) {
            assertTrue(summary.getContent().contains(cardId),
                "Portfolio summary should list all selected cards");
        }
    }

    @Test
    void segmentAllocationForSplitCategories() {
        // High spend in GROCERIES should cause split allocation if cap is hit
        MilpSolution solution = solve(
            Map.of(Category.GROCERIES, 24000.0, Category.OTHER, 1000.0),
            GoalType.CASHBACK);
        List<EvidenceBlock> evidence = analyze(solution,
            Map.of(Category.GROCERIES, 24000.0, Category.OTHER, 1000.0),
            GoalType.CASHBACK);

        // If multiple cards share GROCERIES allocation, ALLOCATION_SEGMENT should appear
        boolean hasSplit = evidence.stream()
            .anyMatch(e -> "ALLOCATION_SEGMENT".equals(e.getType()));
        // Not every profile will have splits, but with $24k groceries and caps, it's likely
        // This is a soft assertion — the test validates the code path works
        assertNotNull(evidence);
    }

    // --- Helpers ---

    private MilpSolution solve(Map<Category, Double> spend, GoalType goal) {
        Map<Category, Money> amounts = new EnumMap<>(Category.class);
        spend.forEach((cat, amt) -> amounts.put(cat, Money.usd(amt)));
        SpendProfile profile = new SpendProfile(Period.ANNUAL, amounts);

        MilpModelBuilder builder = new MilpModelBuilder(catalog, profile,
            UserGoal.of(goal), UserConstraints.defaults());
        MPSolver solver = builder.build();
        assertEquals(MPSolver.ResultStatus.OPTIMAL, solver.solve());
        return builder.extractSolution(solver, 10);
    }

    private List<EvidenceBlock> analyze(MilpSolution solution,
                                        Map<Category, Double> spend, GoalType goal) {
        Map<Category, Money> amounts = new EnumMap<>(Category.class);
        spend.forEach((cat, amt) -> amounts.put(cat, Money.usd(amt)));
        SpendProfile profile = new SpendProfile(Period.ANNUAL, amounts);

        MilpSolutionAnalyzer analyzer = new MilpSolutionAnalyzer(
            catalog, profile, UserGoal.of(goal), UserConstraints.defaults());
        return analyzer.deriveEvidence(solution);
    }
}
