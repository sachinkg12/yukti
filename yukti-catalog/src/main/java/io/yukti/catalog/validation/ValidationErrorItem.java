package io.yukti.catalog.validation;

/**
 * A single validation error with code, deterministic message, and location.
 */
public record ValidationErrorItem(
    ValidationError code,
    String message,
    String location
) {}
