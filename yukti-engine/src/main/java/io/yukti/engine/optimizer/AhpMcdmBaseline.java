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
 * AHP/MCDM (Analytic Hierarchy Process / Multi-Criteria Decision Making) baseline.
 *
 * <p>Implements a simplified AHP-style scoring: each card is scored as a weighted
 * sum of criteria (earn rate quality, fee burden, category coverage, currency
 * alignment). Criteria weights are derived from standard AHP pairwise comparison
 * ratios. Cards are ranked independently and the top K are selected.
 *
 * <p>This models the approach described in Saaty [9] and commonly used in
 * financial product recommendation (e.g., credit card comparison sites that
 * use multi-criteria scoring). The key limitation vs MILP: AHP scores cards
 * independently and cannot model piecewise caps or card interactions.
 *
 * <p>Deterministic: same inputs always produce identical results.
 */
public final class AhpMcdmBaseline implements Optimizer {

    // AHP criteria weights (derived from pairwise comparison matrix)
    // Earn rate > Currency alignment > Fee burden > Category coverage
    public static final double DEFAULT_W_EARN_RATE = 0.45;
    public static final double DEFAULT_W_FEE = 0.20;
    public static final double DEFAULT_W_COVERAGE = 0.15;
    public static final double DEFAULT_W_CURRENCY = 0.20;

    private final double wEarnRate;
    private final double wFee;
    private final double wCoverage;
    private final double wCurrency;
    private final String idSuffix;

    /** Default constructor with standard AHP weights. */
    public AhpMcdmBaseline() {
        this(DEFAULT_W_EARN_RATE, DEFAULT_W_FEE, DEFAULT_W_COVERAGE, DEFAULT_W_CURRENCY);
    }

    /**
     * Configurable-weight constructor for sensitivity analysis.
     * Weights must be non-negative and sum to 1.0 (±0.01 tolerance).
     */
    public AhpMcdmBaseline(double wEarnRate, double wFee, double wCoverage, double wCurrency) {
        double sum = wEarnRate + wFee + wCoverage + wCurrency;
        if (Math.abs(sum - 1.0) > 0.01) {
            throw new IllegalArgumentException("AHP weights must sum to 1.0, got " + sum);
        }
        this.wEarnRate = wEarnRate;
        this.wFee = wFee;
        this.wCoverage = wCoverage;
        this.wCurrency = wCurrency;
        this.idSuffix = "";
    }

    /**
     * Configurable-weight constructor with custom ID suffix for sweep runners.
     */
    public AhpMcdmBaseline(double wEarnRate, double wFee, double wCoverage, double wCurrency, String idSuffix) {
        double sum = wEarnRate + wFee + wCoverage + wCurrency;
        if (Math.abs(sum - 1.0) > 0.01) {
            throw new IllegalArgumentException("AHP weights must sum to 1.0, got " + sum);
        }
        this.wEarnRate = wEarnRate;
        this.wFee = wFee;
        this.wCoverage = wCoverage;
        this.wCurrency = wCurrency;
        this.idSuffix = idSuffix != null ? idSuffix : "";
    }

    /** Returns the current weight configuration for reporting. */
    public double[] getWeights() {
        return new double[]{wEarnRate, wFee, wCoverage, wCurrency};
    }

    @Override
    public String id() {
        return "ahp-mcdm" + idSuffix;
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

        double totalSpend = annualSpend.values().stream()
            .mapToDouble(m -> m.getAmount().doubleValue()).sum();
        int totalCategories = annualSpend.size();

        // Compute min/max fee for normalization
        double maxFee = allCards.stream().mapToDouble(c -> c.annualFee().getAmount().doubleValue()).max().orElse(1);

        // Score each card using AHP criteria
        List<ScoredCard> scored = new ArrayList<>();
        for (Card card : allCards) {
            // Skip cards that exceed fee budget
            if (card.annualFee().getAmount().doubleValue() > constraints.getMaxAnnualFee().getAmount().doubleValue()) {
                continue;
            }

            // Criterion 1: Weighted earn rate (spend-weighted average rate × cpp)
            double earnScore = 0.0;
            for (Map.Entry<Category, Money> entry : annualSpend.entrySet()) {
                Category cat = entry.getKey();
                double spend = entry.getValue().getAmount().doubleValue();
                double weight = spend / totalSpend;
                double bestRateUsd = 0.0;
                for (RewardsRule rule : card.rules()) {
                    if (rule.category() != cat) continue;
                    double cpp = cppResolver.centsPerUnit(rule.currency(), userGoal).doubleValue();
                    if (cpp <= 0) continue;
                    double rateUsd = rule.rate().doubleValue() * cpp / 100.0;
                    if (rateUsd > bestRateUsd) bestRateUsd = rateUsd;
                }
                earnScore += weight * bestRateUsd;
            }
            // Normalize earn score (typical range 0-0.06)
            double earnNorm = Math.min(earnScore / 0.06, 1.0);

            // Criterion 2: Fee burden (lower is better)
            double fee = card.annualFee().getAmount().doubleValue();
            double feeNorm = maxFee > 0 ? 1.0 - (fee / maxFee) : 1.0;

            // Criterion 3: Category coverage (fraction of spend categories covered)
            int coveredCategories = 0;
            for (Category cat : annualSpend.keySet()) {
                for (RewardsRule rule : card.rules()) {
                    if (rule.category() == cat && rule.rate().doubleValue() > 1.0) {
                        coveredCategories++;
                        break;
                    }
                }
            }
            double coverageNorm = totalCategories > 0 ? (double) coveredCategories / totalCategories : 0;

            // Criterion 4: Currency alignment with goal
            double currencyScore = 0.0;
            RewardCurrency primaryCurrency = null;
            for (RewardsRule r : card.rules()) {
                primaryCurrency = r.currency();
                break;
            }
            if (primaryCurrency != null) {
                double cpp = cppResolver.usdPerPoint(primaryCurrency, userGoal).doubleValue();
                // Higher cpp = better alignment
                currencyScore = Math.min(cpp / 0.02, 1.0); // 0.02 USD/pt is the top
                if (primaryCurrency.getType() == RewardCurrencyType.USD_CASH) {
                    // Cash is always aligned with CASHBACK goal
                    currencyScore = userGoal.getGoalType() == GoalType.CASHBACK ? 1.0 : 0.5;
                }
            }

            // AHP weighted sum
            double ahpScore = wEarnRate * earnNorm
                + wFee * feeNorm
                + wCoverage * coverageNorm
                + wCurrency * currencyScore;

            scored.add(new ScoredCard(card.id(), ahpScore));
        }

        // Sort by AHP score descending, tie-break by card id
        scored.sort(Comparator.comparingDouble(ScoredCard::score).reversed()
            .thenComparing(ScoredCard::cardId));
        List<String> topK = scored.stream()
            .limit(K)
            .map(ScoredCard::cardId)
            .toList();

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
        String narrative = "[AHP/MCDM] Portfolio: "
            + portfolioIds.stream().map(id -> cardById.get(id).displayName()).collect(Collectors.joining(", "))
            + ". Net: " + breakdown.getNet() + ".";

        return new OptimizationResult(portfolioIds, allocation, breakdown, evidence, narrative, List.of());
    }

    private record ScoredCard(String cardId, double score) {}
}
