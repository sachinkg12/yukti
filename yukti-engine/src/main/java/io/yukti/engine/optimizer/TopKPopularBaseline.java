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
 * Popularity-based recommendation baseline.
 * Selects K cards with highest "general appeal" regardless of user profile.
 * Non-personalized: every user gets the same cards (for a given goal).
 * Demonstrates that personalization matters for credit card recommendation.
 */
public final class TopKPopularBaseline implements Optimizer {

    @Override
    public String id() {
        return "top-k-popular";
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

        // Score each card by "popularity" — profile-independent
        List<ScoredCard> scored = new ArrayList<>();
        for (Card card : allCards) {
            double avgRate = 0.0;
            int ruleCount = 0;
            for (RewardsRule rule : card.rules()) {
                double cpp = cppResolver.centsPerUnit(rule.currency(), userGoal).doubleValue();
                if (cpp <= 0) continue;
                avgRate += rule.rate().doubleValue() * cpp / 100.0;
                ruleCount++;
            }
            if (ruleCount > 0) {
                avgRate /= ruleCount;
            }
            // Popularity heuristic: high avg rate, prefer no-fee cards
            double feeDiscount = card.annualFee().getAmount().doubleValue() == 0 ? 1.5 : 1.0;
            double popularity = avgRate * feeDiscount;
            scored.add(new ScoredCard(card.id(), popularity));
        }

        // Sort by popularity descending, take top K
        scored.sort(Comparator.comparingDouble(ScoredCard::score).reversed()
            .thenComparing(ScoredCard::cardId));
        List<String> topK = scored.stream()
            .limit(K)
            .map(ScoredCard::cardId)
            .toList();

        // Compute TRUE results via AllocationSolverV1_1
        return buildResult(topK, request, catalog, cppResolver, userGoal);
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
        String narrative = "[Top-K Popular] Portfolio: "
            + portfolioIds.stream().map(id -> cardById.get(id).displayName()).collect(Collectors.joining(", "))
            + ". Net: " + breakdown.getNet() + ".";

        return new OptimizationResult(portfolioIds, allocation, breakdown, evidence, narrative, List.of());
    }

    private record ScoredCard(String cardId, double score) {}
}
