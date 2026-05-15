package io.yukti.engine.explainability.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yukti.core.explainability.LlmProvider;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Anthropic Claude Messages API provider. Reads API key from env {@value #ENV_ANTHROPIC_API_KEY}.
 *
 * <p>Used in multi LLM hallucination evaluation. The provider is stateless and safe to
 * share across threads.
 */
public final class AnthropicLlmProvider implements LlmProvider {

    public static final String ENV_ANTHROPIC_API_KEY = "ANTHROPIC_API_KEY";
    public static final String DEFAULT_MODEL = "claude-sonnet-4-5";
    private static final String MESSAGES_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int DEFAULT_MAX_TOKENS = 4096;

    private static final ObjectMapper OM = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build();

    private final String apiKey;
    private final String model;
    private final int maxTokens;

    public AnthropicLlmProvider(String apiKey) {
        this(apiKey, DEFAULT_MODEL, DEFAULT_MAX_TOKENS);
    }

    public AnthropicLlmProvider(String apiKey, String model, int maxTokens) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Anthropic API key must be non-blank");
        }
        this.apiKey = apiKey.trim();
        this.model = model != null && !model.isBlank() ? model : DEFAULT_MODEL;
        this.maxTokens = maxTokens > 0 ? maxTokens : DEFAULT_MAX_TOKENS;
    }

    public static AnthropicLlmProvider fromEnvOrNull() {
        String key = System.getenv(ENV_ANTHROPIC_API_KEY);
        if (key == null || key.isBlank()) return null;
        return new AnthropicLlmProvider(key);
    }

    public static AnthropicLlmProvider fromEnvOrNull(String model) {
        String key = System.getenv(ENV_ANTHROPIC_API_KEY);
        if (key == null || key.isBlank()) return null;
        return new AnthropicLlmProvider(key, model, DEFAULT_MAX_TOKENS);
    }

    public String getModel() {
        return model;
    }

    @Override
    public String generate(String prompt) {
        Map<String, Object> payload = Map.of(
            "model", model,
            "max_tokens", maxTokens,
            "messages", List.of(Map.of("role", "user", "content", prompt))
        );
        String body;
        try {
            body = OM.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build Anthropic request", e);
        }
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(MESSAGES_URL))
            .header("Content-Type", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .timeout(Duration.ofSeconds(60))
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new RuntimeException("Anthropic API error: " + response.statusCode() + " " + response.body());
            }
            JsonNode root = OM.readTree(response.body());
            JsonNode content = root.path("content");
            if (!content.isArray() || content.isEmpty()) {
                throw new RuntimeException("Anthropic API returned no content");
            }
            JsonNode text = content.get(0).path("text");
            if (text.isMissingNode() || !text.isTextual()) {
                throw new RuntimeException("Anthropic API response missing content[0].text");
            }
            return text.asText();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Anthropic request failed: " + e.getMessage(), e);
        }
    }
}
