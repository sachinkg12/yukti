package io.yukti.catalog;

import io.yukti.catalog.validation.ValidationError;

/**
 * Thrown when catalog load fails (checksum mismatch, validation, etc.).
 */
public final class CatalogException extends Exception {
    private final ValidationError code;

    public CatalogException(ValidationError code, String message) {
        super(message);
        this.code = code;
    }

    public CatalogException(ValidationError code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ValidationError getCode() {
        return code;
    }
}
