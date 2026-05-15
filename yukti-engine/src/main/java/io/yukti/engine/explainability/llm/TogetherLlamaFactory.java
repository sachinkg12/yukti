package io.yukti.engine.explainability.llm;

import io.yukti.core.explainability.LlmProvider;

public final class TogetherLlamaFactory implements LlmProviderFactory {
    @Override public LlmProviderId id() { return LlmProviderId.TOGETHER_LLAMA_70B; }

    @Override public LlmProvider createOrNull() {
        return TogetherLlmProvider.fromEnvOrNull("meta-llama/Llama-3.3-70B-Instruct-Turbo");
    }
}
