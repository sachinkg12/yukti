package io.yukti.catalog;

import io.yukti.catalog.dsl.CardDefinition;
import io.yukti.catalog.dsl.CreditRule;
import io.yukti.catalog.dsl.EarnRule;
import io.yukti.catalog.util.CategoryMapping;
import io.yukti.catalog.util.CurrencyMapping;
import io.yukti.catalog.validation.ValidationError;
import io.yukti.catalog.validation.ValidationErrorItem;
import io.yukti.catalog.validation.ValidationResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates CardDefinitions (DSL v0.1) with explicit error codes and locations.
 */
public final class CatalogValidator {

    private static final String SUPPORTED_DSL = "carddsl.v0.1";

    /**
     * Validates a single card; returns list of errors with locations.
     */
    public List<ValidationErrorItem> validateCard(CardDefinition def) {
        List<ValidationErrorItem> out = new ArrayList<>();
        String cardId = def.cardId();
        String loc = "cardId=" + (cardId != null ? cardId : "?");

        if (cardId == null || cardId.isBlank()) {
            out.add(new ValidationErrorItem(ValidationError.MISSING_REQUIRED_FIELD, "cardId is required", loc));
            return out;
        }

        if (!SUPPORTED_DSL.equals(def.dslVersion())) {
            out.add(new ValidationErrorItem(ValidationError.UNSUPPORTED_DSL_VERSION,
                "Unsupported dslVersion: " + def.dslVersion() + ", expected " + SUPPORTED_DSL,
                "cards/" + cardId + ".json:dslVersion"));
        }

        if (def.annualFeeUsd() < 0) {
            out.add(new ValidationErrorItem(ValidationError.NEGATIVE_AMOUNT,
                "annualFeeUsd must be >= 0", "cards/" + cardId + ".json:annualFeeUsd"));
        }

        if (!CurrencyMapping.isValidCurrency(def.rewardCurrencyType())) {
            out.add(new ValidationErrorItem(ValidationError.INVALID_ENUM,
                "Invalid rewardCurrencyType: " + def.rewardCurrencyType(),
                "cards/" + cardId + ".json:rewardCurrencyType"));
        }

        if (def.sources() == null || def.sources().isEmpty()) {
            out.add(new ValidationErrorItem(ValidationError.MISSING_SOURCES,
                "sources[] is required and must be non-empty", "cards/" + cardId + ".json:sources"));
        }

        if (def.asOfDate() == null || def.asOfDate().isBlank()) {
            out.add(new ValidationErrorItem(ValidationError.MISSING_AS_OF_DATE,
                "asOfDate is required (YYYY-MM-DD)", "cards/" + cardId + ".json:asOfDate"));
        } else if (!isValidAsOfDate(def.asOfDate())) {
            out.add(new ValidationErrorItem(ValidationError.MISSING_AS_OF_DATE,
                "asOfDate must be YYYY-MM-DD, got: " + def.asOfDate(), "cards/" + cardId + ".json:asOfDate"));
        }

        List<EarnRule> earnRules = def.earnRules();
        for (int i = 0; i < earnRules.size(); i++) {
            EarnRule rule = earnRules.get(i);
            String ruleLoc = "cards/" + cardId + ".json:earnRules[" + i + "]";
            if (!"EARN_MULTIPLIER".equals(rule.ruleType())) {
                out.add(new ValidationErrorItem(ValidationError.MISSING_REQUIRED_FIELD,
                    "ruleType must be EARN_MULTIPLIER", ruleLoc + ".ruleType"));
            }
            if (rule.category() != null && !CategoryMapping.isValidCategoryName(rule.category())) {
                out.add(new ValidationErrorItem(ValidationError.INVALID_CATEGORY,
                    "Unknown category: " + rule.category() + "; must be one of GROCERIES, DINING, GAS, TRAVEL, ONLINE, OTHER",
                    ruleLoc + ".category"));
            }
            if (rule.multiplier() != null && rule.multiplier().doubleValue() <= 0) {
                out.add(new ValidationErrorItem(ValidationError.NEGATIVE_AMOUNT,
                    "multiplier must be > 0", ruleLoc + ".multiplier"));
            }
            if (rule.cap() != null) {
                if (rule.cap().amountUsd() <= 0) {
                    out.add(new ValidationErrorItem(ValidationError.INVALID_CAP,
                        "cap.amountUsd must be > 0", ruleLoc + ".cap.amountUsd"));
                }
                String p = rule.cap().period();
                if (p == null || (!"ANNUAL".equalsIgnoreCase(p) && !"MONTHLY".equalsIgnoreCase(p))) {
                    out.add(new ValidationErrorItem(ValidationError.INVALID_CAP,
                        "cap.period must be ANNUAL or MONTHLY", ruleLoc + ".cap.period"));
                }
            }
            if (rule.fallbackMultiplier() != null && rule.fallbackMultiplier().doubleValue() <= 0) {
                out.add(new ValidationErrorItem(ValidationError.INVALID_CAP,
                    "fallbackMultiplier must be > 0 if present", ruleLoc + ".fallbackMultiplier"));
            }
        }

        List<CreditRule> credits = def.credits();
        for (int i = 0; i < credits.size(); i++) {
            CreditRule c = credits.get(i);
            String credLoc = "cards/" + cardId + ".json:credits[" + i + "]";
            if (!"CREDIT".equals(c.ruleType())) {
                out.add(new ValidationErrorItem(ValidationError.MISSING_REQUIRED_FIELD,
                    "ruleType must be CREDIT", credLoc + ".ruleType"));
            }
            if (c.amountUsd() < 0) {
                out.add(new ValidationErrorItem(ValidationError.NEGATIVE_AMOUNT,
                    "amountUsd must be >= 0", credLoc + ".amountUsd"));
            }
            double u = c.assumedUtilizationDefault();
            if (u < 0 || u > 1) {
                out.add(new ValidationErrorItem(ValidationError.INVALID_UTILIZATION,
                    "assumedUtilizationDefault must be in [0,1], got " + u, credLoc + ".assumedUtilizationDefault"));
            }
        }

        return out;
    }

    /**
     * Validates all cards; checks duplicate cardIds. Returns all errors.
     */
    public List<ValidationErrorItem> validateCatalog(Iterable<CardDefinition> definitions) {
        List<ValidationErrorItem> all = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (CardDefinition def : definitions) {
            if (def.cardId() != null && !def.cardId().isBlank()) {
                if (!seenIds.add(def.cardId())) {
                    all.add(new ValidationErrorItem(ValidationError.DUPLICATE_CARD_ID,
                        "Duplicate cardId: " + def.cardId(), "cards/" + def.cardId() + ".json"));
                }
            }
            all.addAll(validateCard(def));
        }
        return all;
    }

    private static boolean isValidAsOfDate(String s) {
        if (s == null || s.length() != 10) return false;
        if (s.charAt(4) != '-' || s.charAt(7) != '-') return false;
        for (int i = 0; i < 10; i++) {
            if (i == 4 || i == 7) continue;
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        int y = Integer.parseInt(s.substring(0, 4));
        int m = Integer.parseInt(s.substring(5, 7));
        int d = Integer.parseInt(s.substring(8, 10));
        return y >= 2000 && y <= 2100 && m >= 1 && m <= 12 && d >= 1 && d <= 31;
    }

    /** Backward compat: validate single card and return ValidationResult (no locations). */
    public ValidationResult validateSingle(CardDefinition def) {
        ValidationResult result = new ValidationResult();
        for (ValidationErrorItem item : validateCard(def)) {
            result.add(item.code(), item.message());
        }
        return result;
    }

    /** Validate a collection and return ValidationResult (no locations). */
    public ValidationResult validate(Iterable<CardDefinition> definitions) {
        ValidationResult result = new ValidationResult();
        for (ValidationErrorItem item : validateCatalog(definitions)) {
            result.add(item.code(), item.message());
        }
        return result;
    }
}
