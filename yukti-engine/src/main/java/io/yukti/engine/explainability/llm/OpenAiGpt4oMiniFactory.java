package io.yukti.engine.explainability.llm;

import io.yukti.core.explainability.LlmProvider;
import io.yukti.engine.explainability.OpenAiLlmProvider;

public final class OpenAiGpt4oMiniFactory implements LlmProviderFactory {
    @Override public LlmProviderId id() { return LlmProviderId.OPENAI_GPT4O_MINI; }

    @Override public LlmProvider createOrNull() {
        String key = System.getenv(OpenAiLlmProvider.ENV_OPENAI_API_KEY);
        if (key == null || key.isBlank()) return null;
        return new OpenAiLlmProvider(key, "gpt-4o-mini");
    }
}
