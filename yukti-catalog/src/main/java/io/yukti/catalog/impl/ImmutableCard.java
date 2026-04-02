package io.yukti.catalog.impl;

import io.yukti.core.api.Card;
import io.yukti.core.api.RewardsRule;
import io.yukti.core.domain.Money;

import java.util.List;

public record ImmutableCard(
    String id,
    String displayName,
    String issuer,
    Money annualFee,
    List<RewardsRule> rules,
    Money statementCreditsAnnual
) implements Card {}
