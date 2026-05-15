package io.yukti.engine.explainability.llm;

import io.yukti.core.explainability.LlmProvider;

public final class AnthropicHaikuFactory implements LlmProviderFactory {
    @Override public LlmProviderId id() { return LlmProviderId.ANTHROPIC_CLAUDE_HAIKU; }

    @Override public LlmProvider createOrNull() {
        return AnthropicLlmProvider.fromEnvOrNull("claude-haiku-4-5");
    }
}
