package io.yukti.engine.reward;

import io.yukti.catalog.impl.ImmutableCard;
import io.yukti.catalog.impl.ImmutableRewardsRule;
import io.yukti.catalog.util.CurrencyMapping;
import io.yukti.core.api.Card;
import io.yukti.core.api.RewardModel;
import io.yukti.core.domain.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RewardModelV1Test {

    private final RewardModel model = new RewardModelV1();

    @Test
    void capSegmentation_spendExceedsCap_usesFallback() {
        Card card = cardWithCappedGroceries();
        Map<Category, Money> spend = Map.of(
            Category.GROCERIES, Money.usd(10000)  // $6k at 6%, $4k at 1% fallback
        );

        RewardsBreakdown r = model.computeRewards(card, spend);

        Points pts = r.get(CurrencyMapping.fromJsonId("USD"));
        assertEquals(new BigDecimal("400.00"), pts.getAmount());  // 6000*0.06 + 4000*0.01 = 360 + 40
    }

    @Test
    void capSegmentation_spendUnderCap_noFallback() {
        Card card = cardWithCappedGroceries();
        Map<Category, Money> spend = Map.of(
            Category.GROCERIES, Money.usd(3000)
        );

        RewardsBreakdown r = model.computeRewards(card, spend);

        Points pts = r.get(CurrencyMapping.fromJsonId("USD"));
        assertEquals(new BigDecimal("180.00"), pts.getAmount());  // 3000 * 0.06
    }

    @Test
    void capSegmentation_uncappedRule_noCapConsumption() {
        Card card = cardWithUncappedOther();
        Map<Category, Money> spend = Map.of(
            Category.OTHER, Money.usd(5000)
        );

        RewardsBreakdown r = model.computeRewards(card, spend);

        Points pts = r.get(CurrencyMapping.fromJsonId("USD"));
        assertEquals(new BigDecimal("100.00"), pts.getAmount());  // 5000 * 0.02
        assertTrue(r.getCapNotes().isEmpty());
    }

    @Test
    void mixedCappedAndUncapped_correctSegmentation() {
        Card card = new ImmutableCard(
            "test-card",
            "Test",
            "Test",
            Money.usd(95),
            List.of(
                new ImmutableRewardsRule("g", Category.GROCERIES, new BigDecimal("0.06"),
                    Optional.of(new Cap(Money.usd(6000), Period.ANNUAL)),
                    Optional.of(new BigDecimal("0.01")),
                    CurrencyMapping.fromJsonId("USD")),
                new ImmutableRewardsRule("o", Category.OTHER, new BigDecimal("0.01"),
                    Optional.empty(), Optional.empty(), CurrencyMapping.fromJsonId("USD"))
            ),
            Money.zeroUsd()
        );
        Map<Category, Money> spend = Map.of(
            Category.GROCERIES, Money.usd(8000),
            Category.OTHER, Money.usd(2000)
        );

        RewardsBreakdown r = model.computeRewards(card, spend);

        Points total = r.get(CurrencyMapping.fromJsonId("USD"));
        assertEquals(new BigDecimal("380.00"), r.getPointsByCategory().get(Category.GROCERIES).getAmount());
        assertEquals(new BigDecimal("20.00"), r.getPointsByCategory().get(Category.OTHER).getAmount());
        assertEquals(new BigDecimal("400.00"), total.getAmount());
    }

    /** (C) Evidence order: Category enum order; within category EarnRateEvidence then CapHitEvidence. */
    @Test
    void spec_evidenceOrder_categoryEnumThenEarnRateThenCapHit() {
        Card card = new ImmutableCard(
            "mixed",
            "Mixed",
            "Test",
            Money.usd(0),
            List.of(
                new ImmutableRewardsRule("g", Category.GROCERIES, new BigDecimal("0.05"),
                    Optional.of(new Cap(Money.usd(5000), Period.ANNUAL)),
                    Optional.empty(), CurrencyMapping.fromJsonId("USD")),
                new ImmutableRewardsRule("d", Category.DINING, new BigDecimal("0.03"),
                    Optional.empty(), Optional.empty(), CurrencyMapping.fromJsonId("USD"))
            ),
            Money.zeroUsd()
        );
        Map<Category, Money> spend = Map.of(
            Category.GROCERIES, Money.usd(7000),
            Category.DINING, Money.usd(1000)
        );
        RewardsBreakdown r = model.computeRewards(card, spend);
        List<io.yukti.core.domain.EvidenceBlock> evidence = r.getEvidence();
        // Should have EARN_RATE then CAP_HIT for GROCERIES, then EARN_RATE for DINING (no cap hit)
        int firstGroceries = indexOfCategory(evidence, "GROCERIES");
        int secondGroceries = indexOfCategory(evidence, "GROCERIES", firstGroceries + 1);
        int dining = indexOfCategory(evidence, "DINING");
        assertTrue(firstGroceries >= 0, "EARN_RATE for GROCERIES");
        assertTrue(secondGroceries >= 0, "CAP_HIT for GROCERIES");
        assertTrue(dining >= 0, "EARN_RATE for DINING");
        assertEquals("EARN_RATE", evidence.get(firstGroceries).getType());
        assertEquals("CAP_HIT", evidence.get(secondGroceries).getType());
        assertEquals("EARN_RATE", evidence.get(dining).getType());
        // Category order: GROCERIES before DINING (enum order)
        assertTrue(firstGroceries < dining, "GROCERIES evidence before DINING");
    }

    private int indexOfCategory(List<io.yukti.core.domain.EvidenceBlock> list, String category) {
        return indexOfCategory(list, category, 0);
    }

    private int indexOfCategory(List<io.yukti.core.domain.EvidenceBlock> list, String category, int from) {
        for (int i = from; i < list.size(); i++) {
            if (category.equals(list.get(i).getCategory())) return i;
        }
        return -1;
    }

    @Test
    void rounding_consistent() {
        Card card = cardWithCappedGroceries();
        Map<Category, Money> spend = Map.of(
            Category.GROCERIES, Money.usd(3333.33)
        );

        RewardsBreakdown r1 = model.computeRewards(card, spend);
        RewardsBreakdown r2 = model.computeRewards(card, spend);

        assertEquals(r1.get(CurrencyMapping.fromJsonId("USD")).getAmount(),
            r2.get(CurrencyMapping.fromJsonId("USD")).getAmount());
        assertEquals(r1.get(CurrencyMapping.fromJsonId("USD")).getAmount().scale(), 2);
    }

    @Test
    void evidence_blocksGenerated() {
        Card card = cardWithCappedGroceries();
        Map<Category, Money> spend = Map.of(
            Category.GROCERIES, Money.usd(10000)
        );

        RewardsBreakdown r = model.computeRewards(card, spend);

        assertFalse(r.getEvidence().isEmpty());
        assertTrue(r.getEvidence().stream().anyMatch(eb -> "CAP_HIT".equals(eb.getType())));
        assertTrue(r.getEvidence().stream().anyMatch(eb -> "EARN_RATE".equals(eb.getType())));
        assertFalse(r.getCapNotes().isEmpty());
    }

    @Test
    void creditsUSD_included() {
        Card card = new ImmutableCard(
            "test",
            "Test",
            "Test",
            Money.usd(95),
            List.of(),
            Money.usd(50)
        );
        RewardsBreakdown r = model.computeRewards(card, Map.of());
        assertEquals(Money.usd(50), r.getCreditsUSD());
    }

    @Test
    void rewardComputation_independentFromOptimizer() {
        Card card = cardWithCappedGroceries();
        Map<Category, Money> spend = Map.of(Category.GROCERIES, Money.usd(10000));
        RewardsBreakdown r = model.computeRewards(card, spend);
        assertNotNull(r);
        assertTrue(r.get(CurrencyMapping.fromJsonId("USD")).getAmount().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void spec_pointsCard_groceriesCap6000_earns20000Points() {
        Card card = new ImmutableCard(
            "aa-card",
            "AA Co-brand",
            "Barclays",
            Money.usd(95),
            List.of(
                new ImmutableRewardsRule("g", Category.GROCERIES, new BigDecimal("3"),
                    Optional.of(new Cap(Money.usd(6000), Period.ANNUAL)),
                    Optional.empty(), CurrencyMapping.fromJsonId("AA_MILES"))
            ),
            Money.zeroUsd()
        );
        Map<Category, Money> spend = Map.of(Category.GROCERIES, Money.usd(8000));
        RewardsBreakdown r = model.computeRewards(card, spend);
        // (A) Cap segmentation: capped 6000*3=18000, remainder 2000*1=2000 -> 20000 points
        assertEquals(new BigDecimal("20000"), r.getPointsByCategory().get(Category.GROCERIES).getAmount());
        assertEquals(new BigDecimal("20000"), r.get(CurrencyMapping.fromJsonId("AA_MILES")).getAmount());
        // CapHitEvidence with spendAppliedToCap=6000, remainingSpend=2000
        assertTrue(r.getEvidence().stream().anyMatch(eb -> "CAP_HIT".equals(eb.getType())));
        String capHitContent = r.getEvidence().stream()
            .filter(eb -> "CAP_HIT".equals(eb.getType()))
            .map(io.yukti.core.domain.EvidenceBlock::getContent)
            .findFirst().orElse("");
        assertTrue(capHitContent.contains("6000") && capHitContent.contains("2000"),
            "CapHitEvidence should mention applied 6000 and remainder 2000: " + capHitContent);
    }

    @Test
    void spec_cashbackUncapped_2pct_spend8000_earns160() {
        Card card = new ImmutableCard(
            "cashback-2",
            "Flat 2%",
            "Test",
            Money.usd(0),
            List.of(
                new ImmutableRewardsRule("o", Category.OTHER, new BigDecimal("0.02"),
                    Optional.empty(), Optional.empty(), CurrencyMapping.fromJsonId("USD"))
            ),
            Money.zeroUsd()
        );
        Map<Category, Money> spend = Map.of(Category.OTHER, Money.usd(8000));
        RewardsBreakdown r = model.computeRewards(card, spend);
        Points pts = r.get(CurrencyMapping.fromJsonId("USD"));
        assertEquals(new BigDecimal("160.00"), pts.getAmount());
    }

    @Test
    void spec_rounding_points333d33_mult3_roundsDownTo999() {
        Card card = new ImmutableCard(
            "pts",
            "Points",
            "Test",
            Money.usd(0),
            List.of(
                new ImmutableRewardsRule("o", Category.OTHER, new BigDecimal("3"),
                    Optional.empty(), Optional.empty(), CurrencyMapping.fromJsonId("AA_MILES"))
            ),
            Money.zeroUsd()
        );
        Map<Category, Money> spend = Map.of(Category.OTHER, Money.usd(new BigDecimal("333.33")));
        RewardsBreakdown r = model.computeRewards(card, spend);
        Points pts = r.get(CurrencyMapping.fromJsonId("AA_MILES"));
        assertEquals(new BigDecimal("999"), pts.getAmount());  // 333.33*3=999.99 -> DOWN to 999
    }

    @Test
    void spec_rounding_cash333d33_mult0d02_roundsTo6d67() {
        Card card = new ImmutableCard(
            "cash",
            "Cash",
            "Test",
            Money.usd(0),
            List.of(
                new ImmutableRewardsRule("o", Category.OTHER, new BigDecimal("0.02"),
                    Optional.empty(), Optional.empty(), CurrencyMapping.fromJsonId("USD"))
            ),
            Money.zeroUsd()
        );
        Map<Category, Money> spend = Map.of(Category.OTHER, Money.usd(new BigDecimal("333.33")));
        RewardsBreakdown r = model.computeRewards(card, spend);
        Points pts = r.get(CurrencyMapping.fromJsonId("USD"));
        assertEquals(new BigDecimal("6.67"), pts.getAmount());  // 333.33*0.02=6.6666 -> HALF_UP 6.67
    }

    /** (D) Credits EV: amount 120 * utilization 0.5 = 60.00 USD. */
    @Test
    void spec_creditsEv_amount120_utilization0d5_equals60() {
        Card card = new ImmutableCard(
            "credit-card",
            "Credit",
            "Test",
            Money.usd(0),
            List.of(),
            Money.usd(new BigDecimal("60.00"))  // precomputed 120 * 0.5
        );
        RewardsBreakdown r = model.computeRewards(card, Map.of());
        assertEquals(Money.usd(new BigDecimal("60.00")), r.getCreditsUSD());
    }

    @Test
    void spec_defaultBase_diningRuleOtherMissing_usesDefault() {
        Card card = new ImmutableCard(
            "dining-only",
            "Dining",
            "Test",
            Money.usd(0),
            List.of(
                new ImmutableRewardsRule("d", Category.DINING, new BigDecimal("0.03"),
                    Optional.empty(), Optional.empty(), CurrencyMapping.fromJsonId("USD"))
            ),
            Money.zeroUsd()
        );
        Map<Category, Money> spend = Map.of(Category.DINING, Money.usd(1000), Category.OTHER, Money.usd(2000));
        RewardsBreakdown r = model.computeRewards(card, spend);
        assertEquals(new BigDecimal("30.00"), r.getPointsByCategory().get(Category.DINING).getAmount());  // 1000*0.03
        assertEquals(new BigDecimal("20.00"), r.getPointsByCategory().get(Category.OTHER).getAmount());  // 2000*0.01 default
        boolean hasDefaultBase = r.getEvidence().stream()
            .anyMatch(eb -> "EARN_RATE".equals(eb.getType()) && eb.getContent().contains("DEFAULT_BASE"));
        assertTrue(hasDefaultBase);
    }

    private Card cardWithCappedGroceries() {
        return new ImmutableCard(
            "amex-bcp",
            "Amex BCP",
            "Amex",
            Money.usd(95),
            List.of(
                new ImmutableRewardsRule("g", Category.GROCERIES, new BigDecimal("0.06"),
                    Optional.of(new Cap(Money.usd(6000), Period.ANNUAL)),
                    Optional.of(new BigDecimal("0.01")),
                    CurrencyMapping.fromJsonId("USD")),
                new ImmutableRewardsRule("o", Category.OTHER, new BigDecimal("0.01"),
                    Optional.empty(), Optional.empty(), CurrencyMapping.fromJsonId("USD"))
            ),
            Money.zeroUsd()
        );
    }

    private Card cardWithUncappedOther() {
        return new ImmutableCard(
            "citi-dc",
            "Citi Double Cash",
            "Citi",
            Money.usd(0),
            List.of(
                new ImmutableRewardsRule("o", Category.OTHER, new BigDecimal("0.02"),
                    Optional.empty(), Optional.empty(), CurrencyMapping.fromJsonId("USD"))
            ),
            Money.zeroUsd()
        );
    }

    @Test
    void negativeSpend_failsFast() {
        Card card = cardWithUncappedOther();
        Map<Category, Money> spend = Map.of(Category.OTHER, Money.usd(new BigDecimal("-100")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> model.computeRewards(card, spend));
        assertTrue(ex.getMessage().contains("Negative spend not allowed"));
        assertTrue(ex.getMessage().contains("OTHER"));
        assertTrue(ex.getMessage().contains("-100"));
    }
}
