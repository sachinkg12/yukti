package io.yukti.engine.optimizer;

import io.yukti.core.api.Card;
import io.yukti.core.api.Catalog;
import io.yukti.core.api.RewardModel;
import io.yukti.core.api.ValuationModel;
import io.yukti.core.domain.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * Deterministic per-category allocation for a fixed portfolio.
 * Uses RewardModel for rewards, ValuationModel for USD value.
 * v1: one card per category; caps handled inside RewardModel (piecewise).
 * Per-request cache: pass non-null cache to reuse (cardId, category, spendAmount) across solve() calls.
 */
public final class AllocationSolverV1 {

    private static final BigDecimal TIE_THRESHOLD = new BigDecimal("0.01");

    /** Cache value: valueUSD + evidence from rewardModel for (cardId, category, spendAmount). */
    public record CachedValue(Money value, List<EvidenceBlock> evidence) {}

    public AllocationResult solve(
        OptimizationRequest request,
        Catalog catalog,
        List<String> portfolioCardIds,
        RewardModel rewardModel,
        ValuationModel valuationModel
    ) {
        return solve(request, catalog, portfolioCardIds, rewardModel, valuationModel, null);
    }

    /**
     * Solve with optional per-request cache. When non-null, cache is reused across calls (same request).
     */
    public AllocationResult solve(
        OptimizationRequest request,
        Catalog catalog,
        List<String> portfolioCardIds,
        RewardModel rewardModel,
        ValuationModel valuationModel,
        Map<String, CachedValue> externalCache
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
        Map<String, Card> cardById = new HashMap<>();
        for (Card c : cards) cardById.put(c.id(), c);

        Map<Category, String> allocation = new EnumMap<>(Category.class);
        List<EvidenceBlock> evidence = new ArrayList<>();
        Map<String, CachedValue> cache = externalCache != null ? externalCache : new HashMap<>();

        for (Map.Entry<Category, Money> e : annualSpend.entrySet()) {
            Category cat = e.getKey();
            Money spend = e.getValue();
            Map<Category, Money> spendSlice = Map.of(cat, spend);

            List<Candidate> candidates = new ArrayList<>();
            for (String cardId : portfolioCardIds) {
                Card card = cardById.get(cardId);
                if (card == null) continue;
                String cacheKey = cardId + "|" + cat.name() + "|" + spend.getAmount();
                CachedValue cv = cache.computeIfAbsent(cacheKey, k -> {
                    RewardsBreakdown breakdown = rewardModel.computeRewards(card, spendSlice);
                    Money value = valuationModel.value(breakdown, userGoal, catalog);
                    return new CachedValue(value, breakdown.getEvidence());
                });
                candidates.add(new Candidate(cardId, cv.value, card.annualFee()));
            }

            if (candidates.isEmpty()) continue;

            candidates.sort((a, b) -> {
                int cmp = b.value.getAmount().compareTo(a.value.getAmount());
                if (cmp != 0) return cmp;
                if (b.value.getAmount().subtract(a.value.getAmount()).abs().compareTo(TIE_THRESHOLD) < 0) {
                    int feeCmp = a.fee.getAmount().compareTo(b.fee.getAmount());
                    if (feeCmp != 0) return feeCmp;
                }
                return a.cardId.compareTo(b.cardId);
            });

            String winner = candidates.get(0).cardId;
            Money winnerValue = candidates.get(0).value;
            String runnerUp = candidates.size() > 1 ? candidates.get(1).cardId : null;
            Money runnerUpValue = candidates.size() > 1 ? candidates.get(1).value : Money.zeroUsd();

            allocation.put(cat, winner);

            Money delta = runnerUp != null ? winnerValue.subtract(runnerUpValue) : Money.zeroUsd();
            evidence.add(new EvidenceBlock("WINNER_BY_CATEGORY", winner, cat.name(),
                String.format("%s wins %s over %s: delta %s", winner, cat.name(),
                    runnerUp != null ? runnerUp : "N/A", delta)));
        }

        Money earnedValue = Money.zeroUsd();
        Money creditValue = Money.zeroUsd();
        Money fees = Money.zeroUsd();

        Set<String> portfolioSet = new LinkedHashSet<>(portfolioCardIds);
        for (String cardId : portfolioSet) {
            Card c = cardById.get(cardId);
            if (c == null) continue;
            creditValue = creditValue.add(c.statementCreditsAnnual());
            fees = fees.add(c.annualFee());
        }

        for (Map.Entry<Category, String> e : allocation.entrySet()) {
            Category cat = e.getKey();
            String cardId = e.getValue();
            Money spend = annualSpend.get(cat);
            if (spend == null) continue;
            String cacheKey = cardId + "|" + cat.name() + "|" + spend.getAmount();
            CachedValue cv = cache.get(cacheKey);
            if (cv != null) {
                evidence.addAll(cv.evidence.stream().filter(eb -> "CAP_HIT".equals(eb.getType())).toList());
                earnedValue = earnedValue.add(cv.value);
            }
        }

        Money netValue = earnedValue.add(creditValue).subtract(fees);

        return new AllocationResult(allocation, earnedValue, creditValue, fees, netValue, evidence);
    }

    private record Candidate(String cardId, Money value, Money fee) {}
}
