package io.yukti.explain.core.claims;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses JSON array of claim_v1 objects into List&lt;NormalizedClaim&gt;.
 * Validates each element with JsonSchemaValidator before parsing.
 *
 * @throws IllegalArgumentException if JSON is not an array or any claim fails schema validation
 */
public final class NormalizedClaimParser {

    private static final ObjectMapper OM = new ObjectMapper();
    private static final JsonSchemaValidator VALIDATOR = new JsonSchemaValidator();

    private NormalizedClaimParser() {}

    /**
     * Parse and validate. Each array element must pass claim_v1 schema.
     */
    public static List<NormalizedClaim> parseArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        JsonNode root;
        try {
            root = OM.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON: " + e.getMessage(), e);
        }
        if (!root.isArray()) {
            throw new IllegalArgumentException("Expected JSON array of claims");
        }
        List<NormalizedClaim> out = new ArrayList<>();
        for (int i = 0; i < root.size(); i++) {
            JsonNode el = root.get(i);
            List<String> errors = VALIDATOR.validate(el);
            if (!errors.isEmpty()) {
                throw new IllegalArgumentException("Claim[" + i + "] schema validation failed: " + String.join("; ", errors));
            }
            out.add(toNormalizedClaim(el));
        }
        return List.copyOf(out);
    }

    private static NormalizedClaim toNormalizedClaim(JsonNode node) {
        String claimId = node.path("claimId").asText(null);
        if (claimId == null) claimId = "";
        ClaimType claimType = parseClaimType(node.path("claimType").asText(null));
        Map<String, Object> normalizedFields = toMap(node.path("normalizedFields"));
        List<String> citedEvidenceIds = toStringList(node.path("citedEvidenceIds"));
        List<String> citedEntities = toStringList(node.path("citedEntities"));
        List<String> citedNumbers = toStringList(node.path("citedNumbers"));
        String renderTemplateId = node.has("renderTemplateId") && !node.path("renderTemplateId").isNull()
            ? node.path("renderTemplateId").asText() : null;
        return new NormalizedClaim(claimId, claimType, normalizedFields, citedEvidenceIds, citedEntities, citedNumbers, renderTemplateId);
    }

    private static ClaimType parseClaimType(String s) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException("claimType is required");
        try {
            return ClaimType.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown claimType: " + s);
        }
    }

    private static Map<String, Object> toMap(JsonNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (!node.isObject()) return map;
        node.fields().forEachRemaining(e -> map.put(e.getKey(), toObject(e.getValue())));
        return map;
    }

    private static Object toObject(JsonNode n) {
        if (n == null || n.isNull()) return null;
        if (n.isTextual()) return n.asText();
        if (n.isNumber()) return n.isIntegralNumber() ? n.asLong() : n.asDouble();
        if (n.isBoolean()) return n.asBoolean();
        if (n.isArray()) {
            List<Object> list = new ArrayList<>();
            n.forEach(c -> list.add(toObject(c)));
            return list;
        }
        if (n.isObject()) return toMap(n);
        return null;
    }

    private static List<String> toStringList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (!node.isArray()) return list;
        node.forEach(c -> list.add(c.isTextual() ? c.asText() : c.toString()));
        return list;
    }
}
