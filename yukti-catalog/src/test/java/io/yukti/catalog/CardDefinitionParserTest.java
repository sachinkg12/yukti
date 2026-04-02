package io.yukti.catalog;

import io.yukti.catalog.dsl.CardDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CardDefinitionParserTest {

    @Test
    void parse_validCardV01_succeeds() throws Exception {
        String json = """
            {
              "dslVersion": "carddsl.v0.1",
              "cardId": "test-card",
              "issuer": "Test",
              "name": "Test Card",
              "segment": "PERSONAL",
              "annualFeeUsd": 95,
              "rewardCurrencyType": "USD_CASH",
              "earnRules": [
                {"ruleType": "EARN_MULTIPLIER", "category": "GROCERIES", "multiplier": 0.06, "cap": {"amountUsd": 6000, "period": "ANNUAL"}, "fallbackMultiplier": 0.01, "channel": null, "notes": null, "evidenceNote": null}
              ],
              "credits": [],
              "sources": ["https://example.com/card"],
              "asOfDate": "2025-02-17"
            }
            """;
        CardDefinition def = new CardDefinitionParser().parse(json);
        assertEquals("carddsl.v0.1", def.dslVersion());
        assertEquals("test-card", def.cardId());
        assertEquals("Test", def.issuer());
        assertEquals("Test Card", def.name());
        assertEquals("PERSONAL", def.segment());
        assertEquals(95, def.annualFeeUsd());
        assertEquals("USD_CASH", def.rewardCurrencyType());
        assertEquals(1, def.earnRules().size());
        assertEquals("EARN_MULTIPLIER", def.earnRules().getFirst().ruleType());
        assertEquals("GROCERIES", def.earnRules().getFirst().category());
        assertNotNull(def.earnRules().getFirst().cap());
        assertEquals(6000, def.earnRules().getFirst().cap().amountUsd());
        assertEquals("ANNUAL", def.earnRules().getFirst().cap().period());
    }

    @Test
    void parse_cardWithCredits_succeeds() throws Exception {
        String json = """
            {
              "dslVersion": "carddsl.v0.1",
              "cardId": "amex-gold",
              "issuer": "Amex",
              "name": "Amex Gold",
              "segment": "PERSONAL",
              "annualFeeUsd": 250,
              "rewardCurrencyType": "BANK_MR",
              "earnRules": [],
              "credits": [
                {"ruleType": "CREDIT", "name": "Dining", "amountUsd": 10, "period": "MONTHLY", "assumedUtilizationDefault": 1.0}
              ],
              "sources": ["https://example.com/amex-gold"],
              "asOfDate": "2025-02-17"
            }
            """;
        CardDefinition def = new CardDefinitionParser().parse(json);
        assertEquals(1, def.credits().size());
        assertEquals("CREDIT", def.credits().getFirst().ruleType());
        assertEquals("Dining", def.credits().getFirst().name());
        assertEquals(10, def.credits().getFirst().amountUsd());
        assertEquals("MONTHLY", def.credits().getFirst().period());
        assertEquals(1.0, def.credits().getFirst().assumedUtilizationDefault());
    }
}
