package io.yukti.engine.optimizer;

import io.yukti.core.api.Card;
import io.yukti.core.api.Catalog;
import io.yukti.core.api.RewardModel;
import io.yukti.core.api.RewardsRule;
import io.yukti.core.api.ValuationModel;
import io.yukti.core.domain.*;
import io.yukti.engine.config.BenchRunConfig;
import io.yukti.engine.valuation.CppResolver;
import io.yukti.explain.core.claims.ClaimTypeRules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Segment-based allocation solver v1.1.
 * Supports cap-aware switching: spend can be split across multiple cards per category
 * using a deterministic greedy "fill best segment next" algorithm.
 * No MILP; uses piecewise marginal rates from rules.
 *
 * <p><b>Determinism (tie-breaking).</b> When two cards in the chosen portfolio have
 * identical utility (rate) for a segment, ties are broken in order: (1) lower annual
 * fee wins; (2) if fees are equal, lexicographically smallest card id wins. This
 * ensures end-to-end determinism for reproduction (see paper §4).
 */
public final class AllocationSolverV1_1 {

    private static final BigDecimal TIE_THRESHOLD = new BigDecimal("0.01");
    private static final BigDecimal FALLBACK_POINTS = BigDecimal.ONE;
    private static final BigDecimal FALLBACK_CASH = new BigDecimal("0.01");

    public AllocationResult solve(
        OptimizationRequest request,
        Catalog catalog,
        List<String> portfolioCardIds,
        RewardModel rewardModel,
        ValuationModel valuationModel
    ) {
        UserGoal userGoal = request.getUserGoal();
        SpendProfile profile = request.getSpendProfile();

        Map<Category, Money> annualSpend = new EnumMap<>(Category.class);
        for (Category cat : Category.values()) {
            Money m = profile.annualSpend(cat);
            if (m.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                annualSpend.put(cat, m);
            }
        }

        List<Card> cards = new ArrayList<>(catalog.cards());
        Map<String, Card> cardById = cards.stream().collect(Collectors.toMap(Card::id, c -> c));
        CppResolver cppResolver = new CppResolver(catalog);

        Map<Category, List<AllocationSegment>> segmentsByCategory = new EnumMap<>(Category.class);
        Map<Category, String> allocationByCategory = new EnumMap<>(Category.class);
        List<EvidenceBlock> evidence = new ArrayList<>();
        Money earnedValue = Money.zeroUsd();

        for (Map.Entry<Category, Money> e : annualSpend.entrySet()) {
            Category cat = e.getKey();
            BigDecimal spendTotal = e.getValue().getAmount();

            List<CardSegment> available = buildSegmentsForCategory(
                cat, spendTotal, portfolioCardIds, cardById, userGoal, cppResolver);

            BigDecimal remaining = spendTotal;
            List<AllocationSegment> catSegments = new ArrayList<>();
            String firstWinner = null;

            while (remaining.compareTo(BigDecimal.ZERO) > 0) {
                CardSegment best = null;
                for (CardSegment seg : available) {
                    if (seg.remainingLength.compareTo(BigDecimal.ZERO) <= 0) continue;
                    if (best == null || compareSegments(seg, best, cardById) > 0) {
                        best = seg;
                    }
                }
                if (best == null) break;

                BigDecimal allocate = remaining.min(best.remainingLength);
                catSegments.add(new AllocationSegment(best.cardId, allocate, "BEST_MARGINAL_RATE"));
                if (firstWinner == null) firstWinner = best.cardId;

                BigDecimal valueUsd = valueOfSpend(allocate, best.rateUsdPerDollar);
                earnedValue = earnedValue.add(Money.usd(valueUsd));

                remaining = remaining.subtract(allocate);
                best.remainingLength = best.remainingLength.subtract(allocate);

                if (best.remainingLength.compareTo(BigDecimal.ZERO) <= 0 && best.fallbackRateUsd != null) {
                    best.rateUsdPerDollar = best.fallbackRateUsd;
                    best.remainingLength = new BigDecimal("999999999");
                }
            }

            if (!catSegments.isEmpty()) {
                segmentsByCategory.put(cat, List.copyOf(catSegments));
                allocationByCategory.put(cat, firstWinner);

                String runnerUp = catSegments.size() > 1 ? catSegments.get(1).cardId() : null;
                evidence.add(new EvidenceBlock("WINNER_BY_CATEGORY", firstWinner, cat.name(),
                    String.format("%s wins %s (first segment) over %s", firstWinner, cat.name(),
                        runnerUp != null ? runnerUp : "N/A")));

                addCapHitEvidence(catSegments, cat, annualSpend.get(cat), cardById, evidence);
                addSegmentAllocationEvidence(cat, catSegments, evidence);
            }
        }

        Money creditValue = Money.zeroUsd();
        Money fees = Money.zeroUsd();
        for (String cardId : portfolioCardIds) {
            Card c = cardById.get(cardId);
            if (c != null) {
                creditValue = creditValue.add(BenchRunConfig.effectiveCredits(c));
                fees = fees.add(c.annualFee());
            }
        }
        Money netValue = earnedValue.add(creditValue).subtract(fees);

        return new AllocationResult(
            allocationByCategory,
            earnedValue,
            creditValue,
            fees,
            netValue,
            evidence,
            new AllocationPlan(segmentsByCategory));
    }

    private void addCapHitEvidence(
        List<AllocationSegment> segments,
        Category cat,
        Money totalSpend,
        Map<String, Card> cardById,
        List<EvidenceBlock> evidence
    ) {
        if (segments.size() < 2) return;

        String primaryCard = segments.get(0).cardId();
        String fallbackCard = segments.get(1).cardId();
        Card primary = cardById.get(primaryCard);
        if (primary == null) return;

        Optional<RewardsRule> rule = selectRule(primary, cat);
        if (rule.isEmpty() || rule.get().cap().isEmpty()) return;

        Cap cap = rule.get().cap().get();
        BigDecimal capAmount = cap.getAmount().getAmount();
        BigDecimal applied = segments.get(0).spendUsd();
        BigDecimal remainder = totalSpend.getAmount().subtract(applied);

        evidence.add(new EvidenceBlock("CAP_HIT", primaryCard, cat.name(),
            String.format("Cap hit: %s %s cap=%s applied=%s remainder=%s fallback=%s",
                primaryCard, cat.name(), Money.usd(capAmount), Money.usd(applied),
                Money.usd(remainder), fallbackCard)));
    }

    private void addSegmentAllocationEvidence(Category cat, List<AllocationSegment> segments, List<EvidenceBlock> evidence) {
        String notes = segments.stream()
            .map(s -> s.cardId() + ":" + s.spendUsd())
            .collect(Collectors.joining(", "));
        evidence.add(new EvidenceBlock(ClaimTypeRules.ALLOCATION_SEGMENT, "", cat.name(),
            String.format("Segment allocation %s: %s", cat.name(), notes)));
    }

    private List<CardSegment> buildSegmentsForCategory(
        Category cat,
        BigDecimal spendTotal,
        List<String> portfolioCardIds,
        Map<String, Card> cardById,
        UserGoal userGoal,
        CppResolver cppResolver
    ) {
        List<CardSegment> out = new ArrayList<>();
        for (String cardId : portfolioCardIds) {
            Card card = cardById.get(cardId);
            if (card == null) continue;

            Optional<RewardsRule> ruleOpt = selectRule(card, cat);
            RewardCurrency currency = cardCurrency(card);
            boolean isCash = currency.getType() == RewardCurrencyType.USD_CASH;
            BigDecimal usdPerPoint = cppResolver.usdPerPoint(currency, userGoal);
            if (usdPerPoint.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal rateCap;
            BigDecimal rateFallback;
            BigDecimal capLength;

            if (ruleOpt.isPresent()) {
                RewardsRule rule = ruleOpt.get();
                rateCap = rule.rate();
                rateFallback = fallbackForRule(card, rule, isCash);
                if (rule.cap().isPresent()) {
                    // Use raw cap amount — spend is already annualized via
                    // profile.annualSpend(), so no period conversion is needed.
                    // Matches MilpModelBuilder (line 110).
                    capLength = rule.cap().get().getAmount().getAmount();
                } else {
                    capLength = null;
                }
            } else {
                rateCap = isCash ? FALLBACK_CASH : FALLBACK_POINTS;
                rateFallback = rateCap;
                capLength = null;
            }

            BigDecimal rateUsd = rateToUsdPerDollar(rateCap, currency, usdPerPoint);
            BigDecimal fallbackUsd = rateToUsdPerDollar(rateFallback, currency, usdPerPoint);

            if (capLength != null) {
                out.add(new CardSegment(cardId, rateUsd, fallbackUsd, capLength));
            } else {
                out.add(new CardSegment(cardId, rateUsd, null, new BigDecimal("999999999")));
            }
        }
        return out;
    }

    private BigDecimal rateToUsdPerDollar(BigDecimal rate, RewardCurrency currency, BigDecimal usdPerPoint) {
        if (currency.getType() == RewardCurrencyType.USD_CASH) {
            return rate;
        }
        return rate.multiply(usdPerPoint);
    }

    private BigDecimal valueOfSpend(BigDecimal spend, BigDecimal rateUsdPerDollar) {
        return spend.multiply(rateUsdPerDollar).setScale(2, RoundingMode.HALF_UP);
    }

    /** Tie-break: higher rate first; then lower fee; then lexicographically smaller card id. */
    private int compareSegments(CardSegment a, CardSegment b, Map<String, Card> cardById) {
        int cmp = a.rateUsdPerDollar.compareTo(b.rateUsdPerDollar);
        if (cmp != 0) return cmp;
        if (a.rateUsdPerDollar.subtract(b.rateUsdPerDollar).abs().compareTo(TIE_THRESHOLD) < 0) {
            Card ca = cardById.get(a.cardId);
            Card cb = cardById.get(b.cardId);
            Money fa = ca != null ? ca.annualFee() : Money.zeroUsd();
            Money fb = cb != null ? cb.annualFee() : Money.zeroUsd();
            cmp = fa.getAmount().compareTo(fb.getAmount());
            if (cmp != 0) return -cmp;
        }
        return b.cardId.compareTo(a.cardId);
    }

    private Optional<RewardsRule> selectRule(Card card, Category cat) {
        RewardCurrency currency = cardCurrency(card);
        List<RewardsRule> applicable = new ArrayList<>();
        for (RewardsRule r : card.rules()) {
            if (r.category() == cat && r.currency().equals(currency)) {
                applicable.add(r);
            }
        }
        if (applicable.isEmpty()) return Optional.empty();
        applicable.sort((a, b) -> {
            int cmp = b.rate().compareTo(a.rate());
            if (cmp != 0) return cmp;
            boolean aCapped = a.cap().isPresent();
            boolean bCapped = b.cap().isPresent();
            if (aCapped && !bCapped) return -1;
            if (!aCapped && bCapped) return 1;
            return a.id().compareTo(b.id());
        });
        return Optional.of(applicable.get(0));
    }

    private BigDecimal fallbackForRule(Card card, RewardsRule rule, boolean isCash) {
        // 1. Explicit fallbackMultiplier from rule (matches MilpModelBuilder.resolveFallback)
        if (rule.fallbackMultiplier().isPresent()) {
            return rule.fallbackMultiplier().get();
        }
        // 2. Same-card, same-category uncapped rule rate
        BigDecimal defaultFallback = isCash ? FALLBACK_CASH : FALLBACK_POINTS;
        for (RewardsRule r : card.rules()) {
            if (r.category() == rule.category() && r != rule
                && r.cap().isEmpty() && r.currency().equals(rule.currency())) {
                return r.rate();
            }
        }
        // 3. Default: 0.01 for cash, 1.0 for points
        return defaultFallback;
    }

    private RewardCurrency cardCurrency(Card card) {
        for (RewardsRule r : card.rules()) {
            return r.currency();
        }
        return new RewardCurrency(RewardCurrencyType.USD_CASH, "USD");
    }

    private static class CardSegment {
        final String cardId;
        BigDecimal rateUsdPerDollar;
        final BigDecimal fallbackRateUsd;
        BigDecimal remainingLength;

        CardSegment(String cardId, BigDecimal rateUsdPerDollar, BigDecimal fallbackRateUsd, BigDecimal remainingLength) {
            this.cardId = cardId;
            this.rateUsdPerDollar = rateUsdPerDollar;
            this.fallbackRateUsd = fallbackRateUsd;
            this.remainingLength = remainingLength;
        }
    }
}
