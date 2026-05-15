package io.yukti.engine.explainability.llm;

import io.yukti.core.explainability.LlmProvider;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of {@link LlmProviderFactory} instances. Discovery is by enum id, not by string.
 *
 * <p>Open Closed: add a new provider by registering a new {@link LlmProviderFactory}.
 * No call site needs to change to support a new backend.
 */
public final class LlmProviderRegistry {

    private final Map<LlmProviderId, LlmProviderFactory> factories;

    public LlmProviderRegistry(List<LlmProviderFactory> factories) {
        this.factories = new EnumMap<>(LlmProviderId.class);
        for (LlmProviderFactory f : factories) {
            this.factories.put(f.id(), f);
        }
    }

    /** Default registry with all built in factories. */
    public static LlmProviderRegistry defaultRegistry() {
        return new LlmProviderRegistry(List.of(
            new OpenAiGpt4oFactory(),
            new OpenAiGpt4oMiniFactory(),
            new AnthropicSonnetFactory(),
            new AnthropicHaikuFactory(),
            new GeminiFlashFactory(),
            new GeminiProFactory(),
            new TogetherLlamaFactory(),
            new TogetherQwenFactory(),
            new MockLlmFactory()
        ));
    }

    /** Return a provider for the given id, or empty if the credentials are not present. */
    public Optional<LlmProvider> get(LlmProviderId id) {
        LlmProviderFactory f = factories.get(id);
        if (f == null) return Optional.empty();
        LlmProvider p = f.createOrNull();
        return Optional.ofNullable(p);
    }

    /** Ids for which we have a registered factory. */
    public List<LlmProviderId> registeredIds() {
        return List.copyOf(factories.keySet());
    }
}
