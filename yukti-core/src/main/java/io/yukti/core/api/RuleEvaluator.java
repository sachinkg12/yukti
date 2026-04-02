package io.yukti.core.api;

import io.yukti.core.domain.Money;
import io.yukti.core.domain.RewardEarnings;

/**
 * Pluggable by rule type: evaluates a rule against a spend slice.
 */
public interface RuleEvaluator {
    String ruleType();
    RewardEarnings evaluate(RewardsRule rule, Money spendSlice);
}
