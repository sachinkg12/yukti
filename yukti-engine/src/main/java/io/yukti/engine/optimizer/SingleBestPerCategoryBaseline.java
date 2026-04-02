package io.yukti.engine.optimizer;

import io.yukti.core.api.*;
import io.yukti.core.domain.*;
import io.yukti.core.explainability.evidence.AssumptionEvidence;
import io.yukti.engine.reward.RewardModelV1;
import io.yukti.engine.valuation.CppResolver;
import io.yukti.engine.valuation.ValuationModelV1;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Naive baseline: for each spend category, pick the single card with highest goal-valued earn
 * (considering caps). Build portfolio from union of chosen cards; trim to maxCards and fee budget.
 * Uses AllocationSolverV1_1 for final allocation. Deterministic.
 */
public final class SingleBestPerCategoryBaseline implements Optimizer {

    @Override
    public String id() {
        return "single-best-per-category-v1";
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
        Map<String, Card> cardById = allCards.stream().collect(Collectors.toMap(Card::id, c -> c));

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

        int maxCards = Math.min(3, constraints.getMaxCards());

        // Step 1: For each category, pick the single card with highest goal-valued earn (considering caps)
        Map<Category, String> bestCardPerCategory = new EnumMap<>(Category.class);
        for (Map.Entry<Category, Money> e : annualSpend.entrySet()) {
            Category cat = e.getKey();
            Money spend = e.getValue();
            Money bestValue = Money.zeroUsd();
            String bestCardId = null;
            for (Card card : allCards) {
                for (RewardsRule rule : card.rules()) {
                    if (rule.category() != cat) continue;
                    BigDecimal cpp = cppResolver.centsPerUnit(rule.currency(), userGoal);
                    if (cpp.compareTo(BigDecimal.ZERO) <= 0) continue;
                    Money usable = usableSpend(spend, rule);
                    Money earnUsd = valueOfPoints(usable, rule.rate(), cpp);
                    if (earnUsd.getAmount().compareTo(bestValue.getAmount()) > 0) {
                        bestValue = earnUsd;
                        bestCardId = card.id();
                    }
                }
            }
            if (bestCardId != null) {
                bestCardPerCategory.put(cat, bestCardId);
            }
        }

        // Step 2: Build portfolio = union of chosen cards, ordered by total spend allocated
        Map<String, Money> spendByCard = new HashMap<>();
        for (Map.Entry<Category, String> e : bestCardPerCategory.entrySet()) {
            Money s = annualSpend.get(e.getKey());
            spendByCard.merge(e.getValue(), s, Money::add);
        }

        // Step 3: Trim to maxCards and enforce fee budget (keep cards with most spend first)
        FeeBudgetPolicy feeBudget = new FeeBudgetPolicy();
        List<String> portfolioIds = new ArrayList<>();
        for (Map.Entry<String, Money> e : spendByCard.entrySet().stream()
                .sorted(Map.Entry.<String, Money>comparingByValue((a, b) -> b.getAmount().compareTo(a.getAmount())))
                .toList()) {
            if (portfolioIds.size() >= maxCards) break;
            List<String> candidate = new ArrayList<>(portfolioIds);
            candidate.add(e.getKey());
            if (feeBudget.enforce(candidate, constraints, catalog)) {
                portfolioIds.add(e.getKey());
            }
        }

        // Step 4: Run AllocationSolverV1_1 for final allocation and breakdown
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
        AssumptionEvidence assumptionEvidence = cppResolver.buildAssumptionEvidence(userGoal, null);
        finalEvidence.add(new EvidenceBlock("ASSUMPTION", "valuation", "valuation", assumptionEvidence.content()));

        StringBuilder narrative = new StringBuilder();
        narrative.append("[Naive baseline] Portfolio: ").append(String.join(", ",
                portfolioIds.stream().map(id -> cardById.get(id).displayName()).toList())).append(". ");
        narrative.append("Earn: ").append(allocResult.earnedValueUsd()).append(", Credits: ")
                .append(allocResult.creditValueUsd()).append(", Fees: ").append(allocResult.feesUsd())
                .append(", Net: ").append(breakdown.getNet()).append(". ");
        for (EvidenceBlock eb : finalEvidence) {
            narrative.append(eb.getContent()).append(". ");
        }

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
        return Money.usd(cents.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
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
