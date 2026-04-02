package io.yukti.engine.explainability;

import io.yukti.core.explainability.LlmProvider;

/**
 * Stub LLM provider for tests. No network. Configurable behavior.
 */
public final class StubLlmProvider implements LlmProvider {
    private final String response;
    private final RuntimeException toThrow;

    public StubLlmProvider(String response) {
        this.response = response;
        this.toThrow = null;
    }

    public StubLlmProvider(RuntimeException toThrow) {
        this.response = null;
        this.toThrow = toThrow;
    }

    @Override
    public String generate(String prompt) {
        if (toThrow != null) throw toThrow;
        return response;
    }
}
