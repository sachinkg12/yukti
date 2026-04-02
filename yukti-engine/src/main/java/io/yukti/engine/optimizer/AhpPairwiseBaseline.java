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
 * Proper Saaty AHP baseline with a full 6×6 pairwise comparison matrix.
 *
 * <p>Unlike {@link AhpMcdmBaseline} (which uses fixed weights), this implementation
 * derives criteria weights from a proper pairwise comparison matrix following
 * Saaty's original AHP methodology [Saaty, 1980]. The six criteria are:
 *
 * <ol>
 *   <li>Earn Rate Quality (spend-weighted effective rate)</li>
 *   <li>Fee Burden (lower is better)</li>
 *   <li>Category Coverage (fraction of spend categories with above-base rates)</li>
 *   <li>Currency Alignment (how well the card's currency matches the goal)</li>
 *   <li>Cap Handling (robust to spending cap boundaries)</li>
 *   <li>Multi-Card Synergy (complementarity with other portfolio cards)</li>
 * </ol>
 *
 * <p>The pairwise comparison matrix expresses relative importance judgments
 * on Saaty's 1-9 scale. Weights are derived via the normalized geometric
 * mean (row geometric mean method), which closely approximates the principal
 * eigenvector. Consistency Ratio (CR) is checked at construction time
 * and must be &lt; 0.10.
 *
 * <p>Deterministic: same inputs always produce identical results.
 */
public final class AhpPairwiseBaseline implements Optimizer {

    private static final int N = 6; // number of criteria

    // Saaty Random Index for n=6
    private static final double RI_6 = 1.24;

    // Criteria names for documentation
    private static final String[] CRITERIA = {
        "EarnRate", "FeeBurden", "Coverage", "CurrencyAlign", "CapHandling", "Synergy"
    };

    /**
     * Default pairwise comparison matrix (Saaty 1-9 scale).
     *
     * Interpretation: a[i][j] = "how much more important is criterion i vs criterion j?"
     *   1 = equal, 3 = moderate, 5 = strong, 7 = very strong, 9 = extreme.
     *
     * Priority judgments:
     *   EarnRate is moderately-to-strongly more important than all others (it drives $/year).
     *   FeeBurden is moderately more important than Coverage, CurrencyAlign.
     *   CurrencyAlign is moderately more important than Coverage.
     *   CapHandling is slightly more important than Synergy.
     *   Synergy is the least important (individual card quality matters more).
     */
    private static final double[][] DEFAULT_MATRIX = {
        //            Earn   Fee   Cover  Curr   Cap   Synergy
        /* Earn  */ { 1,     3,    5,     4,     5,    7     },
        /* Fee   */ { 1./3,  1,    3,     2,     3,    5     },
        /* Cover */ { 1./5,  1./3, 1,     1./2,  1,    3     },
        /* Curr  */ { 1./4,  1./2, 2,     1,     2,    4     },
        /* Cap   */ { 1./5,  1./3, 1,     1./2,  1,    3     },
        /* Syner */ { 1./7,  1./5, 1./3,  1./4,  1./3, 1     },
    };

    private final double[] weights;
    private final double consistencyRatio;

    /** Default constructor with standard pairwise comparison matrix. */
    public AhpPairwiseBaseline() {
        this(DEFAULT_MATRIX);
    }

    /** Constructor with custom pairwise comparison matrix. */
    public AhpPairwiseBaseline(double[][] comparisonMatrix) {
        if (comparisonMatrix.length != N || comparisonMatrix[0].length != N) {
            throw new IllegalArgumentException("Comparison matrix must be " + N + "x" + N);
        }
        this.weights = computeWeights(comparisonMatrix);
        this.consistencyRatio = computeConsistencyRatio(comparisonMatrix, weights);
        if (consistencyRatio > 0.10) {
            throw new IllegalArgumentException(
                "Pairwise comparison matrix is inconsistent: CR = " + String.format("%.4f", consistencyRatio)
                + " (must be < 0.10). Review your judgments.");
        }
    }

    @Override
    public String id() {
        return "ahp-pairwise";
    }

    /** Returns derived weights and consistency ratio for reporting. */
    public double[] getWeights() {
        return Arrays.copyOf(weights, weights.length);
    }

    public double getConsistencyRatio() {
        return consistencyRatio;
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
        double maxFee = allCards.stream().mapToDouble(c -> c.annualFee().getAmount().doubleValue()).max().orElse(1);

        // Score each card using 6 AHP criteria
        List<ScoredCard> scored = new ArrayList<>();
        for (Card card : allCards) {
            if (card.annualFee().getAmount().doubleValue() > constraints.getMaxAnnualFee().getAmount().doubleValue()) {
                continue;
            }

            double[] criteria = new double[N];

            // C1: Earn rate quality (spend-weighted effective rate)
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
            criteria[0] = Math.min(earnScore / 0.06, 1.0);

            // C2: Fee burden (lower is better)
            double fee = card.annualFee().getAmount().doubleValue();
            criteria[1] = maxFee > 0 ? 1.0 - (fee / maxFee) : 1.0;

            // C3: Category coverage
            int coveredCategories = 0;
            for (Category cat : annualSpend.keySet()) {
                for (RewardsRule rule : card.rules()) {
                    if (rule.category() == cat && rule.rate().doubleValue() > 1.0) {
                        coveredCategories++;
                        break;
                    }
                }
            }
            criteria[2] = totalCategories > 0 ? (double) coveredCategories / totalCategories : 0;

            // C4: Currency alignment with goal
            RewardCurrency primaryCurrency = null;
            for (RewardsRule r : card.rules()) {
                primaryCurrency = r.currency();
                break;
            }
            if (primaryCurrency != null) {
                double cpp = cppResolver.usdPerPoint(primaryCurrency, userGoal).doubleValue();
                criteria[3] = Math.min(cpp / 0.02, 1.0);
                if (primaryCurrency.getType() == RewardCurrencyType.USD_CASH) {
                    criteria[3] = userGoal.getGoalType() == GoalType.CASHBACK ? 1.0 : 0.5;
                }
            }

            // C5: Cap handling robustness (penalize cards whose caps are likely hit by the profile)
            double capScore = 1.0;
            for (Map.Entry<Category, Money> entry : annualSpend.entrySet()) {
                Category cat = entry.getKey();
                double spend = entry.getValue().getAmount().doubleValue();
                for (RewardsRule rule : card.rules()) {
                    if (rule.category() == cat && rule.cap().isPresent()) {
                        double capAmt = rule.cap().get().getAmount().getAmount().doubleValue();
                        if (spend > capAmt) {
                            double capUtilization = capAmt / spend;
                            double weight = spend / totalSpend;
                            capScore -= weight * (1.0 - capUtilization) * 0.5;
                        }
                    }
                }
            }
            criteria[4] = Math.max(0, Math.min(1.0, capScore));

            // C6: Multi-card synergy (cards covering different categories get higher synergy potential)
            Set<Category> strongCategories = new HashSet<>();
            for (RewardsRule rule : card.rules()) {
                if (rule.rate().doubleValue() > 2.0 && annualSpend.containsKey(rule.category())) {
                    strongCategories.add(rule.category());
                }
            }
            // Specialists (few strong categories) have higher synergy in multi-card portfolios
            double specialization = strongCategories.isEmpty() ? 0 :
                1.0 - ((double) strongCategories.size() / Math.max(totalCategories, 1));
            criteria[5] = 0.5 + 0.5 * specialization; // Base 0.5 + up to 0.5 for specialists

            // Weighted sum using AHP-derived weights
            double ahpScore = 0;
            for (int i = 0; i < N; i++) {
                ahpScore += weights[i] * criteria[i];
            }

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
        String narrative = "[AHP-Pairwise] Portfolio: "
            + portfolioIds.stream().map(id -> cardById.get(id).displayName()).collect(Collectors.joining(", "))
            + ". Net: " + breakdown.getNet()
            + ". (CR=" + String.format("%.4f", consistencyRatio) + ")";

        return new OptimizationResult(portfolioIds, allocation, breakdown, evidence, narrative, List.of());
    }

    // --- AHP Weight Computation ---

    /**
     * Compute priority weights from pairwise comparison matrix using the
     * geometric mean method (closely approximates the principal eigenvector).
     */
    private static double[] computeWeights(double[][] matrix) {
        double[] geoMeans = new double[N];
        double totalGeoMean = 0;
        for (int i = 0; i < N; i++) {
            double product = 1.0;
            for (int j = 0; j < N; j++) {
                product *= matrix[i][j];
            }
            geoMeans[i] = Math.pow(product, 1.0 / N);
            totalGeoMean += geoMeans[i];
        }
        double[] w = new double[N];
        for (int i = 0; i < N; i++) {
            w[i] = geoMeans[i] / totalGeoMean;
        }
        return w;
    }

    /**
     * Compute Consistency Ratio (CR = CI / RI).
     * CI = (λ_max - n) / (n - 1), where λ_max is the maximum eigenvalue estimate.
     */
    private static double computeConsistencyRatio(double[][] matrix, double[] w) {
        // Compute Aw (matrix × weight vector)
        double[] aw = new double[N];
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                aw[i] += matrix[i][j] * w[j];
            }
        }
        // λ_max = average of (Aw_i / w_i)
        double lambdaMax = 0;
        for (int i = 0; i < N; i++) {
            lambdaMax += aw[i] / w[i];
        }
        lambdaMax /= N;

        double ci = (lambdaMax - N) / (N - 1);
        return ci / RI_6;
    }

    private record ScoredCard(String cardId, double score) {}
}
