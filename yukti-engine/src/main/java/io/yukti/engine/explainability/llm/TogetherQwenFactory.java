package io.yukti.engine.explainability.llm;

import io.yukti.core.explainability.LlmProvider;

public final class TogetherQwenFactory implements LlmProviderFactory {
    @Override public LlmProviderId id() { return LlmProviderId.TOGETHER_QWEN_72B; }

    @Override public LlmProvider createOrNull() {
        return TogetherLlmProvider.fromEnvOrNull("Qwen/Qwen2.5-7B-Instruct-Turbo");
    }
}
