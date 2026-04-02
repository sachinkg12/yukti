package io.yukti.core.explainability;

/**
 * Abstraction for LLM generation. Stub for tests; real provider optional.
 */
public interface LlmProvider {
    String generate(String prompt);
}
