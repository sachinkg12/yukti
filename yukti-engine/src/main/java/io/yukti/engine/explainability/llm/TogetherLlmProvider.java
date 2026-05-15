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
 * Together AI provider. Used for open weight models like Llama and Qwen via the
 * OpenAI compatible chat completions endpoint.
 *
 * <p>The default model is Meta Llama 3.1 70B Instruct, which is a common open weight
 * baseline for hallucination evaluation.
 */
public final class TogetherLlmProvider implements LlmProvider {

    public static final String ENV_TOGETHER_API_KEY = "TOGETHER_API_KEY";
    public static final String DEFAULT_MODEL = "meta-llama/Meta-Llama-3.1-70B-Instruct-Turbo";
    private static final String CHAT_URL = "https://api.together.xyz/v1/chat/completions";

    private static final ObjectMapper OM = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build();

    private final String apiKey;
    private final String model;

    public TogetherLlmProvider(String apiKey) {
        this(apiKey, DEFAULT_MODEL);
    }

    public TogetherLlmProvider(String apiKey, String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Together API key must be non-blank");
        }
        this.apiKey = apiKey.trim();
        this.model = model != null && !model.isBlank() ? model : DEFAULT_MODEL;
    }

    public static TogetherLlmProvider fromEnvOrNull() {
        String key = System.getenv(ENV_TOGETHER_API_KEY);
        if (key == null || key.isBlank()) return null;
        return new TogetherLlmProvider(key);
    }

    public static TogetherLlmProvider fromEnvOrNull(String model) {
        String key = System.getenv(ENV_TOGETHER_API_KEY);
        if (key == null || key.isBlank()) return null;
        return new TogetherLlmProvider(key, model);
    }

    public String getModel() {
        return model;
    }

    @Override
    public String generate(String prompt) {
        Map<String, Object> payload = Map.of(
            "model", model,
            "messages", List.of(Map.of("role", "user", "content", prompt))
        );
        String body;
        try {
            body = OM.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build Together request", e);
        }
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(CHAT_URL))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .timeout(Duration.ofSeconds(60))
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new RuntimeException("Together API error: " + response.statusCode() + " " + response.body());
            }
            JsonNode root = OM.readTree(response.body());
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new RuntimeException("Together API returned no choices");
            }
            JsonNode content = choices.get(0).path("message").path("content");
            if (content.isMissingNode() || !content.isTextual()) {
                throw new RuntimeException("Together API response missing message.content");
            }
            return content.asText();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Together request failed: " + e.getMessage(), e);
        }
    }
}
