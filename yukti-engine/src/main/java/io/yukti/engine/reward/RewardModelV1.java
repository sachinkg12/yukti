package io.yukti.engine.reward;

import io.yukti.core.api.Card;
import io.yukti.core.api.RewardModel;
import io.yukti.core.api.RewardsRule;
import io.yukti.core.domain.*;
import io.yukti.core.domain.RewardCurrency;
import io.yukti.engine.math.RoundingRules;

import java.math.BigDecimal;
import java.util.*;

/**
 * RewardModelV1: currency-aware, cap-aware piecewise segmentation.
 * Pure function: Card + SpendProfile -> RewardsBreakdown.
 * Evidence blocks in deterministic order: Category enum order, EarnRateEvidence then CapHitEvidence per category.
 * v1 selects ONE rule per category (highest effective multiplier; ties: prefer capped, else lex).
 */
public final class RewardModelV1 implements RewardModel {

    private static final BigDecimal FALLBACK_POINTS = BigDecimal.ONE;   // 1x = 1 pt/$
    private static final BigDecimal FALLBACK_CASH = new BigDecimal("0.01");  // 1%

    @Override
    public String id() {
        return "reward-model-v1";
    }

    @Override
    public RewardsBreakdown computeRewards(Card card, Map<Category, Money> spendAllocation) {
        Map<Category, Money> amounts = spendAllocation == null || spendAllocation.isEmpty()
            ? new EnumMap<>(Category.class)
            : new EnumMap<>(spendAllocation);
        return computeRewards(card, new SpendProfile(Period.ANNUAL, amounts));
    }

    /**
     * Compute rewards for card given spend profile. Period-aware for cap matching.
     * For MONTHLY profile: 1-month; for ANNUAL: 1-year. Caps prorated by period.
     */
    public RewardsBreakdown computeRewards(Card card, SpendProfile spendProfile) {
        Period profilePeriod = spendProfile.getPeriod();
        Map<Category, Money> spendByCategory = spendProfile.getAmounts();

        Map<RewardCurrency, Points> byCurrency = new HashMap<>();
        Map<Category, Points> pointsByCategory = new EnumMap<>(Category.class);
        List<String> capNotes = new ArrayList<>();
        List<EvidenceBlock> evidence = new ArrayList<>();

        RewardCurrency currency = cardCurrency(card);
        boolean isCash = currency.getType() == RewardCurrencyType.USD_CASH;

        for (Category cat : Category.values()) {
            Money spendMoney = spendByCategory.getOrDefault(cat, Money.zeroUsd());
            BigDecimal spend = spendMoney.getAmount();
            if (spend.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Negative spend not allowed: category=" + cat + " spend=" + spend);
            }
            if (spend.compareTo(BigDecimal.ZERO) == 0) continue;

            Optional<RewardsRule> selectedRule = selectRule(card, cat, currency);
            BigDecimal multiplier;
            BigDecimal fallbackMultiplier;
            Optional<Cap> cap;
            String effectiveReturnSummary;

            if (selectedRule.isPresent()) {
                RewardsRule rule = selectedRule.get();
                multiplier = rule.rate();
                fallbackMultiplier = rule.fallbackMultiplier()
                    .orElseGet(() -> fallbackForRule(card, rule, isCash));
                cap = rule.cap();
                effectiveReturnSummary = cap.isPresent() ? "CAP_APPLIED" : "NO_CAP";
            } else {
                multiplier = isCash ? FALLBACK_CASH : FALLBACK_POINTS;
                fallbackMultiplier = multiplier;
                cap = Optional.empty();
                effectiveReturnSummary = "DEFAULT_BASE";
            }

            BigDecimal capAmountUsd = null;
            Period capPeriod = null;
            if (cap.isPresent()) {
                capPeriod = cap.get().getPeriod();
                BigDecimal rawCap = cap.get().getAmount().getAmount();
                if (profilePeriod == Period.MONTHLY && capPeriod == Period.ANNUAL) {
                    capAmountUsd = rawCap.divide(BigDecimal.valueOf(12), 6, java.math.RoundingMode.HALF_UP);
                } else if (profilePeriod == Period.ANNUAL && capPeriod == Period.MONTHLY) {
                    capAmountUsd = rawCap.multiply(BigDecimal.valueOf(12));
                } else {
                    capAmountUsd = rawCap;
                }
            }

            BigDecimal earned;
            BigDecimal applicableSpend;
            BigDecimal remainderSpend = BigDecimal.ZERO;

            if (cap.isEmpty() || capAmountUsd == null) {
                earned = spend.multiply(multiplier);
                applicableSpend = spend;
            } else {
                applicableSpend = spend.min(capAmountUsd);
                remainderSpend = spend.subtract(applicableSpend);
                BigDecimal earnFromCap = applicableSpend.multiply(multiplier);
                BigDecimal earnFromRemainder = remainderSpend.multiply(fallbackMultiplier);
                earned = earnFromCap.add(earnFromRemainder);
            }

            BigDecimal roundedEarned = isCash
                ? RoundingRules.roundUsd(earned)
                : RoundingRules.roundPoints(earned);

            Points pts = Points.of(roundedEarned);
            byCurrency.merge(currency, pts, (a, b) -> Points.of(a.getAmount().add(b.getAmount())));
            pointsByCategory.put(cat, pts);

            Money capAmountMoney = capAmountUsd != null ? Money.usd(capAmountUsd) : null;
            evidence.add(toEvidenceBlock(new EarnRateEvidence(
                card.id(), cat.name(),
                multiplier,
                fallbackMultiplier,
                capAmountMoney,
                capPeriod,
                effectiveReturnSummary
            )));

            boolean capHit = remainderSpend.compareTo(BigDecimal.ZERO) > 0;
            boolean nearCap = capAmountUsd != null && capAmountUsd.compareTo(BigDecimal.ZERO) > 0
                && remainderSpend.compareTo(BigDecimal.ZERO) == 0
                && spend.compareTo(capAmountUsd.multiply(new BigDecimal("0.99"))) >= 0;
            if (capHit || nearCap) {
                evidence.add(toEvidenceBlock(new CapHitEvidence(
                    card.id(), cat.name(),
                    Money.usd(capAmountUsd),
                    capPeriod,
                    Money.usd(applicableSpend),
                    Money.usd(remainderSpend),
                    null
                )));
                if (capHit) {
                    capNotes.add(String.format("%s %s: cap %s hit, %s at fallback",
                        card.id(), cat.name(), Money.usd(capAmountUsd), Money.usd(remainderSpend)));
                }
            }
        }

        Money creditsUSD = card.statementCreditsAnnual();
        return new RewardsBreakdown(byCurrency, pointsByCategory, creditsUSD, capNotes, evidence);
    }

    private RewardCurrency cardCurrency(Card card) {
        for (RewardsRule r : card.rules()) {
            return r.currency();
        }
        return new RewardCurrency(RewardCurrencyType.USD_CASH, "USD");
    }

    /** v1: pick single rule with highest multiplier for category; ties: prefer capped, else lex by rule id. */
    private Optional<RewardsRule> selectRule(Card card, Category cat, RewardCurrency currency) {
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
        BigDecimal defaultFallback = isCash ? FALLBACK_CASH : FALLBACK_POINTS;
        for (RewardsRule r : card.rules()) {
            if (r.category() == rule.category() && r != rule
                && r.cap().isEmpty() && r.currency().equals(rule.currency())) {
                return r.rate();
            }
        }
        return defaultFallback;
    }

    private EvidenceBlock toEvidenceBlock(CapHitEvidence e) {
        String content = String.format("Cap hit: %s %s cap=%s period=%s applied=%s remainder=%s",
            e.cardId(), e.category(), e.capAmount(), e.capPeriod(), e.spendAppliedToCap(), e.remainingSpend());
        return new EvidenceBlock("CAP_HIT", e.cardId(), e.category(), content);
    }

    private EvidenceBlock toEvidenceBlock(EarnRateEvidence e) {
        String content = String.format("multiplier=%s fallback=%s mode=%s",
            e.multiplierUsedForCappedPortion(), e.fallbackMultiplierUsed(), e.effectiveReturnSummary());
        return new EvidenceBlock("EARN_RATE", e.cardId(), e.category(), content);
    }
}
