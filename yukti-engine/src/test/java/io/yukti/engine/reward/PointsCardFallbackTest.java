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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Points cards use 1x fallback when cap is exceeded.
 */
class PointsCardFallbackTest {

    private final RewardModel model = new RewardModelV1();

    @Test
    void pointsCard_capExceeded_uses1xFallback() {
        Card card = new ImmutableCard(
            "chase-flex",
            "Chase Freedom Flex",
            "Chase",
            Money.usd(0),
            List.of(
                new ImmutableRewardsRule("g", Category.GROCERIES, new BigDecimal("0.05"),
                    Optional.of(new Cap(Money.usd(12000), Period.ANNUAL)),
                    Optional.empty(),
                    CurrencyMapping.fromJsonId("CHASE_UR"))
            ),
            Money.zeroUsd()
        );
        Map<Category, Money> spend = Map.of(Category.GROCERIES, Money.usd(15000));

        RewardsBreakdown r = model.computeRewards(card, spend);

        Points pts = r.get(CurrencyMapping.fromJsonId("CHASE_UR"));
        assertEquals(new BigDecimal("3600"), pts.getAmount());  // 12000*0.05 + 3000*1 = 600 + 3000 (round down)
    }
}
