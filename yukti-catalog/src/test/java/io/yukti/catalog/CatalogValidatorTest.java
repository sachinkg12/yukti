package io.yukti.catalog;

import io.yukti.catalog.dsl.CardDefinition;
import io.yukti.catalog.dsl.CreditRule;
import io.yukti.catalog.dsl.EarnRule;
import io.yukti.catalog.validation.ValidationError;
import io.yukti.catalog.validation.ValidationResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CatalogValidatorTest {

    private static final List<String> REQUIRED_SOURCES = List.of("https://example.com/card");
    private static final String REQUIRED_AS_OF = "2025-02-17";

    private static CardDefinition validCard() {
        return new CardDefinition(
            "carddsl.v0.1",
            "test-card",
            "Test",
            "Test Card",
            "PERSONAL",
            0,
            "USD_CASH",
            List.of(new EarnRule("EARN_MULTIPLIER", "OTHER", BigDecimal.valueOf(0.02), null, null, null, null, null)),
            List.of(),
            REQUIRED_SOURCES,
            REQUIRED_AS_OF,
            List.of()
        );
    }

    @Test
    void validateSingle_success() {
        var validator = new CatalogValidator();
        ValidationResult r = validator.validateSingle(validCard());
        assertTrue(r.isValid(), "Valid card should pass: " + r.getMessages());
    }

    @Test
    void validate_duplicateCardId_fails() {
        var validator = new CatalogValidator();
        var defs = List.of(
            validCard(),
            new CardDefinition("carddsl.v0.1", "test-card", "X", "X", "PERSONAL", 0, "USD_CASH", List.of(), List.of(), REQUIRED_SOURCES, REQUIRED_AS_OF, List.of())
        );
        ValidationResult r = validator.validate(defs);
        assertFalse(r.isValid());
        assertTrue(r.getErrors().contains(ValidationError.DUPLICATE_CARD_ID));
    }

    @Test
    void validate_invalidCap_fails() {
        var def = new CardDefinition(
            "carddsl.v0.1",
            "test-card",
            "Test",
            "Test",
            "PERSONAL",
            0,
            "USD_CASH",
            List.of(new EarnRule("EARN_MULTIPLIER", "OTHER", BigDecimal.ONE, new io.yukti.catalog.dsl.CapObject(0, "ANNUAL"), null, null, null, null)),
            List.of(),
            REQUIRED_SOURCES,
            REQUIRED_AS_OF,
            List.of()
        );
        var validator = new CatalogValidator();
        ValidationResult r = validator.validateSingle(def);
        assertFalse(r.isValid());
        assertTrue(r.getErrors().contains(ValidationError.INVALID_CAP));
    }

    @Test
    void validate_utilizationOutOfRange_fails() {
        var def = new CardDefinition(
            "carddsl.v0.1",
            "test-card",
            "Test",
            "Test",
            "PERSONAL",
            0,
            "USD_CASH",
            List.of(),
            List.of(new CreditRule("CREDIT", "Test Credit", 50, "ANNUAL", 1.5)),
            REQUIRED_SOURCES,
            REQUIRED_AS_OF,
            List.of()
        );
        var validator = new CatalogValidator();
        ValidationResult r = validator.validateSingle(def);
        assertFalse(r.isValid());
        assertTrue(r.getErrors().contains(ValidationError.INVALID_UTILIZATION));
    }

    @Test
    void validate_utilizationNegative_fails() {
        var def = new CardDefinition(
            "carddsl.v0.1",
            "test-card",
            "Test",
            "Test",
            "PERSONAL",
            0,
            "USD_CASH",
            List.of(),
            List.of(new CreditRule("CREDIT", "Test Credit", 50, "ANNUAL", -0.1)),
            REQUIRED_SOURCES,
            REQUIRED_AS_OF,
            List.of()
        );
        var validator = new CatalogValidator();
        ValidationResult r = validator.validateSingle(def);
        assertFalse(r.isValid());
        assertTrue(r.getErrors().contains(ValidationError.INVALID_UTILIZATION));
    }

    @Test
    void validate_utilizationOne_succeeds() {
        var def = new CardDefinition(
            "carddsl.v0.1",
            "test-card",
            "Test",
            "Test",
            "PERSONAL",
            0,
            "USD_CASH",
            List.of(),
            List.of(new CreditRule("CREDIT", "Test Credit", 50, "ANNUAL", 1.0)),
            REQUIRED_SOURCES,
            REQUIRED_AS_OF,
            List.of()
        );
        var validator = new CatalogValidator();
        ValidationResult r = validator.validateSingle(def);
        assertTrue(r.isValid());
    }

    @Test
    void validate_invalidRewardCurrency_fails() {
        var def = new CardDefinition(
            "carddsl.v0.1",
            "test-card",
            "Test",
            "Test",
            "PERSONAL",
            0,
            "INVALID_CURRENCY_XYZ",
            List.of(new EarnRule("EARN_MULTIPLIER", "OTHER", BigDecimal.ONE, null, null, null, null, null)),
            List.of(),
            REQUIRED_SOURCES,
            REQUIRED_AS_OF,
            List.of()
        );
        var validator = new CatalogValidator();
        ValidationResult r = validator.validateSingle(def);
        assertFalse(r.isValid());
        assertTrue(r.getErrors().contains(ValidationError.INVALID_ENUM));
    }

    @Test
    void validateCard_unsupportedDslVersion_fails() {
        var def = new CardDefinition(
            "carddsl.v0.0",
            "test-card",
            "Test",
            "Test",
            "PERSONAL",
            0,
            "USD_CASH",
            List.of(new EarnRule("EARN_MULTIPLIER", "OTHER", BigDecimal.ONE, null, null, null, null, null)),
            List.of(),
            REQUIRED_SOURCES,
            REQUIRED_AS_OF,
            List.of()
        );
        var validator = new CatalogValidator();
        var items = validator.validateCard(def);
        assertFalse(items.isEmpty());
        assertTrue(items.stream().anyMatch(e -> e.code() == ValidationError.UNSUPPORTED_DSL_VERSION));
    }

    @Test
    void validateCard_missingSources_fails() {
        var def = new CardDefinition(
            "carddsl.v0.1",
            "test-card",
            "Test",
            "Test",
            "PERSONAL",
            0,
            "USD_CASH",
            List.of(new EarnRule("EARN_MULTIPLIER", "OTHER", BigDecimal.ONE, null, null, null, null, null)),
            List.of(),
            List.of(),
            REQUIRED_AS_OF,
            List.of()
        );
        var validator = new CatalogValidator();
        var items = validator.validateCard(def);
        assertTrue(items.stream().anyMatch(e -> e.code() == ValidationError.MISSING_SOURCES));
    }

    @Test
    void validateCard_missingAsOfDate_fails() {
        var def = new CardDefinition(
            "carddsl.v0.1",
            "test-card",
            "Test",
            "Test",
            "PERSONAL",
            0,
            "USD_CASH",
            List.of(new EarnRule("EARN_MULTIPLIER", "OTHER", BigDecimal.ONE, null, null, null, null, null)),
            List.of(),
            REQUIRED_SOURCES,
            "",
            List.of()
        );
        var validator = new CatalogValidator();
        var items = validator.validateCard(def);
        assertTrue(items.stream().anyMatch(e -> e.code() == ValidationError.MISSING_AS_OF_DATE));
    }

    @Test
    void validateCard_invalidCategory_fails() {
        var def = new CardDefinition(
            "carddsl.v0.1",
            "test-card",
            "Test",
            "Test",
            "PERSONAL",
            0,
            "USD_CASH",
            List.of(new EarnRule("EARN_MULTIPLIER", "INVALID_CAT", BigDecimal.ONE, null, null, null, null, null)),
            List.of(),
            REQUIRED_SOURCES,
            REQUIRED_AS_OF,
            List.of()
        );
        var validator = new CatalogValidator();
        var items = validator.validateCard(def);
        assertTrue(items.stream().anyMatch(e -> e.code() == ValidationError.INVALID_CATEGORY));
    }
}
