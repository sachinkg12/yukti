package io.yukti.catalog.validation;

/**
 * Explicit validation error codes for catalog loading.
 */
public enum ValidationError {
    MISSING_REQUIRED_FIELD,
    INVALID_ENUM,
    NEGATIVE_AMOUNT,
    INVALID_CAP,
    INVALID_UTILIZATION,
    DUPLICATE_CARD_ID,
    UNSUPPORTED_DSL_VERSION,
    CHECKSUM_MISMATCH,
    INDEX_NOT_SORTED,
    CARD_NOT_LISTED_IN_INDEX,
    INDEX_ENTRY_NOT_FOUND,
    INVALID_CATEGORY,
    MISSING_CARD_FILE,
    MALFORMED_JSON,
    INVALID_EARN_RULE_TYPE,
    INVALID_CREDIT_RULE_TYPE,
    INVALID_REWARD_CURRENCY,
    UTILIZATION_OUT_OF_RANGE,
    /** Card missing required sources[] (non-empty). */
    MISSING_SOURCES,
    /** Card missing or invalid asOfDate (required, YYYY-MM-DD). */
    MISSING_AS_OF_DATE
}
