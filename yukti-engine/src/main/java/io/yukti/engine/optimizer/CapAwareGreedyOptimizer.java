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
 * Cap-aware greedy optimizer v1.
 * Deterministic: same inputs + catalog -> same outputs.
 * v1.1: Enforces fee budget via FeeBudgetPolicy; uses AllocationSolverV1_1 for segment-based allocation.
 */
public final class CapAwareGreedyOptimizer implements Optimizer {
    @Override
    public String id() {
        return "cap-aware-greedy-v1";
    }

    @Override
    public OptimizationResult optimize(OptimizationRequest request, Catalog catalog) {
        SpendProfile profile = request.getSpendProfile();
        UserGoal userGoal = request.getUserGoal();
        UserConstraints constraints = request.getUserConstraints();
        if (profile.isEmpty()) {
            return OptimizationResult.empty("No spend provided.");
        }

        CppResolver val = new CppResolver(catalog);
        List<Card> allCards = new ArrayList<>(catalog.cards());
        Map<String, Card> cardById = allCards.stream().collect(Collectors.toMap(Card::id, c -> c));

        Map<Category, Money> annualSpend = new EnumMap<>(Category.class);
        for (Category cat : Category.values()) {
            Money m = profile.annualSpend(cat);
            if (m.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                annualSpend.put(cat, m);
            }
        }
        if (annualSpend.isEmpty()) return OptimizationResult.empty("No spend provided.");

        int maxCards = Math.min(3, constraints.getMaxCards());

        Map<Category, String> allocation = new EnumMap<>(Category.class);
        for (Category cat : Category.values()) {
            Money spend = annualSpend.get(cat);
            if (spend == null || spend.getAmount().compareTo(BigDecimal.ZERO) <= 0) continue;

            Money bestValue = Money.zeroUsd();
            String bestCardId = null;
            for (Card card : allCards) {
                for (RewardsRule rule : card.rules()) {
                    if (rule.category() != cat) continue;
                    BigDecimal cpp = val.centsPerUnit(rule.currency(), userGoal);
                    if (cpp.compareTo(BigDecimal.ZERO) <= 0) continue;
                    Money usable = usableSpend(spend, rule);
                    Money earnUsd = valueOfPoints(usable, rule.rate(), cpp);
                    if (earnUsd.getAmount().compareTo(bestValue.getAmount()) > 0) {
                        bestValue = earnUsd;
                        bestCardId = card.id();
                    }
                }
            }
            if (bestCardId != null) allocation.put(cat, bestCardId);
        }

        Map<String, Money> spendByCard = new HashMap<>();
        for (Map.Entry<Category, String> e : allocation.entrySet()) {
            Money s = annualSpend.get(e.getKey());
            spendByCard.merge(e.getValue(), s, Money::add);
        }

        FeeBudgetPolicy feeBudget = new FeeBudgetPolicy();
        List<String> portfolioIds = new ArrayList<>();
        for (Map.Entry<String, Money> e : spendByCard.entrySet().stream()
            .sorted(Map.Entry.<String, Money>comparingByValue((a, b) -> b.getAmount().compareTo(a.getAmount()))
                .thenComparing((e1, e2) -> {
                    Card c1 = cardById.get(e1.getKey());
                    Card c2 = cardById.get(e2.getKey());
                    Money f1 = c1 != null ? c1.annualFee() : Money.zeroUsd();
                    Money f2 = c2 != null ? c2.annualFee() : Money.zeroUsd();
                    return f1.getAmount().compareTo(f2.getAmount());
                })
                .thenComparing(Map.Entry.comparingByKey()))
            .toList()) {
            if (portfolioIds.size() >= maxCards) break;
            List<String> candidate = new ArrayList<>(portfolioIds);
            candidate.add(e.getKey());
            if (feeBudget.enforce(candidate, constraints, catalog)) {
                portfolioIds.add(e.getKey());
            }
        }

        RewardModel rewardModel = new RewardModelV1();
        ValuationModel valuationModel = new ValuationModelV1(catalog);
        AllocationSolverV1_1 solver = new AllocationSolverV1_1();
        AllocationResult allocResult = solver.solve(request, catalog, portfolioIds, rewardModel, valuationModel);

        Map<Category, String> finalAllocation = allocResult.allocationByCategory();
        ObjectiveBreakdown breakdown = new ObjectiveBreakdown(
            allocResult.earnedValueUsd(),
            allocResult.creditValueUsd(),
            allocResult.feesUsd());

        List<EvidenceBlock> finalEvidence = new ArrayList<>(allocResult.evidenceBlocks());
        AssumptionEvidence assumptionEvidence = val.buildAssumptionEvidence(userGoal, null);
        finalEvidence.add(new EvidenceBlock("ASSUMPTION", "valuation", "valuation", assumptionEvidence.content()));

        StringBuilder narrative = new StringBuilder();
        narrative.append("Portfolio: ").append(String.join(", ",
            portfolioIds.stream().map(id -> cardById.get(id).displayName()).toList())).append(". ");
        narrative.append("Earn: ").append(allocResult.earnedValueUsd()).append(", Credits: ").append(allocResult.creditValueUsd())
            .append(", Fees: ").append(allocResult.feesUsd()).append(", Net: ").append(breakdown.getNet()).append(". ");
        for (EvidenceBlock eb : finalEvidence) narrative.append(eb.getContent()).append(". ");

        List<String> switchingNotes = buildSwitchingNotesFromSegments(allocResult.allocationPlan(), cardById);

        return new OptimizationResult(
            portfolioIds,
            finalAllocation,
            breakdown,
            finalEvidence,
            narrative.toString(),
            switchingNotes
        );
    }

    private Money usableSpend(Money spend, RewardsRule rule) {
        Optional<Cap> cap = rule.cap();
        if (cap.isEmpty()) return spend;
        Money capAmount = cap.get().getAmount();
        if (spend.getAmount().compareTo(capAmount.getAmount()) <= 0) return spend;
        return capAmount;
    }

    private Money valueOfPoints(Money spend, BigDecimal rate, BigDecimal cpp) {
        BigDecimal points = spend.getAmount().multiply(rate);
        BigDecimal cents = points.multiply(cpp);
        return Money.usd(cents.divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP));
    }

    private List<String> buildSwitchingNotesFromSegments(AllocationPlan plan, Map<String, Card> cardById) {
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
