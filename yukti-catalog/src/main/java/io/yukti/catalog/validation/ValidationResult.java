package io.yukti.catalog.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of catalog validation. Contains errors with explicit codes.
 */
public final class ValidationResult {
    private final List<ValidationError> errors = new ArrayList<>();
    private final List<String> messages = new ArrayList<>();

    public void add(ValidationError code, String message) {
        errors.add(code);
        messages.add(message);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public List<ValidationError> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public List<String> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public static ValidationResult ok() {
        return new ValidationResult();
    }
}
