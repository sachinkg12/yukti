package io.yukti.catalog.dsl;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DSL v0.1: cap as object with amount (USD spend) and period.
 */
public record CapObject(
    @JsonProperty("amountUsd") double amountUsd,
    @JsonProperty("period") String period
) {}
