package io.yukti.engine.display;

import io.yukti.core.api.Card;
import io.yukti.core.api.Catalog;
import io.yukti.core.api.RewardsRule;
import io.yukti.core.domain.*;
import io.yukti.engine.reward.RewardModelV1;
import io.yukti.engine.valuation.CppResolver;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Enriches allocation (category -> cardId) with display data: spend in category,
 * effective earn rate %, and earn value USD for that (category, card).
 */
public final class AllocationDisplayEnricher {

    private static final RewardModelV1 REWARD_MODEL = new RewardModelV1();

    /**
     * Display row for one allocation entry: category, card, spend, effective rate %, value USD.
     */
    public record AllocationEntryDisplay(
        String category,
        String cardId,
        BigDecimal spendUsd,
        double earnRatePercent,
        BigDecimal earnValueUsd
    ) {}

    /**
     * Enrich allocation map with spend (from profile), earn rate %, and earn value USD per entry.
     */
    public List<AllocationEntryDisplay> enrich(
        SpendProfile profile,
        Map<Category, String> allocation,
        Catalog catalog,
        UserGoal userGoal
    ) {
        if (allocation == null || allocation.isEmpty()) return List.of();
        Map<String, Card> cardById = new HashMap<>();
        for (Card c : catalog.cards()) cardById.put(c.id(), c);
        CppResolver cppResolver = new CppResolver(catalog);

        List<AllocationEntryDisplay> out = new ArrayList<>();
        for (Map.Entry<Category, String> e : allocation.entrySet()) {
            Category cat = e.getKey();
            String cardId = e.getValue();
            Card card = cardById.get(cardId);
            if (card == null) continue;
            Money spendMoney = profile.annualSpend(cat);
            BigDecimal spendUsd = spendMoney.getAmount();
            if (spendUsd.compareTo(BigDecimal.ZERO) <= 0) continue;

            Map<Category, Money> singleCategory = new EnumMap<>(Category.class);
            singleCategory.put(cat, spendMoney);
            RewardsBreakdown breakdown = REWARD_MODEL.computeRewards(card, singleCategory);

            RewardCurrency currency = cardCurrency(card);
            Points points = breakdown.getByCurrency().get(currency);
            BigDecimal pointsAmount = points != null ? points.getAmount() : BigDecimal.ZERO;

            BigDecimal earnValueUsd;
            if (currency.getType() == RewardCurrencyType.USD_CASH) {
                earnValueUsd = pointsAmount;
            } else {
                BigDecimal cpp = cppResolver.usdPerPoint(currency, userGoal);
                earnValueUsd = pointsAmount.multiply(cpp).setScale(2, RoundingMode.HALF_UP);
            }
            double earnRatePercent = spendUsd.compareTo(BigDecimal.ZERO) > 0
                ? earnValueUsd.divide(spendUsd, 6, RoundingMode.HALF_UP).doubleValue() * 100.0
                : 0.0;

            out.add(new AllocationEntryDisplay(
                cat.name(),
                cardId,
                spendUsd.setScale(2, RoundingMode.HALF_UP),
                Math.round(earnRatePercent * 100.0) / 100.0,
                earnValueUsd
            ));
        }
        return out;
    }

    private static RewardCurrency cardCurrency(Card card) {
        for (RewardsRule r : card.rules()) return r.currency();
        return new RewardCurrency(RewardCurrencyType.USD_CASH, "USD");
    }
}
