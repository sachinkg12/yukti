package io.yukti.engine.explainability.llm;

import io.yukti.core.explainability.LlmProvider;

public final class AnthropicSonnetFactory implements LlmProviderFactory {
    @Override public LlmProviderId id() { return LlmProviderId.ANTHROPIC_CLAUDE_SONNET; }

    @Override public LlmProvider createOrNull() {
        return AnthropicLlmProvider.fromEnvOrNull("claude-sonnet-4-5");
    }
}
