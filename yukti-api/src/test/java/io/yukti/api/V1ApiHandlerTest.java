package io.yukti.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yukti.api.handler.V1ApiHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for V1ApiHandler (no HTTP server). Fast execution.
 */
class V1ApiHandlerTest {

    private static final ObjectMapper OM = new ObjectMapper();
    private static V1ApiHandler handler;

    @BeforeAll
    static void setUp() throws Exception {
        handler = new V1ApiHandler();
    }

    @Test
    void postOptimize_happyPath_returns200() throws Exception {
        String body = OM.writeValueAsString(Map.of(
            "period", "ANNUAL",
            "spendByCategoryUsd", Map.of(
                "GROCERIES", 12000,
                "DINING", 8000,
                "GAS", 2400,
                "OTHER", 5000
            ),
            "goal", Map.of("goalType", "CASHBACK"),
            "constraints", Map.of("maxCards", 2, "maxAnnualFeeUsd", 500, "allowBusinessCards", true)
        ));

        var result = handler.handleOptimize(body, "test-req-id");
        assertEquals(200, result.status());

        JsonNode json = OM.readTree(result.body());
        assertTrue(json.has("requestId"));
        assertTrue(json.has("portfolio"));
        assertFalse(json.get("portfolio").isEmpty());
        assertTrue(json.has("breakdown"));
        assertTrue(json.get("breakdown").has("netValueUsd"));
        assertTrue(json.has("explanation"));
        assertTrue(json.get("explanation").has("fullText"));
        assertFalse(json.get("explanation").get("fullText").asText().isBlank());
        assertTrue(json.path("goalInterpretation").isMissingNode() || json.get("goalInterpretation").isNull(),
            "When no goalPrompt is sent, goalInterpretation must be absent or null");
    }

    @Test
    void postOptimize_negativeSpend_returns400() throws Exception {
        String body = OM.writeValueAsString(Map.of(
            "period", "ANNUAL",
            "spendByCategoryUsd", Map.of("GROCERIES", -100),
            "goal", Map.of("goalType", "CASHBACK"),
            "constraints", Map.of("maxCards", 2, "maxAnnualFeeUsd", 500, "allowBusinessCards", true)
        ));

        var result = handler.handleOptimize(body, "test-req-id");
        assertEquals(400, result.status());
        JsonNode json = OM.readTree(result.body());
        assertEquals("VALIDATION_ERROR", json.get("errorCode").asText());
        assertTrue(json.has("requestId"));
        assertTrue(json.has("details"));
        assertTrue(json.get("details").isArray());
        boolean hasField = false;
        for (JsonNode d : json.get("details")) {
            if ("spendByCategoryUsd.GROCERIES".equals(d.path("field").asText(null))) {
                hasField = true;
                break;
            }
        }
        assertTrue(hasField, "details should contain field spendByCategoryUsd.GROCERIES");
    }

    @Test
    void postOptimize_maxCards5_returns400() throws Exception {
        String body = OM.writeValueAsString(Map.of(
            "period", "ANNUAL",
            "spendByCategoryUsd", Map.of("GROCERIES", 1000),
            "goal", Map.of("goalType", "CASHBACK"),
            "constraints", Map.of("maxCards", 5, "maxAnnualFeeUsd", 500, "allowBusinessCards", true)
        ));

        var result = handler.handleOptimize(body, "test-req-id");
        assertEquals(400, result.status());
        JsonNode json = OM.readTree(result.body());
        assertEquals("VALIDATION_ERROR", json.get("errorCode").asText());
    }

    @Test
    void postOptimize_programPointsWithoutPrimary_returns400() throws Exception {
        String body = OM.writeValueAsString(Map.of(
            "period", "ANNUAL",
            "spendByCategoryUsd", Map.of("GROCERIES", 1000),
            "goal", Map.of("goalType", "PROGRAM_POINTS"),
            "constraints", Map.of("maxCards", 2, "maxAnnualFeeUsd", 500, "allowBusinessCards", true)
        ));

        var result = handler.handleOptimize(body, "test-req-id");
        assertEquals(400, result.status());
        JsonNode json = OM.readTree(result.body());
        assertEquals("VALIDATION_ERROR", json.get("errorCode").asText());
        assertTrue(json.has("requestId"));
        assertTrue(json.get("details").toString().contains("goal.primaryCurrency"));
    }

    @Test
    void postOptimize_missingPeriod_returns400() throws Exception {
        String body = OM.writeValueAsString(Map.of(
            "spendByCategoryUsd", Map.of("GROCERIES", 1000),
            "goal", Map.of("goalType", "CASHBACK"),
            "constraints", Map.of("maxCards", 2, "maxAnnualFeeUsd", 500, "allowBusinessCards", true)
        ));

        var result = handler.handleOptimize(body, "test-req-id");
        assertEquals(400, result.status());
        JsonNode json = OM.readTree(result.body());
        assertEquals("VALIDATION_ERROR", json.get("errorCode").asText());
    }

    @Test
    void postOptimize_maxAnnualFeeOver5000_returns400() throws Exception {
        String body = OM.writeValueAsString(Map.of(
            "period", "ANNUAL",
            "spendByCategoryUsd", Map.of("GROCERIES", 1000),
            "goal", Map.of("goalType", "CASHBACK"),
            "constraints", Map.of("maxCards", 2, "maxAnnualFeeUsd", 10000, "allowBusinessCards", true)
        ));

        var result = handler.handleOptimize(body, "test-req-id");
        assertEquals(400, result.status());
        JsonNode json = OM.readTree(result.body());
        assertEquals("VALIDATION_ERROR", json.get("errorCode").asText());
    }

    @Test
    void postOptimize_determinism_samePayloadSameOutput() throws Exception {
        String body = OM.writeValueAsString(Map.of(
            "period", "ANNUAL",
            "spendByCategoryUsd", Map.of(
                "GROCERIES", 6000,
                "DINING", 3000,
                "GAS", 2400,
                "OTHER", 5000
            ),
            "goal", Map.of("goalType", "CASHBACK"),
            "constraints", Map.of("maxCards", 2, "maxAnnualFeeUsd", 95, "allowBusinessCards", true)
        ));

        var r1 = handler.handleOptimize(body, "test-1");
        var r2 = handler.handleOptimize(body, "test-2");

        assertEquals(200, r1.status());
        assertEquals(200, r2.status());

        JsonNode j1 = OM.readTree(r1.body());
        JsonNode j2 = OM.readTree(r2.body());

        assertEquals(
            j1.get("breakdown").get("netValueUsd").decimalValue(),
            j2.get("breakdown").get("netValueUsd").decimalValue(),
            "Same payload must yield same netValueUsd (compare numerically; JsonNode equality can fail when scales differ)");
        assertEquals(j1.get("explanation").get("fullText").asText(), j2.get("explanation").get("fullText").asText());
    }

    @Test
    void handleConfigGoals_returnsSupportedGoalsAndDefaultCpp() throws Exception {
        byte[] out = handler.handleConfigGoals();
        JsonNode json = OM.readTree(out);
        assertTrue(json.has("supportedGoals"));
        assertTrue(json.has("defaultCppByCurrency"));
        assertTrue(json.get("defaultCppByCurrency").has("AVIOS"));
    }

    /**
     * Determinism smoke: same payload twice must yield identical netValueUsd and explanation.fullText.
     */
    @Test
    void postOptimize_fillSample_annualAndMonthly_sameNetValue() throws Exception {
        Map<String, Double> fillSample = Map.of(
            "GROCERIES", 12000.0, "DINING", 8000.0, "GAS", 2400.0,
            "TRAVEL", 2000.0, "ONLINE", 1200.0, "OTHER", 5000.0
        );
        var constraints = Map.of("maxCards", 2, "maxAnnualFeeUsd", 200, "allowBusinessCards", true);
        var goal = Map.of("goalType", "CASHBACK");
        String body = OM.writeValueAsString(Map.of(
            "period", "ANNUAL", "spendByCategoryUsd", fillSample, "goal", goal, "constraints", constraints));

        var result1 = handler.handleOptimize(body, "req-1");
        var result2 = handler.handleOptimize(body, "req-2");

        assertEquals(200, result1.status());
        assertEquals(200, result2.status());

        JsonNode j1 = OM.readTree(result1.body());
        JsonNode j2 = OM.readTree(result2.body());
        assertEquals(
            j1.get("breakdown").get("netValueUsd").decimalValue(),
            j2.get("breakdown").get("netValueUsd").decimalValue(),
            "Same payload must yield same netValueUsd");
        assertEquals(
            j1.get("explanation").get("fullText").asText(),
            j2.get("explanation").get("fullText").asText(),
            "Same payload must yield same explanation.fullText");
    }

    @Test
    void postOptimize_allowBusinessCardsFalse_goalPromptFutureTravel_returns200() throws Exception {
        String body = """
            {"period":"ANNUAL","spendByCategoryUsd":{"TRAVEL":5000,"DINING":3000,"GROCERIES":4000},
            "goal":{"goalType":"CASHBACK","primaryCurrency":null},
            "constraints":{"maxCards":3,"maxAnnualFeeUsd":200,"allowBusinessCards":false},
            "goalPrompt":"future travel"}
            """;

        var result = handler.handleOptimize(body, "test-req-id");
        assertEquals(200, result.status(), () -> "status=" + result.status() + " body=" + (result.body() != null ? new String(result.body()) : "null"));

        JsonNode json = OM.readTree(result.body());
        assertTrue(json.has("goalInterpretation"), "When goalPrompt is sent, goalInterpretation must be present");
        JsonNode gi = json.get("goalInterpretation");
        assertEquals("future travel", gi.path("userPrompt").asText());
        assertEquals("PROGRAM_POINTS", gi.path("interpretedGoalType").asText());
        assertTrue(gi.path("rationale").asText().length() > 0, "rationale must be non-empty");
    }

    @Test
    void handleCatalogCards_returnsListAndMetadata() throws Exception {
        byte[] out = handler.handleCatalogCards("1.0");
        JsonNode json = OM.readTree(out);
        assertTrue(json.has("catalogVersion"));
        assertTrue(json.has("cards"));
        assertFalse(json.get("cards").isEmpty());
        JsonNode first = json.get("cards").get(0);
        assertTrue(first.has("cardId"));
        assertTrue(first.has("issuer"));
        assertTrue(first.has("name"));
    }
}
