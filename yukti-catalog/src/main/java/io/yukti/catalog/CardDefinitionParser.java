package io.yukti.catalog;

import io.yukti.catalog.dsl.CardDefinition;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Parses DSL v0.1 JSON into CardDefinition. Uses camelCase (cardId, annualFeeUsd, earnRules, etc.).
 */
public final class CardDefinitionParser {
    private static final ObjectMapper OM = new ObjectMapper();

    public CardDefinition parse(String json) throws Exception {
        return OM.readValue(json, CardDefinition.class);
    }
}
