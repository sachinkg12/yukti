package io.yukti.engine.explainability;

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

/**
 * Real LLM provider using OpenAI Chat Completions API.
 * API key from env {@value #ENV_OPENAI_API_KEY}. Production uses only this provider when LLM is enabled (no stubs).
 */
public final class OpenAiLlmProvider implements LlmProvider {

    public static final String ENV_OPENAI_API_KEY = "OPENAI_API_KEY";
    private static final String CHAT_COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_MODEL = "gpt-4o-mini";
    private static final ObjectMapper OM = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build();

    private final String apiKey;
    private final String model;

    public OpenAiLlmProvider(String apiKey) {
        this(apiKey, DEFAULT_MODEL);
    }

    public OpenAiLlmProvider(String apiKey, String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("OpenAI API key must be non-blank");
        }
        this.apiKey = apiKey.trim();
        this.model = model != null && !model.isBlank() ? model : DEFAULT_MODEL;
    }

    /**
     * Create provider from env OPENAI_API_KEY, or null if not set.
     */
    public static OpenAiLlmProvider fromEnvOrNull() {
        String key = System.getenv(ENV_OPENAI_API_KEY);
        if (key == null || key.isBlank()) return null;
        return new OpenAiLlmProvider(key);
    }

    @Override
    public String generate(String prompt) {
        String body;
        try {
            body = OM.writeValueAsString(new RequestBody(model, List.of(new Message("user", prompt))));
        } catch (Exception e) {
            throw new RuntimeException("Failed to build request", e);
        }
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(CHAT_COMPLETIONS_URL))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .timeout(Duration.ofSeconds(60))
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new RuntimeException("OpenAI API error: " + response.statusCode() + " " + response.body());
            }
            JsonNode root = OM.readTree(response.body());
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new RuntimeException("OpenAI API returned no choices");
            }
            JsonNode message = choices.get(0).path("message");
            JsonNode content = message.path("content");
            if (content.isMissingNode() || !content.isTextual()) {
                throw new RuntimeException("OpenAI API response missing message.content");
            }
            return content.asText();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("OpenAI request failed: " + e.getMessage(), e);
        }
    }

    private record RequestBody(String model, List<Message> messages) {}
    private record Message(String role, String content) {}
}
