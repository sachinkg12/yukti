package io.yukti.catalog;

import io.yukti.catalog.dsl.CardDefinition;
import io.yukti.catalog.dsl.CapObject;
import io.yukti.catalog.dsl.CreditRule;
import io.yukti.catalog.dsl.EarnRule;
import io.yukti.catalog.impl.ImmutableCard;
import io.yukti.catalog.impl.ImmutableRewardsRule;
import io.yukti.catalog.util.CategoryMapping;
import io.yukti.catalog.util.CurrencyMapping;
import io.yukti.core.api.Card;
import io.yukti.core.api.RewardsRule;
import io.yukti.core.domain.Cap;
import io.yukti.core.domain.Money;
import io.yukti.core.domain.Period;
import io.yukti.core.domain.RewardCurrency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Maps CardDefinition (DSL v0.1) to domain Card.
 */
public final class CardDefinitionMapper {

    public Card toCard(CardDefinition def) {
        String cardId = def.cardId();
        RewardCurrency currency = CurrencyMapping.fromJsonId(def.rewardCurrencyType());
        List<RewardsRule> rules = new ArrayList<>();
        for (EarnRule r : def.earnRules()) {
            if (!"EARN_MULTIPLIER".equals(r.ruleType())) continue;
            rules.add(toRewardsRule(r, cardId, currency));
        }
        Money credits = sumCreditsToAnnual(def.credits());
        return new ImmutableCard(
            cardId,
            def.name(),
            def.issuer(),
            Money.usd(def.annualFeeUsd()),
            rules,
            credits
        );
    }

    private RewardsRule toRewardsRule(EarnRule r, String cardId, RewardCurrency currency) {
        var cat = CategoryMapping.fromJson(r.category());
        BigDecimal rate = r.multiplier() != null ? r.multiplier() : BigDecimal.ZERO;
        Optional<Cap> cap = Optional.empty();
        if (r.cap() != null && r.cap().amountUsd() > 0) {
            cap = Optional.of(new Cap(
                Money.usd(r.cap().amountUsd()),
                "MONTHLY".equalsIgnoreCase(r.cap().period()) ? Period.MONTHLY : Period.ANNUAL
            ));
        }
        Optional<BigDecimal> fallbackMultiplier = r.fallbackMultiplier() != null
            ? Optional.of(r.fallbackMultiplier())
            : Optional.empty();
        String ruleId = cardId + "_" + cat.name();
        return new ImmutableRewardsRule(ruleId, cat, rate, cap, fallbackMultiplier, currency);
    }

    private Money sumCreditsToAnnual(List<CreditRule> credits) {
        double total = 0;
        for (CreditRule c : credits) {
            if (!"CREDIT".equals(c.ruleType())) continue;
            double ev = c.amountUsd() * c.assumedUtilizationDefault();
            if ("MONTHLY".equalsIgnoreCase(c.period())) {
                ev *= 12;
            }
            total += ev;
        }
        return Money.usd(BigDecimal.valueOf(total).setScale(2, RoundingMode.HALF_UP));
    }
}
