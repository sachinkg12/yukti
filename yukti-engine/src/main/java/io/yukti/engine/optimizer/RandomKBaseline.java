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
 * Random selection baseline.
 * Selects K random cards. Deterministic via seed for reproducibility.
 * Establishes a lower bound for recommendation quality.
 */
public final class RandomKBaseline implements Optimizer {

    private static final long DEFAULT_SEED = 42L;

    private final long seed;

    public RandomKBaseline() {
        this(DEFAULT_SEED);
    }

    public RandomKBaseline(long seed) {
        this.seed = seed;
    }

    @Override
    public String id() {
        return "random-k";
    }

    @Override
    public OptimizationResult optimize(OptimizationRequest request, Catalog catalog) {
        SpendProfile profile = request.getSpendProfile();
        UserGoal userGoal = request.getUserGoal();
        UserConstraints constraints = request.getUserConstraints();
        if (profile.isEmpty()) {
            return OptimizationResult.empty("No spend provided.");
        }

        CppResolver cppResolver = new CppResolver(catalog);
        List<Card> allCards = new ArrayList<>(catalog.cards());
        int K = Math.min(3, constraints.getMaxCards());

        // Deterministic random selection: seed + profile hash for reproducibility
        long combinedSeed = seed + profile.hashCode() + userGoal.hashCode();
        Random rng = new Random(combinedSeed);
        Collections.shuffle(allCards, rng);
        List<String> selected = allCards.stream()
            .limit(K)
            .map(Card::id)
            .sorted()
            .toList();

        // Compute TRUE results via AllocationSolverV1_1
        return buildResult(selected, request, catalog, cppResolver, userGoal);
    }

    private OptimizationResult buildResult(List<String> portfolioIds, OptimizationRequest request,
                                           Catalog catalog, CppResolver cppResolver, UserGoal userGoal) {
        RewardModel rewardModel = new RewardModelV1();
        ValuationModel valuationModel = new ValuationModelV1(catalog);
        AllocationSolverV1_1 solver = new AllocationSolverV1_1();
        AllocationResult allocResult = solver.solve(request, catalog, portfolioIds, rewardModel, valuationModel);

        Map<Category, String> allocation = allocResult.allocationByCategory();
        ObjectiveBreakdown breakdown = new ObjectiveBreakdown(
            allocResult.earnedValueUsd(), allocResult.creditValueUsd(), allocResult.feesUsd());

        List<EvidenceBlock> evidence = new ArrayList<>(allocResult.evidenceBlocks());
        AssumptionEvidence assumptionEvidence = cppResolver.buildAssumptionEvidence(userGoal, null);
        evidence.add(new EvidenceBlock("ASSUMPTION", "valuation", "valuation", assumptionEvidence.content()));

        Map<String, Card> cardById = catalog.cards().stream().collect(Collectors.toMap(Card::id, c -> c));
        String narrative = "[Random-K] Portfolio: "
            + portfolioIds.stream().map(id -> cardById.get(id).displayName()).collect(Collectors.joining(", "))
            + ". Net: " + breakdown.getNet() + ".";

        return new OptimizationResult(portfolioIds, allocation, breakdown, evidence, narrative, List.of());
    }
}
