package io.yukti.engine.explainability;

/**
 * Thrown when LLM claim generation fails (empty output, I/O error, etc.).
 */
public class LlmClaimException extends Exception {

    public LlmClaimException(String message) {
        super(message);
    }

    public LlmClaimException(String message, Throwable cause) {
        super(message, cause);
    }
}
