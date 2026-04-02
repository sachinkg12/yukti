package io.yukti.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yukti.catalog.impl.ImmutableCard;
import io.yukti.catalog.impl.ImmutableCatalog;
import io.yukti.catalog.impl.ImmutableRewardsRule;
import io.yukti.catalog.impl.ImmutableValuationPolicy;
import io.yukti.catalog.util.CategoryMapping;
import io.yukti.catalog.util.CurrencyMapping;
import io.yukti.core.api.Card;
import io.yukti.core.api.Catalog;
import io.yukti.core.api.RewardsRule;
import io.yukti.core.api.ValuationPolicy;
import io.yukti.core.domain.*;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;

public class JsonCatalogParser {
    private static final ObjectMapper OM = new ObjectMapper();

    public Catalog parse(InputStream in) throws Exception {
        JsonNode root = OM.readTree(in);
        String version = root.path("version").asText("1.0");
        JsonNode cardsNode = root.path("cards");
        JsonNode policiesNode = root.path("valuationPolicies");

        List<Card> cards = new ArrayList<>();
        for (JsonNode c : cardsNode) {
            cards.add(parseCard(c));
        }

        List<ValuationPolicy> policies = new ArrayList<>();
        for (JsonNode p : policiesNode) {
            policies.add(parseValuationPolicy(p));
        }

        return new ImmutableCatalog(version, cards, policies);
    }

    private Card parseCard(JsonNode c) {
        String id = c.path("id").asText();
        String displayName = c.path("displayName").asText();
        String issuer = c.path("issuer").asText();
        Money annualFee = Money.usd(c.path("annualFee").asDouble(0));
        Money credits = Money.usd(c.path("statementCreditsAnnual").asDouble(0));
        List<RewardsRule> rules = new ArrayList<>();
        for (JsonNode r : c.path("rules")) {
            rules.add(parseRule(r, id));
        }
        return new ImmutableCard(id, displayName, issuer, annualFee, rules, credits);
    }

    /**
     * Parse rule from bundle JSON. Supports paper DSL format (multiplier, cap, fallbackMultiplier)
     * and legacy format (rate, annualCapSpend) for backward compatibility.
     */
    private RewardsRule parseRule(JsonNode r, String cardId) {
        Category cat = CategoryMapping.fromJson(r.path("category").asText());
        // Paper format: "multiplier"; legacy: "rate"
        BigDecimal rate = r.has("multiplier")
            ? BigDecimal.valueOf(r.path("multiplier").asDouble())
            : BigDecimal.valueOf(r.path("rate").asDouble());
        Optional<Cap> cap = Optional.empty();
        if (r.has("cap") && !r.path("cap").isNull()) {
            JsonNode capNode = r.path("cap");
            double amountUsd = capNode.path("amountUsd").asDouble(0);
            if (amountUsd > 0) {
                String periodStr = capNode.path("period").asText("ANNUAL");
                cap = Optional.of(new Cap(Money.usd(amountUsd),
                    "MONTHLY".equalsIgnoreCase(periodStr) ? Period.MONTHLY : Period.ANNUAL));
            }
        } else {
            double capVal = r.path("annualCapSpend").asDouble(-1);
            if (capVal >= 0) {
                cap = Optional.of(new Cap(Money.usd(capVal), Period.ANNUAL));
            }
        }
        Optional<BigDecimal> fallbackMultiplier = Optional.empty();
        if (r.has("fallbackMultiplier") && !r.path("fallbackMultiplier").isNull()) {
            fallbackMultiplier = Optional.of(BigDecimal.valueOf(r.path("fallbackMultiplier").asDouble()));
        }
        RewardCurrency curr = CurrencyMapping.fromJsonId(r.path("currency").asText("USD"));
        String ruleId = cardId + "_" + cat.name();
        return new ImmutableRewardsRule(ruleId, cat, rate, cap, fallbackMultiplier, curr);
    }

    private ValuationPolicy parseValuationPolicy(JsonNode p) {
        RewardCurrency curr = CurrencyMapping.fromJsonId(p.path("currency").asText());
        GoalType goal = GoalType.valueOf(p.path("goal").asText());
        BigDecimal cpp = BigDecimal.valueOf(p.path("centsPerUnit").asDouble());
        String id = curr.getId() + "_" + goal + "_" + p.path("id").asText("");
        return new ImmutableValuationPolicy(id, curr, goal, cpp);
    }
}
