package io.yukti.engine.optimizer;

import com.google.ortools.linearsolver.MPSolver;
import io.yukti.core.api.Card;
import io.yukti.core.api.Catalog;
import io.yukti.core.api.Optimizer;
import io.yukti.core.domain.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * MILP-based portfolio optimizer — the primary optimizer (Paper §2).
 *
 * <p>Solves the exact mixed-integer linear program for credit card portfolio
 * optimization using OR-Tools CBC. Produces optimal solutions with rich
 * evidence blocks for the explanation pipeline.
 *
 * <p>Deterministic: same inputs + catalog + valuation config always produce
 * identical results. CBC is a deterministic solver.
 *
 * <p>Fallback: if the MILP is infeasible (should be rare with a reasonable catalog),
 * delegates to {@link CapAwareGreedyOptimizer} and logs a warning.
 *
 * @see MilpModelBuilder  model construction
 * @see MilpSolutionAnalyzer  post-solve evidence derivation
 * @see MilpSolution  solved variable values
 */
public final class MilpOptimizer implements Optimizer {

    private static final Logger LOG = Logger.getLogger(MilpOptimizer.class.getName());
    private static final String ID = "milp-v1";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public OptimizationResult optimize(OptimizationRequest request, Catalog catalog) {
        SpendProfile profile = request.getSpendProfile();
        UserGoal userGoal = request.getUserGoal();
        UserConstraints constraints = request.getUserConstraints();

        if (profile.isEmpty()) {
            return OptimizationResult.empty("No spend provided.");
        }

        // Build MILP model
        MilpModelBuilder builder = new MilpModelBuilder(catalog, profile, userGoal, constraints);
        MPSolver solver = builder.build();

        // Solve
        long startMs = System.currentTimeMillis();
        MPSolver.ResultStatus status = solver.solve();
        long elapsedMs = System.currentTimeMillis() - startMs;

        if (status == MPSolver.ResultStatus.FEASIBLE) {
            // Sub-optimal but valid solution — use it but tag status
            LOG.log(Level.INFO,
                "MILP solver returned FEASIBLE (not OPTIMAL); using sub-optimal solution");
            MilpSolution feasibleSolution = builder.extractSolution(solver, elapsedMs);
            MilpSolutionAnalyzer feasibleAnalyzer = new MilpSolutionAnalyzer(
                catalog, profile, userGoal, constraints);
            List<EvidenceBlock> feasibleEvidence = feasibleAnalyzer.deriveEvidence(feasibleSolution);
            Map<Category, String> feasibleAlloc = feasibleSolution.allocationMap();
            Map<String, Card> feasibleCards = new LinkedHashMap<>();
            for (Card card : catalog.cards()) { feasibleCards.put(card.id(), card); }
            return new OptimizationResult(
                feasibleSolution.selectedCardIds(), feasibleAlloc,
                feasibleSolution.objectiveBreakdown(), feasibleEvidence,
                buildNarrative(feasibleSolution, feasibleAlloc, feasibleCards),
                List.of(), SolverStatus.FEASIBLE);
        }

        if (status != MPSolver.ResultStatus.OPTIMAL) {
            // INFEASIBLE, UNBOUNDED, or other failure — fall back to Greedy
            SolverStatus mappedStatus = mapCbcStatus(status);
            LOG.log(Level.WARNING,
                "MILP solver returned {0}; falling back to CapAwareGreedyOptimizer", status);
            OptimizationResult greedyResult = new CapAwareGreedyOptimizer().optimize(request, catalog);
            // Re-wrap with FALLBACK_GREEDY status and fallback evidence
            List<EvidenceBlock> augmented = new ArrayList<>(greedyResult.getEvidenceBlocks());
            augmented.add(new EvidenceBlock(
                "SOLVER_FALLBACK", "", "",
                "MILP solver returned " + status + "; result produced by greedy fallback."));
            return new OptimizationResult(
                greedyResult.getPortfolioIds(), greedyResult.getAllocation(),
                greedyResult.getBreakdown(), augmented, greedyResult.getNarrative(),
                greedyResult.getSwitchingNotes(), SolverStatus.FALLBACK_GREEDY);
        }

        // Extract solution
        MilpSolution solution = builder.extractSolution(solver, elapsedMs);

        // Derive evidence
        MilpSolutionAnalyzer analyzer = new MilpSolutionAnalyzer(
            catalog, profile, userGoal, constraints);
        List<EvidenceBlock> evidenceBlocks = analyzer.deriveEvidence(solution);

        // Build allocation map
        Map<Category, String> allocation = solution.allocationMap();

        // Build narrative
        Map<String, Card> cardById = new LinkedHashMap<>();
        for (Card card : catalog.cards()) {
            cardById.put(card.id(), card);
        }
        String narrative = buildNarrative(solution, allocation, cardById);

        return new OptimizationResult(
            solution.selectedCardIds(),
            allocation,
            solution.objectiveBreakdown(),
            evidenceBlocks,
            narrative,
            List.of()  // No switching notes — MILP allocation is already optimal
        );
    }

    private static SolverStatus mapCbcStatus(MPSolver.ResultStatus status) {
        return switch (status) {
            case INFEASIBLE -> SolverStatus.INFEASIBLE;
            case UNBOUNDED -> SolverStatus.UNBOUNDED;
            default -> SolverStatus.NOT_SOLVED;
        };
    }

    private String buildNarrative(MilpSolution solution, Map<Category, String> allocation,
                                  Map<String, Card> cardById) {
        StringBuilder sb = new StringBuilder();
        sb.append("Portfolio: ");
        sb.append(solution.selectedCardIds().stream()
            .map(id -> {
                Card c = cardById.get(id);
                return c != null ? c.displayName() : id;
            })
            .collect(Collectors.joining(", ")));
        sb.append(". ");

        ObjectiveBreakdown bd = solution.objectiveBreakdown();
        sb.append("Earn: ").append(bd.getEarnValue())
          .append(", Credits: ").append(bd.getCreditsValue())
          .append(", Fees: ").append(bd.getFees())
          .append(", Net: ").append(bd.getNet())
          .append(". ");

        sb.append("MILP-optimal solution (").append(solution.solveTimeMs()).append("ms).");
        return sb.toString();
    }
}
