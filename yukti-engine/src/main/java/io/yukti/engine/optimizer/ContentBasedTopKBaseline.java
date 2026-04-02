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
 * Content-based recommendation baseline.
 * Scores cards INDEPENDENTLY based on profile-item attribute similarity.
 * Ignores piecewise caps and card interactions during selection.
 * Evaluates true performance via AllocationSolverV1_1 post-selection.
 * Demonstrates why joint optimization (MILP) matters.
 */
public final class ContentBasedTopKBaseline implements Optimizer {

    @Override
    public String id() {
        return "content-based-top3";
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

        Map<Category, Money> annualSpend = new EnumMap<>(Category.class);
        for (Category cat : Category.values()) {
            Money m = profile.annualSpend(cat);
            if (m.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                annualSpend.put(cat, m);
            }
        }
        if (annualSpend.isEmpty()) {
            return OptimizationResult.empty("No spend provided.");
        }

        // Score each card INDEPENDENTLY — ignoring caps and card interactions
        List<ScoredCard> scored = new ArrayList<>();
        for (Card card : allCards) {
            double score = 0.0;
            for (Map.Entry<Category, Money> entry : annualSpend.entrySet()) {
                Category cat = entry.getKey();
                double spend = entry.getValue().getAmount().doubleValue();
                double bestRate = 0.0;
                double bestCpp = 0.0;
                for (RewardsRule rule : card.rules()) {
                    if (rule.category() != cat) continue;
                    double cpp = cppResolver.centsPerUnit(rule.currency(), userGoal).doubleValue();
                    if (cpp <= 0) continue;
                    double rate = rule.rate().doubleValue();
                    // Use FULL multiplier on ALL spend (ignores cap — the key flaw)
                    if (rate * cpp > bestRate * bestCpp) {
                        bestRate = rate;
                        bestCpp = cpp;
                    }
                }
                score += spend * bestRate * bestCpp / 100.0;
            }
            score -= card.annualFee().getAmount().doubleValue();
            scored.add(new ScoredCard(card.id(), score));
        }

        // Sort by score descending, take top K
        scored.sort(Comparator.comparingDouble(ScoredCard::score).reversed());
        List<String> topK = scored.stream()
            .limit(K)
            .map(ScoredCard::cardId)
            .toList();

        // Compute TRUE results via AllocationSolverV1_1 (cap-aware)
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
        String narrative = "[Content-Based Top-K] Portfolio: "
            + portfolioIds.stream().map(id -> cardById.get(id).displayName()).collect(Collectors.joining(", "))
            + ". Net: " + breakdown.getNet() + ".";

        return new OptimizationResult(portfolioIds, allocation, breakdown, evidence, narrative, List.of());
    }

    private record ScoredCard(String cardId, double score) {}
}
