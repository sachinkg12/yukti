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
 * Google Gemini provider via the Generative Language API. Reads the API key from
 * env {@value #ENV_GOOGLE_API_KEY}.
 *
 * <p>Free tier and paid keys are both supported. Get a key at
 * <a href="https://aistudio.google.com/apikey">aistudio.google.com/apikey</a>.
 */
public final class GeminiLlmProvider implements LlmProvider {

    public static final String ENV_GOOGLE_API_KEY = "GOOGLE_API_KEY";
    public static final String DEFAULT_MODEL = "gemini-2.5-flash";
    private static final String API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";

    private static final ObjectMapper OM = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build();

    private final String apiKey;
    private final String model;

    public GeminiLlmProvider(String apiKey) {
        this(apiKey, DEFAULT_MODEL);
    }

    public GeminiLlmProvider(String apiKey, String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Google API key must be non-blank");
        }
        this.apiKey = apiKey.trim();
        this.model = model != null && !model.isBlank() ? model : DEFAULT_MODEL;
    }

    public static GeminiLlmProvider fromEnvOrNull() {
        String key = System.getenv(ENV_GOOGLE_API_KEY);
        if (key == null || key.isBlank()) return null;
        return new GeminiLlmProvider(key);
    }

    public static GeminiLlmProvider fromEnvOrNull(String model) {
        String key = System.getenv(ENV_GOOGLE_API_KEY);
        if (key == null || key.isBlank()) return null;
        return new GeminiLlmProvider(key, model);
    }

    public String getModel() {
        return model;
    }

    @Override
    public String generate(String prompt) {
        Map<String, Object> payload = Map.of(
            "contents", List.of(Map.of(
                "parts", List.of(Map.of("text", prompt))
            ))
        );
        String body;
        try {
            body = OM.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build Gemini request", e);
        }
        String url = API_BASE + model + ":generateContent?key=" + apiKey;
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(120))
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new RuntimeException("Gemini API error: " + response.statusCode() + " " + response.body());
            }
            JsonNode root = OM.readTree(response.body());
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                throw new RuntimeException("Gemini API returned no candidates");
            }
            JsonNode parts = candidates.get(0).path("content").path("parts");
            if (!parts.isArray() || parts.isEmpty()) {
                throw new RuntimeException("Gemini API response missing content.parts");
            }
            JsonNode text = parts.get(0).path("text");
            if (text.isMissingNode() || !text.isTextual()) {
                throw new RuntimeException("Gemini API response missing parts[0].text");
            }
            return text.asText();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Gemini request failed: " + e.getMessage(), e);
        }
    }
}
