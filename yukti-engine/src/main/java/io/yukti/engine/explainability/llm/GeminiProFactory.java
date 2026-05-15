package io.yukti.engine.explainability.llm;

import io.yukti.core.explainability.LlmProvider;

public final class GeminiProFactory implements LlmProviderFactory {
    @Override public LlmProviderId id() { return LlmProviderId.GOOGLE_GEMINI_PRO; }

    @Override public LlmProvider createOrNull() {
        return GeminiLlmProvider.fromEnvOrNull("gemini-2.5-pro");
    }
}
