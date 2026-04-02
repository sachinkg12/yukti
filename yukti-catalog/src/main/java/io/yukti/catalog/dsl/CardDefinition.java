package io.yukti.catalog.dsl;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * DSL v0.1 card definition. Required: dslVersion, cardId, issuer, name, segment,
 * annualFeeUsd, rewardCurrencyType, earnRules, credits, sources, asOfDate.
 */
public record CardDefinition(
    @JsonProperty("dslVersion") String dslVersion,
    @JsonProperty("cardId") String cardId,
    @JsonProperty("issuer") String issuer,
    @JsonProperty("name") String name,
    @JsonProperty("segment") String segment,
    @JsonProperty("annualFeeUsd") double annualFeeUsd,
    @JsonProperty("rewardCurrencyType") String rewardCurrencyType,
    @JsonProperty("earnRules") List<EarnRule> earnRules,
    @JsonProperty("credits") List<CreditRule> credits,
    /** Required: URLs or citation strings for benefit data. */
    @JsonProperty("sources") List<String> sources,
    /** Required: date of source data, YYYY-MM-DD. */
    @JsonProperty("asOfDate") String asOfDate,
    /** Optional: benefit types that cannot be represented in this schema. */
    @JsonProperty("unsupportedFeatures") List<String> unsupportedFeatures
) {
    public CardDefinition {
        earnRules = earnRules != null ? List.copyOf(earnRules) : List.of();
        credits = credits != null ? List.copyOf(credits) : List.of();
        sources = sources != null ? List.copyOf(sources) : List.of();
        unsupportedFeatures = unsupportedFeatures != null ? List.copyOf(unsupportedFeatures) : List.of();
    }
}
