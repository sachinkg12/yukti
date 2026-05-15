package io.yukti.engine.explainability.llm;

import io.yukti.core.explainability.LlmProvider;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic provider for tests and offline runs. Cycles through a fixed list of
 * canned responses. Useful when API keys are not available in CI.
 */
public final class MockLlmProvider implements LlmProvider {
    private final List<String> responses;
    private final AtomicInteger index = new AtomicInteger(0);

    public MockLlmProvider(List<String> responses) {
        if (responses == null || responses.isEmpty()) {
            throw new IllegalArgumentException("responses must be non-empty");
        }
        this.responses = List.copyOf(responses);
    }

    public static MockLlmProvider single(String response) {
        return new MockLlmProvider(List.of(response));
    }

    @Override
    public String generate(String prompt) {
        int i = index.getAndIncrement() % responses.size();
        return responses.get(i);
    }
}
