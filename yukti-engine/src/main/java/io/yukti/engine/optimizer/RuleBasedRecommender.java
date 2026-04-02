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
 * Rule-based recommender baseline.
 *
 * <p>Implements expert-authored heuristic rules commonly used by financial
 * comparison sites: (1) identify the user's top spending category, (2) find
 * the card with the highest rate in that category, (3) add a general-purpose
 * card if budget allows, (4) add a no-fee card for remaining categories.
 *
 * <p>This models the "editorial pick" approach used by NerdWallet, CardPointers,
 * and similar services. Rules are deterministic and goal-aware but cannot
 * model piecewise caps, card interactions, or joint allocation.
 *
 * <p>Deterministic: same inputs always produce identical results.
 */
public final class RuleBasedRecommender implements Optimizer {

    @Override
    public String id() {
        return "rule-based";
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
        int maxCards = Math.min(3, constraints.getMaxCards());
        double feeBudget = constraints.getMaxAnnualFee().getAmount().doubleValue();

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

        // Rule 1: Find top spending category
        Category topCategory = annualSpend.entrySet().stream()
            .max(Comparator.comparing(e -> e.getValue().getAmount()))
            .map(Map.Entry::getKey)
            .orElse(Category.OTHER);

        // Rule 2: Best card for top category (highest rate × cpp, fee-aware)
        List<String> selected = new ArrayList<>();
        double usedFee = 0.0;

        String topCatCard = findBestCardForCategory(topCategory, allCards, userGoal, cppResolver, feeBudget - usedFee, selected);
        if (topCatCard != null) {
            selected.add(topCatCard);
            usedFee += feeFor(topCatCard, allCards);
        }

        // Rule 3: If goal is FLEX_POINTS or PROGRAM_POINTS, add a transferable points card
        if (selected.size() < maxCards && userGoal.getGoalType() != GoalType.CASHBACK) {
            String pointsCard = findBestPointsCard(allCards, userGoal, cppResolver, feeBudget - usedFee, selected);
            if (pointsCard != null) {
                selected.add(pointsCard);
                usedFee += feeFor(pointsCard, allCards);
            }
        }

        // Rule 4: Add best no-fee card for remaining categories
        if (selected.size() < maxCards) {
            String noFeeCard = findBestNoFeeCard(allCards, annualSpend, userGoal, cppResolver, selected);
            if (noFeeCard != null) {
                selected.add(noFeeCard);
            }
        }

        // Rule 5: If still room, add best overall card not yet selected
        if (selected.size() < maxCards) {
            String generalCard = findBestGeneralCard(allCards, annualSpend, userGoal, cppResolver, feeBudget - usedFee, selected);
            if (generalCard != null) {
                selected.add(generalCard);
            }
        }

        if (selected.isEmpty()) {
            return OptimizationResult.empty("No cards match rules.");
        }

        return buildResult(selected, request, catalog, cppResolver, userGoal);
    }

    private String findBestCardForCategory(Category category, List<Card> cards, UserGoal goal,
                                            CppResolver cppResolver, double remainingBudget,
                                            List<String> exclude) {
        String bestId = null;
        double bestScore = -1;
        for (Card card : cards) {
            if (exclude.contains(card.id())) continue;
            if (card.annualFee().getAmount().doubleValue() > remainingBudget) continue;
            for (RewardsRule rule : card.rules()) {
                if (rule.category() != category) continue;
                double cpp = cppResolver.centsPerUnit(rule.currency(), goal).doubleValue();
                if (cpp <= 0) continue;
                double score = rule.rate().doubleValue() * cpp / 100.0;
                if (score > bestScore) {
                    bestScore = score;
                    bestId = card.id();
                }
            }
        }
        return bestId;
    }

    private String findBestPointsCard(List<Card> cards, UserGoal goal, CppResolver cppResolver,
                                       double remainingBudget, List<String> exclude) {
        String bestId = null;
        double bestCpp = -1;
        for (Card card : cards) {
            if (exclude.contains(card.id())) continue;
            if (card.annualFee().getAmount().doubleValue() > remainingBudget) continue;
            RewardCurrency currency = null;
            for (RewardsRule r : card.rules()) {
                currency = r.currency();
                break;
            }
            if (currency == null || currency.getType() == RewardCurrencyType.USD_CASH) continue;
            double cpp = cppResolver.usdPerPoint(currency, goal).doubleValue();
            if (cpp > bestCpp) {
                bestCpp = cpp;
                bestId = card.id();
            }
        }
        return bestId;
    }

    private String findBestNoFeeCard(List<Card> cards, Map<Category, Money> annualSpend,
                                      UserGoal goal, CppResolver cppResolver, List<String> exclude) {
        String bestId = null;
        double bestScore = -1;
        for (Card card : cards) {
            if (exclude.contains(card.id())) continue;
            if (card.annualFee().getAmount().doubleValue() > 0) continue;
            double score = scoreCardForSpend(card, annualSpend, goal, cppResolver);
            if (score > bestScore) {
                bestScore = score;
                bestId = card.id();
            }
        }
        return bestId;
    }

    private String findBestGeneralCard(List<Card> cards, Map<Category, Money> annualSpend,
                                        UserGoal goal, CppResolver cppResolver,
                                        double remainingBudget, List<String> exclude) {
        String bestId = null;
        double bestScore = -1;
        for (Card card : cards) {
            if (exclude.contains(card.id())) continue;
            if (card.annualFee().getAmount().doubleValue() > remainingBudget) continue;
            double score = scoreCardForSpend(card, annualSpend, goal, cppResolver)
                - card.annualFee().getAmount().doubleValue();
            if (score > bestScore) {
                bestScore = score;
                bestId = card.id();
            }
        }
        return bestId;
    }

    private double scoreCardForSpend(Card card, Map<Category, Money> annualSpend,
                                      UserGoal goal, CppResolver cppResolver) {
        double score = 0;
        for (Map.Entry<Category, Money> entry : annualSpend.entrySet()) {
            Category cat = entry.getKey();
            double spend = entry.getValue().getAmount().doubleValue();
            for (RewardsRule rule : card.rules()) {
                if (rule.category() != cat) continue;
                double cpp = cppResolver.centsPerUnit(rule.currency(), goal).doubleValue();
                if (cpp <= 0) continue;
                score += spend * rule.rate().doubleValue() * cpp / 100.0;
                break;
            }
        }
        return score;
    }

    private double feeFor(String cardId, List<Card> cards) {
        for (Card c : cards) {
            if (c.id().equals(cardId)) return c.annualFee().getAmount().doubleValue();
        }
        return 0;
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
        String narrative = "[Rule-Based] Portfolio: "
            + portfolioIds.stream().map(id -> cardById.get(id).displayName()).collect(Collectors.joining(", "))
            + ". Net: " + breakdown.getNet() + ".";

        return new OptimizationResult(portfolioIds, allocation, breakdown, evidence, narrative, List.of());
    }
}
