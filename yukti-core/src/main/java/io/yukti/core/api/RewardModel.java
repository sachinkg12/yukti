package io.yukti.core.api;

import io.yukti.core.domain.Category;
import io.yukti.core.domain.Money;
import io.yukti.core.domain.RewardsBreakdown;

import java.util.Map;

/**
 * Computes rewards for a card given spend allocation. Currency-aware.
 */
public interface RewardModel {
    String id();
    RewardsBreakdown computeRewards(Card card, Map<Category, Money> spendAllocation);
}
