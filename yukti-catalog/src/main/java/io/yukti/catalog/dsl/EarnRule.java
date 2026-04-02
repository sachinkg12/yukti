package io.yukti.catalog.dsl;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * DSL v0.1: EARN_MULTIPLIER rule. cap is object { amountUsd, period } or null.
 */
public record EarnRule(
    @JsonProperty("ruleType") String ruleType,
    @JsonProperty("category") String category,
    @JsonProperty("multiplier") BigDecimal multiplier,
    @JsonProperty("cap") CapObject cap,
    @JsonProperty("fallbackMultiplier") BigDecimal fallbackMultiplier,
    @JsonProperty("channel") String channel,
    @JsonProperty("notes") String notes,
    /** Evidence note: quote or paraphrase from issuer page for this rule. */
    @JsonProperty("evidenceNote") String evidenceNote
) {}
