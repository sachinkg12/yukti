package io.yukti.engine.optimizer;

import io.yukti.core.api.Card;
import io.yukti.core.api.Catalog;
import io.yukti.core.api.RewardsRule;
import io.yukti.core.domain.*;
import io.yukti.core.explainability.evidence.AssumptionEvidence;
import io.yukti.engine.config.BenchRunConfig;
import io.yukti.engine.valuation.CppResolver;
import io.yukti.explain.core.claims.ClaimTypeRules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Derives rich evidence blocks from a solved {@link MilpSolution}.
 *
 * <p>Post-solve analysis: reads variable values (y, x, w, z) and produces
 * all evidence types consumed by the explanation pipeline. Evidence format
 * matches what {@code AllocationSolverV1_1} produces, ensuring OCP:
 * the downstream {@code ExplanationServiceV1}, {@code EvidenceGraphBuilder},
 * and {@code ClaimVerifier} work unchanged.
 *
 * <p>Evidence types emitted:
 * <ol>
 *   <li>WINNER_BY_CATEGORY — winner with runner-up and USD delta</li>
 *   <li>CAP_HIT — capped portion hit with overflow and fallback card</li>
 *   <li>ALLOCATION_SEGMENT — per-category allocation when split across cards</li>
 *   <li>FEE_BREAK_EVEN — per-card earn, credits, fee, net</li>
 *   <li>PORTFOLIO_STOP — binding constraint (max_cards, fee_budget, no_marginal_benefit)</li>
 *   <li>ASSUMPTION — valuation parameters (goal, cpp, penalties)</li>
 *   <li>RESULT_BREAKDOWN — total earn, credits, fees, net</li>
 *   <li>PORTFOLIO_SUMMARY — selected card IDs</li>
 * </ol>
 */
public final class MilpSolutionAnalyzer {

    private static final double CAP_HIT_THRESHOLD = 0.999;
    private static final double ALLOCATION_THRESHOLD = 0.01;
    private static final BigDecimal FALLBACK_CASH = new BigDecimal("0.01");
    private static final BigDecimal FALLBACK_POINTS = BigDecimal.ONE;

    private final Catalog catalog;
    private final Map<Category, BigDecimal> annualSpend;
    private final UserGoal goal;
    private final UserConstraints constraints;
    private final CppResolver cppResolver;
    private final Map<String, Card> cardById;

    public MilpSolutionAnalyzer(Catalog catalog, SpendProfile profile,
                                UserGoal goal, UserConstraints constraints) {
        this.catalog = Objects.requireNonNull(catalog);
        this.goal = Objects.requireNonNull(goal);
        this.constraints = Objects.requireNonNull(constraints);
        this.cppResolver = new CppResolver(catalog);

        this.annualSpend = new EnumMap<>(Category.class);
        for (Category cat : Category.values()) {
            BigDecimal spend = profile.annualSpend(cat).getAmount();
            if (spend.compareTo(BigDecimal.ZERO) > 0) {
                annualSpend.put(cat, spend);
            }
        }

        this.cardById = new LinkedHashMap<>();
        for (Card card : catalog.cards()) {
            cardById.put(card.id(), card);
        }
    }

    public List<EvidenceBlock> deriveEvidence(MilpSolution solution) {
        List<EvidenceBlock> evidence = new ArrayList<>();

        addWinnerByCategoryEvidence(solution, evidence);
        addCapHitEvidence(solution, evidence);
        addSegmentAllocationEvidence(solution, evidence);
        addFeeBreakEvenEvidence(solution, evidence);
        addPortfolioStopEvidence(solution, evidence);
        addAssumptionEvidence(evidence);
        addResultBreakdownEvidence(solution, evidence);
        addPortfolioSummaryEvidence(solution, evidence);

        return evidence;
    }

    private void addWinnerByCategoryEvidence(MilpSolution solution, List<EvidenceBlock> evidence) {
        for (Category cat : Category.values()) {
            if (!annualSpend.containsKey(cat)) continue;

            String winner = findWinner(cat, solution);
            if (winner == null) continue;

            String runnerUp = findRunnerUp(cat, solution, winner);
            double winnerValue = computeCategoryValue(winner, cat, solution);
            double runnerUpValue = runnerUp != null ? computeHypotheticalValue(runnerUp, cat) : 0;
            double delta = winnerValue - runnerUpValue;

            String content = runnerUp != null
                ? String.format("%s wins %s (delta $%.2f over %s)", winner, cat.name(), delta, runnerUp)
                : String.format("%s wins %s (no runner-up)", winner, cat.name());

            evidence.add(new EvidenceBlock("WINNER_BY_CATEGORY", winner, cat.name(), content));
        }
    }

    private void addCapHitEvidence(MilpSolution solution, List<EvidenceBlock> evidence) {
        for (String cardId : solution.selectedCardIds()) {
            Card card = cardById.get(cardId);
            if (card == null) continue;

            for (Category cat : annualSpend.keySet()) {
                RewardsRule rule = bestRuleForCategory(card, cat);
                if (rule == null || rule.cap().isEmpty()) continue;

                double capUsd = rule.cap().get().getAmount().getAmount().doubleValue();
                double wValue = solution.w(cardId, cat);

                if (wValue >= capUsd * CAP_HIT_THRESHOLD) {
                    double overflow = solution.z(cardId, cat);
                    String fallback = findFallbackCard(cat, cardId, solution);

                    String content = String.format(
                        "Cap hit: %s %s cap=%s applied=%s remainder=%s fallback=%s",
                        cardId, cat.name(),
                        Money.usd(capUsd), Money.usd(wValue),
                        Money.usd(overflow),
                        fallback != null ? fallback : "N/A");

                    evidence.add(new EvidenceBlock("CAP_HIT", cardId, cat.name(), content));
                }
            }
        }
    }

    private void addSegmentAllocationEvidence(MilpSolution solution, List<EvidenceBlock> evidence) {
        for (Category cat : annualSpend.keySet()) {
            List<String> cardsInCategory = new ArrayList<>();
            for (String cardId : solution.selectedCardIds()) {
                if (solution.x(cardId, cat) > ALLOCATION_THRESHOLD) {
                    cardsInCategory.add(cardId);
                }
            }

            // Emit when multiple cards serve the category OR when any card hits its cap.
            // A cap hit produces CAP_HIT evidence; CAP_SWITCH claims require both CAP_HIT
            // and ALLOCATION_SEGMENT, so we must emit the latter even for single-card categories.
            boolean hasCapHit = false;
            for (String cardId : cardsInCategory) {
                Card card = cardById.get(cardId);
                if (card != null) {
                    RewardsRule rule = bestRuleForCategory(card, cat);
                    if (rule != null && rule.cap().isPresent()) {
                        double capUsd = rule.cap().get().getAmount().getAmount().doubleValue();
                        double wValue = solution.w(cardId, cat);
                        if (wValue >= capUsd * CAP_HIT_THRESHOLD) {
                            hasCapHit = true;
                            break;
                        }
                    }
                }
            }

            if (cardsInCategory.size() > 1 || hasCapHit) {
                String notes = cardsInCategory.stream()
                    .map(cid -> cid + ":" + Money.usd(solution.x(cid, cat)))
                    .collect(Collectors.joining(", "));
                evidence.add(new EvidenceBlock(ClaimTypeRules.ALLOCATION_SEGMENT, "", cat.name(),
                    String.format("Segment allocation %s: %s", cat.name(), notes)));
            }
        }
    }

    private void addFeeBreakEvenEvidence(MilpSolution solution, List<EvidenceBlock> evidence) {
        for (String cardId : solution.selectedCardIds()) {
            Card card = cardById.get(cardId);
            if (card == null) continue;

            double totalEarn = computeCardEarn(cardId, solution);
            double credits = BenchRunConfig.effectiveCredits(card).getAmount().doubleValue();
            double fee = card.annualFee().getAmount().doubleValue();
            double net = totalEarn + credits - fee;

            String content = String.format(
                "%s earns $%.2f, credits $%.2f, fee $%.2f, net $%.2f",
                cardId, totalEarn, credits, fee, net);

            evidence.add(new EvidenceBlock("FEE_BREAK_EVEN", cardId, "", content));
        }
    }

    private void addPortfolioStopEvidence(MilpSolution solution, List<EvidenceBlock> evidence) {
        int portfolioSize = solution.selectedCardIds().size();
        String reason = identifyBindingConstraint(solution);

        String content = String.format("Portfolio size %d: %s", portfolioSize, reason);
        evidence.add(new EvidenceBlock("PORTFOLIO_STOP", "", "", content));
    }

    private void addAssumptionEvidence(List<EvidenceBlock> evidence) {
        AssumptionEvidence assumption = cppResolver.buildAssumptionEvidence(goal, null);
        evidence.add(new EvidenceBlock("ASSUMPTION", "valuation", "valuation", assumption.content()));
    }

    private void addResultBreakdownEvidence(MilpSolution solution, List<EvidenceBlock> evidence) {
        ObjectiveBreakdown bd = solution.objectiveBreakdown();
        String content = String.format(
            "Earn %s, credits %s, fees %s, net %s; card count %d.",
            bd.getEarnValue(), bd.getCreditsValue(), bd.getFees(), bd.getNet(),
            solution.selectedCardIds().size());
        evidence.add(new EvidenceBlock("RESULT_BREAKDOWN", "", "", content));
    }

    private void addPortfolioSummaryEvidence(MilpSolution solution, List<EvidenceBlock> evidence) {
        String content = String.format("Portfolio: %s; size %d.",
            String.join(", ", solution.selectedCardIds()),
            solution.selectedCardIds().size());
        evidence.add(new EvidenceBlock("PORTFOLIO_SUMMARY", "", "", content));
    }

    // --- Helper methods ---

    private String findWinner(Category cat, MilpSolution solution) {
        String best = null;
        double bestAlloc = 0;
        for (String cardId : solution.selectedCardIds()) {
            double alloc = solution.x(cardId, cat);
            if (alloc > bestAlloc || (alloc == bestAlloc && alloc > 0
                    && (best == null || cardId.compareTo(best) < 0))) {
                bestAlloc = alloc;
                best = cardId;
            }
        }
        return bestAlloc > ALLOCATION_THRESHOLD ? best : null;
    }

    private String findRunnerUp(Category cat, MilpSolution solution, String winner) {
        // Best card NOT the winner: highest hypothetical value in this category
        String bestRunnerUp = null;
        double bestValue = Double.NEGATIVE_INFINITY;

        for (Card card : catalog.cards()) {
            if (card.id().equals(winner)) continue;
            double value = computeHypotheticalValue(card.id(), cat);
            if (value > bestValue || (value == bestValue
                    && (bestRunnerUp == null || card.id().compareTo(bestRunnerUp) < 0))) {
                bestValue = value;
                bestRunnerUp = card.id();
            }
        }
        return bestValue > 0 ? bestRunnerUp : null;
    }

    private double computeCategoryValue(String cardId, Category cat, MilpSolution solution) {
        Card card = cardById.get(cardId);
        if (card == null) return 0;

        RewardsRule rule = bestRuleForCategory(card, cat);
        if (rule == null) return 0;

        double cpp = cppResolver.usdPerPoint(rule.currency(), goal).doubleValue();
        if (cpp <= 0) return 0;

        boolean isCash = rule.currency().getType() == RewardCurrencyType.USD_CASH;

        if (rule.cap().isPresent()) {
            double wVal = solution.w(cardId, cat);
            double zVal = solution.z(cardId, cat);
            double m = rule.rate().doubleValue();
            double b = resolveFallback(card, rule).doubleValue();
            return isCash ? (m * wVal + b * zVal) : (m * wVal + b * zVal) * cpp;
        } else {
            double xVal = solution.x(cardId, cat);
            double m = rule.rate().doubleValue();
            return isCash ? m * xVal : m * xVal * cpp;
        }
    }

    private double computeHypotheticalValue(String cardId, Category cat) {
        Card card = cardById.get(cardId);
        if (card == null) return 0;

        RewardsRule rule = bestRuleForCategory(card, cat);
        if (rule == null) return 0;

        double cpp = cppResolver.usdPerPoint(rule.currency(), goal).doubleValue();
        if (cpp <= 0) return 0;

        double sc = annualSpend.getOrDefault(cat, BigDecimal.ZERO).doubleValue();
        boolean isCash = rule.currency().getType() == RewardCurrencyType.USD_CASH;
        double m = rule.rate().doubleValue();

        if (rule.cap().isPresent()) {
            double capUsd = rule.cap().get().getAmount().getAmount().doubleValue();
            double b = resolveFallback(card, rule).doubleValue();
            double capped = Math.min(sc, capUsd);
            double overflow = Math.max(0, sc - capUsd);
            return isCash ? (m * capped + b * overflow) : (m * capped + b * overflow) * cpp;
        } else {
            return isCash ? m * sc : m * sc * cpp;
        }
    }

    private double computeCardEarn(String cardId, MilpSolution solution) {
        Card card = cardById.get(cardId);
        if (card == null) return 0;

        double totalEarn = 0;
        for (Category cat : annualSpend.keySet()) {
            totalEarn += computeCategoryValue(cardId, cat, solution);
        }
        return totalEarn;
    }

    private String findFallbackCard(Category cat, String excludeCardId, MilpSolution solution) {
        for (String cardId : solution.selectedCardIds()) {
            if (!cardId.equals(excludeCardId) && solution.x(cardId, cat) > ALLOCATION_THRESHOLD) {
                return cardId;
            }
        }
        return null;
    }

    private String identifyBindingConstraint(MilpSolution solution) {
        int portfolioSize = solution.selectedCardIds().size();
        int maxCards = Math.min(3, constraints.getMaxCards());
        if (portfolioSize >= maxCards) return "max_cards";

        double totalFees = solution.selectedCardIds().stream()
            .mapToDouble(cid -> {
                Card c = cardById.get(cid);
                return c != null ? c.annualFee().getAmount().doubleValue() : 0;
            })
            .sum();
        double feeBudget = constraints.getMaxAnnualFee().getAmount().doubleValue();
        if (Math.abs(totalFees - feeBudget) < 0.01) return "fee_budget";

        return "no_marginal_benefit";
    }

    private RewardsRule bestRuleForCategory(Card card, Category cat) {
        List<RewardsRule> candidates = new ArrayList<>();
        for (RewardsRule r : card.rules()) {
            if (r.category() == cat) candidates.add(r);
        }
        if (candidates.isEmpty()) return null;
        candidates.sort((a, b) -> {
            int cmp = b.rate().compareTo(a.rate());
            if (cmp != 0) return cmp;
            boolean aCapped = a.cap().isPresent();
            boolean bCapped = b.cap().isPresent();
            if (aCapped && !bCapped) return -1;
            if (!aCapped && bCapped) return 1;
            return a.id().compareTo(b.id());
        });
        return candidates.get(0);
    }

    private BigDecimal resolveFallback(Card card, RewardsRule rule) {
        if (rule.fallbackMultiplier().isPresent()) {
            return rule.fallbackMultiplier().get();
        }
        boolean isCash = rule.currency().getType() == RewardCurrencyType.USD_CASH;
        for (RewardsRule r : card.rules()) {
            if (r.category() == rule.category() && r != rule
                    && r.cap().isEmpty() && r.currency().equals(rule.currency())) {
                return r.rate();
            }
        }
        return isCash ? FALLBACK_CASH : FALLBACK_POINTS;
    }
}
