package io.yukti.catalog.dsl;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DSL v0.1: CREDIT rule. amountUsd, period, assumedUtilizationDefault in [0,1].
 */
public record CreditRule(
    @JsonProperty("ruleType") String ruleType,
    @JsonProperty("name") String name,
    @JsonProperty("amountUsd") double amountUsd,
    @JsonProperty("period") String period,
    @JsonProperty("assumedUtilizationDefault") double assumedUtilizationDefault
) {}
