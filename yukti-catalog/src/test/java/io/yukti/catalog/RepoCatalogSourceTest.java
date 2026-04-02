package io.yukti.catalog;

import io.yukti.catalog.dsl.CardDefinition;
import io.yukti.catalog.dsl.CatalogIndex;
import io.yukti.catalog.validation.ValidationError;
import io.yukti.core.api.Card;
import io.yukti.core.api.Catalog;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class RepoCatalogSourceTest {

    @Test
    void load_loadsSourceBackedCards() throws Exception {
        var source = new RepoCatalogSource("catalog/v1", DefaultValuationPolicies.defaults());
        Catalog catalog = source.load("v1");
        assertTrue(catalog.cards().size() >= 15, "Catalog must have at least 15 source-backed cards");
        assertFalse(catalog.valuationPolicies().isEmpty());
    }

    @Test
    void load_cardsHaveRules() throws Exception {
        var source = new RepoCatalogSource("catalog/v1", DefaultValuationPolicies.defaults());
        Catalog catalog = source.load("v1");
        for (Card c : catalog.cards()) {
            assertNotNull(c.id());
            assertNotNull(c.displayName());
            assertNotNull(c.rules());
            assertFalse(c.rules().isEmpty(), "Card " + c.id() + " should have rules");
        }
    }

    @Test
    void load_rewardCurrencyTypesPresent() throws Exception {
        var source = new RepoCatalogSource("catalog/v1", DefaultValuationPolicies.defaults());
        Catalog catalog = source.load("v1");
        var ids = catalog.cards().stream().map(Card::id).sorted().toList();
        assertTrue(ids.contains("citi-double-cash"));
        assertTrue(ids.contains("amex-everyday"));
        assertTrue(ids.contains("chase-ba-visa"));
        assertTrue(ids.contains("chase-sapphire-preferred"));
    }

    @Test
    void load_indexNotSortedFailsHard() throws Exception {
        var source = new RepoCatalogSource("catalog/v1-unsorted-index", DefaultValuationPolicies.defaults());
        CatalogException ex = assertThrows(CatalogException.class, () -> source.load("v1"),
            "Index not sorted must fail hard");
        assertEquals(ValidationError.INDEX_NOT_SORTED, ex.getCode());
    }

    @Test
    void load_checksumMismatchFailsHard() throws Exception {
        var source = new RepoCatalogSource("catalog/v1-bad-checksum", DefaultValuationPolicies.defaults());
        CatalogException ex = assertThrows(CatalogException.class, () -> source.load("v1"),
            "Checksum mismatch must fail hard");
        assertEquals(ValidationError.CHECKSUM_MISMATCH, ex.getCode());
    }

    @Test
    void load_deterministicOrder() throws Exception {
        var source = new RepoCatalogSource("catalog/v1", DefaultValuationPolicies.defaults());
        Catalog c1 = source.load("v1");
        Catalog c2 = source.load("v1");
        assertTrue(c1.cards().size() >= 15);
        assertEquals(c1.cards().size(), c2.cards().size());
        var ids1 = c1.cards().stream().map(Card::id).toList();
        var ids2 = c2.cards().stream().map(Card::id).toList();
        assertEquals(ids1, ids2, "Load must be deterministic: same card order");
    }

    @Test
    void everyCardHasSourcesAndAsOfDate() throws Exception {
        var indexPath = "catalog/v1/index.json";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(indexPath)) {
            Objects.requireNonNull(in, "index not found: " + indexPath);
            CatalogIndex index = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                new String(in.readAllBytes(), StandardCharsets.UTF_8), CatalogIndex.class);
            CardDefinitionParser parser = new CardDefinitionParser();
            for (CatalogIndex.IndexEntry entry : index.entries()) {
                String resPath = "catalog/v1/" + (entry.path() != null && !entry.path().isBlank() ? entry.path() : "cards/" + entry.cardId() + ".json");
                try (InputStream cardIn = getClass().getClassLoader().getResourceAsStream(resPath)) {
                    Objects.requireNonNull(cardIn, "card file not found: " + resPath);
                    CardDefinition def = parser.parse(new String(cardIn.readAllBytes(), StandardCharsets.UTF_8));
                    assertFalse(def.sources() == null || def.sources().isEmpty(),
                        "Card " + def.cardId() + " must have non-empty sources[]");
                    assertTrue(def.asOfDate() != null && !def.asOfDate().isBlank(),
                        "Card " + def.cardId() + " must have asOfDate (YYYY-MM-DD)");
                }
            }
        }
    }
}
