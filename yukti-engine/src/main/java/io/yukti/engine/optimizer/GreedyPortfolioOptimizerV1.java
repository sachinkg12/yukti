package io.yukti.engine.optimizer;

import io.yukti.core.api.*;
import io.yukti.core.domain.*;
import io.yukti.core.explainability.evidence.AssumptionEvidence;
import io.yukti.engine.config.BenchRunConfig;
import io.yukti.engine.reward.RewardModelV1;
import io.yukti.engine.valuation.CppResolver;
import io.yukti.engine.valuation.ValuationModelV1;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Greedy portfolio optimizer: iteratively add cards maximizing marginal net value.
 * Uses AllocationSolverV1_1 (segment-based, cap-aware) for allocation. Deterministic.
 * This is an approximation; does not claim optimality. Documented in docs/spec.md.
 */
public final class GreedyPortfolioOptimizerV1 implements Optimizer {

    /** Tie-break epsilon (USD): marginals within this are treated as equal. Paper: TIE_BREAK_EPSILON_USD = 0.01.
     *  Internal objective computation uses full-precision BigDecimal; this epsilon is applied only at the
     *  comparison boundary (difference of marginals) so portfolio ranking is deterministic. */
    private static final BigDecimal TIE_THRESHOLD = new BigDecimal("0.01");
    public static final BigDecimal TIE_BREAK_EPSILON_USD = TIE_THRESHOLD;

    private final RewardModel rewardModel;
    private final ValuationModel valuationModel;

    public GreedyPortfolioOptimizerV1() {
        this(new RewardModelV1(), null);
    }

    public GreedyPortfolioOptimizerV1(RewardModel rewardModel, ValuationModel valuationModel) {
        this.rewardModel = rewardModel != null ? rewardModel : new RewardModelV1();
        this.valuationModel = valuationModel;
    }

    @Override
    public String id() {
        return "greedy-v1";
    }

    @Override
    public OptimizationResult optimize(OptimizationRequest request, Catalog catalog) {
        SpendProfile profile = request.getSpendProfile();
        UserGoal userGoal = request.getUserGoal();
        UserConstraints constraints = request.getUserConstraints();
        if (profile.isEmpty()) {
            return OptimizationResult.empty("No spend provided.");
        }

        Map<Category, Money> annualSpend = new EnumMap<>(Category.class);
        for (Category cat : Category.values()) {
            Money m = profile.annualSpend(cat);
            if (m.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                annualSpend.put(cat, m);
            }
        }
        if (annualSpend.isEmpty()) return OptimizationResult.empty("No spend provided.");

        // Phase-1: hard clamp to at most 3 cards (spec).
        int maxCards = Math.min(3, constraints.getMaxCards());

        ValuationModel val = valuationModel != null ? valuationModel : new ValuationModelV1(catalog);
        List<Card> allCards = new ArrayList<>(catalog.cards());
        if (!constraints.isAllowBusinessCards()) {
            allCards = filterBusinessCards(allCards, catalog);
        }
        allCards.sort(Comparator.comparing(Card::id));
        Map<String, Card> cardById = allCards.stream().collect(Collectors.toMap(Card::id, c -> c));

        FeeBudgetPolicy feeBudget = new FeeBudgetPolicy();
        AllocationSolverV1_1 solver = new AllocationSolverV1_1();
        List<String> portfolio = new ArrayList<>();
        Money totalFees = Money.zeroUsd();
        List<EvidenceBlock> evidence = new ArrayList<>();
        Map<Category, String> allocation = Map.of();
        Money currentNet = Money.zeroUsd();
        String stopReason = null;

        for (int step = 0; step < maxCards; step++) {
            AllocationResult currentResult = solver.solve(request, catalog, portfolio, rewardModel, val);
            currentNet = currentResult.netValueUsd();

            String bestCardId = null;
            Money bestMarginal = Money.zeroUsd();
            Money bestNewFees = totalFees;
            AllocationResult bestNewResult = null;

            for (Card card : allCards) {
                if (portfolio.contains(card.id())) continue;
                List<String> candidate = new ArrayList<>(portfolio);
                candidate.add(card.id());
                if (!feeBudget.enforce(candidate, constraints, catalog)) continue;

                Money newFees = totalFees.add(card.annualFee());
                AllocationResult newResult = solver.solve(request, catalog, candidate, rewardModel, val);
                Money marginal = newResult.netValueUsd().subtract(currentNet);

                if (bestCardId == null || compareCandidates(marginal, newFees, card.id(), bestMarginal, bestNewFees, bestCardId) > 0) {
                    bestMarginal = marginal;
                    bestCardId = card.id();
                    bestNewFees = newFees;
                    bestNewResult = newResult;
                }
            }

            if (bestCardId == null || bestMarginal.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                stopReason = "NO_POSITIVE_GAIN";
                evidence.add(toBlock(new PortfolioStopEvidence("NO_POSITIVE_GAIN: " +
                    (step == 0 ? "No card with positive marginal net value." : "No further positive marginal gain."))));
                break;
            }

            portfolio.add(bestCardId);
            totalFees = totalFees.add(cardById.get(bestCardId).annualFee());
            allocation = bestNewResult != null ? bestNewResult.allocationByCategory() : solver.solve(request, catalog, portfolio, rewardModel, val).allocationByCategory();
        }

        if (stopReason == null && portfolio.size() >= maxCards) {
            stopReason = "MAX_CARDS_REACHED";
            evidence.add(toBlock(new PortfolioStopEvidence("MAX_CARDS_REACHED: Reached maxCards=" + maxCards)));
        }

        AllocationResult finalResult = solver.solve(request, catalog, portfolio, rewardModel, val);
        allocation = finalResult.allocationByCategory();

        evidence.addAll(finalResult.evidenceBlocks().stream().filter(eb -> "WINNER_BY_CATEGORY".equals(eb.getType())).toList());
        evidence.addAll(finalResult.evidenceBlocks().stream().filter(eb -> "CAP_HIT".equals(eb.getType())).toList());

        Money earnValue = finalResult.earnedValueUsd();
        Money creditsValue = finalResult.creditValueUsd();
        Money fees = finalResult.feesUsd();

        for (String cid : portfolio) {
            Card c = cardById.get(cid);
            List<String> withoutCard = new ArrayList<>(portfolio);
            withoutCard.remove(cid);
            AllocationResult withoutResult = solver.solve(request, catalog, withoutCard, rewardModel, val);
            Money incrementalEarn = finalResult.earnedValueUsd().subtract(withoutResult.earnedValueUsd());
            Money netDelta = incrementalEarn.add(BenchRunConfig.effectiveCredits(c)).subtract(c.annualFee());
            evidence.add(toBlock(new FeeBreakEvenEvidence(cid, c.annualFee(), c.statementCreditsAnnual(), netDelta)));
        }

        ObjectiveBreakdown breakdown = new ObjectiveBreakdown(earnValue, creditsValue, fees);

        StringBuilder narrative = new StringBuilder();
        narrative.append("Portfolio: ").append(String.join(", ",
            portfolio.stream().map(id -> cardById.get(id).displayName()).toList())).append(". ");
        narrative.append("Earn: ").append(earnValue).append(", Credits: ").append(creditsValue)
            .append(", Fees: ").append(fees).append(", Net: ").append(breakdown.getNet()).append(". ");
        for (EvidenceBlock eb : evidence) narrative.append(eb.getContent()).append(". ");

        CppResolver cppResolver = new CppResolver(catalog);
        AssumptionEvidence assumptionEvidence = cppResolver.buildAssumptionEvidence(userGoal, null);
        evidence.add(new EvidenceBlock("ASSUMPTION", "valuation", "valuation", assumptionEvidence.content()));

        return new OptimizationResult(portfolio, allocation, breakdown, evidence, narrative.toString(), List.of());
    }

    private List<Card> filterBusinessCards(List<Card> cards, Catalog catalog) {
        return new ArrayList<>(cards.stream().filter(c -> !isBusinessCard(c, catalog)).toList());
    }

    private boolean isBusinessCard(Card card, Catalog catalog) {
        return false;
    }

    /** Compare candidates: positive if a is better than b. Tie-break: (1) marginal, (2) lower fees, (3) lexicographically smallest cardId. */
    private int compareCandidates(Money marginalA, Money feesA, String cardIdA, Money marginalB, Money feesB, String cardIdB) {
        int cmp = marginalA.getAmount().compareTo(marginalB.getAmount());
        if (cmp != 0) return cmp;
        if (marginalA.getAmount().subtract(marginalB.getAmount()).abs().compareTo(TIE_THRESHOLD) < 0) {
            cmp = feesB.getAmount().compareTo(feesA.getAmount());
            if (cmp != 0) return cmp;
        }
        return cardIdB.compareTo(cardIdA);
    }

    private EvidenceBlock toBlock(PortfolioStopEvidence e) {
        return new EvidenceBlock("PORTFOLIO_STOP", "", "", e.reason());
    }

    private EvidenceBlock toBlock(FeeBreakEvenEvidence e) {
        return new EvidenceBlock("FEE_BREAK_EVEN", e.cardId(), "",
            String.format(java.util.Locale.ROOT, "%s: fee %s, credits %s, net delta %s", e.cardId(), e.feeUSD(), e.creditsAssumedUSD(), e.netDeltaUSD()));
    }
}
