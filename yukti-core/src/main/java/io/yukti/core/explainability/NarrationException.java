package io.yukti.core.explainability;

/**
 * Thrown when AI narration fails validation or generation.
 */
public class NarrationException extends Exception {
    public NarrationException(String message) {
        super(message);
    }

    public NarrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
