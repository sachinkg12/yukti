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
 * Simulated Annealing optimizer for credit card portfolio selection.
 *
 * <p>Explores the portfolio selection space (which 1-3 cards to hold) using
 * simulated annealing with geometric cooling. Each candidate portfolio is
 * evaluated by delegating allocation to {@link AllocationSolverV1_1}, which
 * greedily assigns spend segments to maximize net value.
 *
 * <p>Neighbor function (seeded Random):
 * <ul>
 *   <li>40% chance: swap one card for another not in portfolio</li>
 *   <li>30% chance: add a card if size &lt; maxCards</li>
 *   <li>30% chance: remove a card if size &gt; 1</li>
 * </ul>
 *
 * <p>Cooling schedule: geometric, T0=1000.0, alpha=0.995, iterations=5000.
 * Deterministic: seed=42L, {@link java.util.Random}.
 */
public final class SimulatedAnnealingOptimizer implements Optimizer {

    private static final long SEED = 42L;
    private static final double T0 = 1000.0;
    private static final double ALPHA = 0.995;
    private static final int ITERATIONS = 5000;

    @Override
    public String id() {
        return "sa-v1";
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

        Random rng = new Random(SEED);

        // Initial solution: random portfolio of size min(maxCards, 3)
        int initSize = Math.min(maxCards, 3);
        List<String> currentPortfolio = randomPortfolio(cardIds, initSize, rng);

        // Evaluate initial solution
        AllocationResult currentResult = null;
        BigDecimal currentNet = BigDecimal.valueOf(Long.MIN_VALUE);
        if (feeBudget.enforce(currentPortfolio, constraints, catalog)) {
            currentResult = solver.solve(request, catalog, currentPortfolio, rewardModel, valuationModel);
            currentNet = currentResult.netValueUsd().getAmount();
        }

        AllocationResult bestResult = currentResult;
        List<String> bestPortfolio = currentPortfolio;
        BigDecimal bestNet = currentNet;

        int evaluated = 1;
        double temperature = T0;

        for (int i = 0; i < ITERATIONS; i++) {
            List<String> neighbor = generateNeighbor(currentPortfolio, cardIds, maxCards, rng);

            if (!feeBudget.enforce(neighbor, constraints, catalog)) {
                temperature *= ALPHA;
                continue;
            }

            AllocationResult neighborResult = solver.solve(request, catalog, neighbor, rewardModel, valuationModel);
            BigDecimal neighborNet = neighborResult.netValueUsd().getAmount();
            evaluated++;

            double delta = neighborNet.subtract(currentNet).doubleValue();

            if (delta > 0 || rng.nextDouble() < Math.exp(delta / temperature)) {
                currentPortfolio = neighbor;
                currentResult = neighborResult;
                currentNet = neighborNet;

                if (currentNet.compareTo(bestNet) > 0
                        || (currentNet.compareTo(bestNet) == 0 && compareTieBreak(currentPortfolio, bestPortfolio) < 0)) {
                    bestNet = currentNet;
                    bestResult = currentResult;
                    bestPortfolio = currentPortfolio;
                }
            }

            temperature *= ALPHA;
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
        narrative.append("[Simulated annealing] Portfolio: ").append(
                bestPortfolio.stream().map(id -> cardById.get(id).displayName()).collect(Collectors.joining(", ")));
        narrative.append(". Earn: ").append(bestResult.earnedValueUsd());
        narrative.append(", Credits: ").append(bestResult.creditValueUsd());
        narrative.append(", Fees: ").append(bestResult.feesUsd());
        narrative.append(", Net: ").append(breakdown.getNet()).append(". ");
        narrative.append("Evaluated ").append(evaluated).append(" portfolios over ").append(ITERATIONS).append(" iterations.");

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
     * Generate a random portfolio of the given size from sorted card IDs.
     */
    private List<String> randomPortfolio(List<String> cardIds, int size, Random rng) {
        List<String> shuffled = new ArrayList<>(cardIds);
        Collections.shuffle(shuffled, rng);
        List<String> portfolio = new ArrayList<>(shuffled.subList(0, Math.min(size, shuffled.size())));
        Collections.sort(portfolio);
        return portfolio;
    }

    /**
     * Generate a neighbor portfolio by swap (40%), add (30%), or remove (30%).
     * The returned portfolio is always sorted for deterministic comparison.
     */
    private List<String> generateNeighbor(List<String> current, List<String> allCardIds, int maxCards, Random rng) {
        Set<String> currentSet = new HashSet<>(current);
        List<String> notInPortfolio = allCardIds.stream()
                .filter(id -> !currentSet.contains(id))
                .collect(Collectors.toList());

        double roll = rng.nextDouble();

        if (roll < 0.40 && !notInPortfolio.isEmpty() && !current.isEmpty()) {
            // Swap: replace one card with another not in portfolio
            List<String> neighbor = new ArrayList<>(current);
            int removeIdx = rng.nextInt(neighbor.size());
            neighbor.remove(removeIdx);
            String addCard = notInPortfolio.get(rng.nextInt(notInPortfolio.size()));
            neighbor.add(addCard);
            Collections.sort(neighbor);
            return neighbor;
        } else if (roll < 0.70 && current.size() < maxCards && !notInPortfolio.isEmpty()) {
            // Add a card
            List<String> neighbor = new ArrayList<>(current);
            String addCard = notInPortfolio.get(rng.nextInt(notInPortfolio.size()));
            neighbor.add(addCard);
            Collections.sort(neighbor);
            return neighbor;
        } else if (current.size() > 1) {
            // Remove a card
            List<String> neighbor = new ArrayList<>(current);
            int removeIdx = rng.nextInt(neighbor.size());
            neighbor.remove(removeIdx);
            Collections.sort(neighbor);
            return neighbor;
        }

        // Fallback: swap if possible, otherwise return copy
        if (!notInPortfolio.isEmpty() && !current.isEmpty()) {
            List<String> neighbor = new ArrayList<>(current);
            int removeIdx = rng.nextInt(neighbor.size());
            neighbor.remove(removeIdx);
            String addCard = notInPortfolio.get(rng.nextInt(notInPortfolio.size()));
            neighbor.add(addCard);
            Collections.sort(neighbor);
            return neighbor;
        }
        return new ArrayList<>(current);
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
