package io.yukti.core.api;

import io.yukti.core.domain.Money;

import java.util.List;

/**
 * Credit card definition. Catalog-sourced.
 */
public interface Card {
    String id();
    String displayName();
    String issuer();
    Money annualFee();
    List<? extends RewardsRule> rules();
    Money statementCreditsAnnual();
}
