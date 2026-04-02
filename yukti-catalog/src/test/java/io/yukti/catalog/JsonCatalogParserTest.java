package io.yukti.catalog;

import io.yukti.core.api.Card;
import io.yukti.core.api.Catalog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonCatalogParserTest {

    @Test
    void parse_loadsCardsAndPolicies() throws Exception {
        try (var in = getClass().getClassLoader().getResourceAsStream("catalog/catalog-v1.json")) {
            assertNotNull(in);
            Catalog catalog = new JsonCatalogParser().parse(in);
            assertFalse(catalog.cards().isEmpty());
            assertFalse(catalog.valuationPolicies().isEmpty());
        }
    }

    @Test
    void parse_cardsHaveRules() throws Exception {
        Catalog catalog = new ClasspathCatalogSource("catalog/catalog-v1.json").load("1.0");
        for (Card c : catalog.cards()) {
            assertNotNull(c.id());
            assertNotNull(c.displayName());
            assertNotNull(c.rules());
        }
    }
}
