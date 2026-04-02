package io.yukti.api.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yukti.api.dto.OptimizeRequestDto;
import io.yukti.api.dto.OptimizeResponseDto;
import io.yukti.api.mapper.OptimizationMapper;
import io.yukti.catalog.ClasspathCatalogSource;
import io.yukti.core.api.Catalog;
import io.yukti.core.api.Optimizer;
import io.yukti.core.domain.OptimizationRequest;
import io.yukti.core.domain.OptimizationResult;
import io.yukti.engine.optimizer.OptimizerRegistry;

import java.util.HashMap;
import java.util.Map;

public class OptimizeHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final ObjectMapper OM = new ObjectMapper();
    private static Catalog catalog;
    private static final Optimizer optimizer = new OptimizerRegistry().select();

    static {
        try {
            var source = new ClasspathCatalogSource("catalog/catalog-v1.json");
            catalog = source.load("1.0");
        } catch (Exception e) {
            throw new RuntimeException("Failed to load catalog", e);
        }
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        try {
            String bodyStr = (String) input.get("body");
            if (bodyStr == null) bodyStr = "{}";

            JsonNode req = OM.readTree(bodyStr);
            boolean monthly = req.path("monthly").asBoolean(false);
            JsonNode spendNode = req.path("spend");
            Map<String, Double> spend = new HashMap<>();
            for (var it = spendNode.fields(); it.hasNext(); ) {
                var e = it.next();
                spend.put(e.getKey(), e.getValue().asDouble(0));
            }
            if (spend.isEmpty()) spend.put("OTHER", 0.0);
            String goal = req.path("goal").asText("CASHBACK");
            int maxCards = req.path("maxCards").asInt(3);

            var requestDto = new OptimizeRequestDto(monthly, spend, goal, maxCards);
            OptimizationRequest request = OptimizationMapper.toRequest(requestDto);
            OptimizationResult result = optimizer.optimize(request, catalog);
            OptimizeResponseDto responseDto = OptimizationMapper.toResponse(result);

            return Map.of(
                "statusCode", 200,
                "headers", Map.of("Content-Type", "application/json", "Access-Control-Allow-Origin", "*"),
                "body", OM.writeValueAsString(responseDto)
            );
        } catch (Exception e) {
            try {
                return Map.of(
                    "statusCode", 500,
                    "headers", Map.of("Content-Type", "application/json", "Access-Control-Allow-Origin", "*"),
                    "body", OM.writeValueAsString(Map.of("error", e.getMessage()))
                );
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }
}
