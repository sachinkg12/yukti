package io.yukti.engine.explainability.llm;

import io.yukti.core.explainability.LlmProvider;

public final class GeminiFlashFactory implements LlmProviderFactory {
    @Override public LlmProviderId id() { return LlmProviderId.GOOGLE_GEMINI_FLASH; }

    @Override public LlmProvider createOrNull() {
        return GeminiLlmProvider.fromEnvOrNull("gemini-2.5-flash");
    }
}
