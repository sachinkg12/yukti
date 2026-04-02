package io.yukti.engine.optimizer;

import io.yukti.core.api.*;
import io.yukti.core.domain.*;
import io.yukti.core.explainability.evidence.AssumptionEvidence;
import io.yukti.engine.reward.RewardModelV1;
import io.yukti.engine.valuation.CppResolver;
import io.yukti.engine.valuation.ValuationModelV1;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Enumerate-and-allocate baseline: tries all C(n,1) + C(n,2) + C(n,3) portfolios
 * (k &le; 3), evaluates each with greedy segment-filling via AllocationSolverV1_1,
 * and returns the portfolio with maximum net value.
 *
 * <p>Serves as a strong baseline but not a global optimality oracle due to the
 * two-stage decomposition (enumerate portfolios, then greedily allocate within each).
 * The MILP jointly optimizes portfolio selection and allocation, and can discover
 * solutions the two-stage approach misses. For a 20 card catalog, total candidates
 * are C(20,1)+C(20,2)+C(20,3) = 1,350 which is tractable.
 *
 * <p>Deterministic: same inputs always produce identical results. Tie breaking follows
 * the same conventions as AllocationSolverV1_1 (rate, fee, lexicographic card id).
 */
public final class ExhaustiveSearchOptimizer implements Optimizer {

    @Override
    public String id() {
        return "exhaustive-search-v1";
    }

    @Override
    public OptimizationResult optimize(OptimizationRequest request, Catalog catalog) {
        SpendProfile profile = request.getSpendProfile();
        UserGoal userGoal = request.getUserGoal();
        UserConstraints constraints = request.getUserConstraints();

        if (profile.isEmpty()) {
            return OptimizationResult.empty("No spend provided.");
        }

        int maxCards = Math.min(3, constraints.getMaxCards());
        List<Card> allCards = new ArrayList<>(catalog.cards());
        Map<String, Card> cardById = allCards.stream().collect(Collectors.toMap(Card::id, c -> c));
        List<String> cardIds = allCards.stream().map(Card::id).sorted().collect(Collectors.toList());

        FeeBudgetPolicy feeBudget = new FeeBudgetPolicy();
        RewardModel rewardModel = new RewardModelV1();
        ValuationModel valuationModel = new ValuationModelV1(catalog);
        AllocationSolverV1_1 solver = new AllocationSolverV1_1();

        AllocationResult bestResult = null;
        List<String> bestPortfolio = List.of();
        BigDecimal bestNet = null;

        // Enumerate all portfolios of size 1..maxCards
        for (int k = 1; k <= maxCards; k++) {
            List<List<String>> combos = combinations(cardIds, k);
            for (List<String> portfolio : combos) {
                if (!feeBudget.enforce(portfolio, constraints, catalog)) {
                    continue;
                }

                AllocationResult result = solver.solve(request, catalog, portfolio, rewardModel, valuationModel);
                BigDecimal net = result.netValueUsd().getAmount();

                if (bestNet == null || net.compareTo(bestNet) > 0
                        || (net.compareTo(bestNet) == 0 && compareTieBreak(portfolio, bestPortfolio) < 0)) {
                    bestNet = net;
                    bestResult = result;
                    bestPortfolio = portfolio;
                }
            }
        }

        if (bestResult == null) {
            return OptimizationResult.empty("No feasible portfolio found within fee budget.");
        }

        // Build evidence and narrative
        CppResolver cppResolver = new CppResolver(catalog);
        List<EvidenceBlock> finalEvidence = new ArrayList<>(bestResult.evidenceBlocks());
        AssumptionEvidence assumptionEvidence = cppResolver.buildAssumptionEvidence(userGoal, null);
        finalEvidence.add(new EvidenceBlock("ASSUMPTION", "valuation", "valuation", assumptionEvidence.content()));

        ObjectiveBreakdown breakdown = new ObjectiveBreakdown(
                bestResult.earnedValueUsd(),
                bestResult.creditValueUsd(),
                bestResult.feesUsd());

        StringBuilder narrative = new StringBuilder();
        narrative.append("[Exhaustive search] Portfolio: ").append(
                bestPortfolio.stream().map(id -> cardById.get(id).displayName()).collect(Collectors.joining(", ")));
        narrative.append(". Earn: ").append(bestResult.earnedValueUsd());
        narrative.append(", Credits: ").append(bestResult.creditValueUsd());
        narrative.append(", Fees: ").append(bestResult.feesUsd());
        narrative.append(", Net: ").append(breakdown.getNet()).append(". ");
        narrative.append("Evaluated ").append(totalCandidates(cardIds.size(), maxCards)).append(" portfolios.");

        List<String> switchingNotes = buildSwitchingNotes(bestResult.allocationPlan(), cardById);

        return new OptimizationResult(
                bestPortfolio,
                bestResult.allocationByCategory(),
                breakdown,
                finalEvidence,
                narrative.toString(),
                switchingNotes
        );
    }

    /**
     * Generate all k-combinations from the sorted list of card IDs.
     * Returns combinations in lexicographic order for determinism.
     */
    static List<List<String>> combinations(List<String> items, int k) {
        List<List<String>> result = new ArrayList<>();
        combinationsHelper(items, k, 0, new ArrayList<>(), result);
        return result;
    }

    private static void combinationsHelper(List<String> items, int k, int start,
                                            List<String> current, List<List<String>> result) {
        if (current.size() == k) {
            result.add(List.copyOf(current));
            return;
        }
        for (int i = start; i < items.size(); i++) {
            current.add(items.get(i));
            combinationsHelper(items, k, i + 1, current, result);
            current.remove(current.size() - 1);
        }
    }

    /** Deterministic tie break for equal net value: prefer lexicographically smaller portfolio. */
    private int compareTieBreak(List<String> a, List<String> b) {
        int sizeCompare = Integer.compare(a.size(), b.size());
        if (sizeCompare != 0) return sizeCompare;
        for (int i = 0; i < a.size(); i++) {
            int cmp = a.get(i).compareTo(b.get(i));
            if (cmp != 0) return cmp;
        }
        return 0;
    }

    private int totalCandidates(int n, int maxK) {
        int total = 0;
        for (int k = 1; k <= maxK; k++) {
            total += choose(n, k);
        }
        return total;
    }

    private int choose(int n, int k) {
        if (k > n) return 0;
        if (k == 0 || k == n) return 1;
        int result = 1;
        for (int i = 0; i < k; i++) {
            result = result * (n - i) / (i + 1);
        }
        return result;
    }

    private List<String> buildSwitchingNotes(AllocationPlan plan, Map<String, Card> cardById) {
        List<String> notes = new ArrayList<>();
        if (plan == null) return notes;
        for (Map.Entry<Category, List<AllocationSegment>> e : plan.segmentsByCategory().entrySet()) {
            List<AllocationSegment> segs = e.getValue();
            if (segs.size() < 2) continue;
            Category cat = e.getKey();
            String firstCard = segs.get(0).cardId();
            String secondCard = segs.get(1).cardId();
            Card second = cardById.get(secondCard);
            if (second != null) {
                notes.add(String.format("%s: use %s then switch to %s for remainder.",
                        cat.name(), cardById.get(firstCard).displayName(), second.displayName()));
            }
        }
        return notes;
    }
}
