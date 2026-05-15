package io.yukti.engine.explainability.llm;

import io.yukti.core.explainability.LlmProvider;

/**
 * Factory for an LLM provider variant. One factory per supported provider id.
 * Returns null if the required credentials are not available.
 */
public interface LlmProviderFactory {
    LlmProviderId id();
    LlmProvider createOrNull();
}
