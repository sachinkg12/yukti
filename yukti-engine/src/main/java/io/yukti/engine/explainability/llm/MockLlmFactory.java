package io.yukti.engine.explainability.llm;

import io.yukti.core.explainability.LlmProvider;

import java.util.List;

public final class MockLlmFactory implements LlmProviderFactory {
    @Override public LlmProviderId id() { return LlmProviderId.MOCK; }

    @Override public LlmProvider createOrNull() {
        return new MockLlmProvider(List.of("[]"));
    }
}
