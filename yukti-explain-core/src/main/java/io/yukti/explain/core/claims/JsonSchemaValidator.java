package io.yukti.explain.core.claims;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Validates claim JSON against the Claim schema v1 (structure only).
 * No Yukti-specific logic: required fields, types, and no extra top-level properties.
 * Schema is loaded from classpath: schema/claim_v1.schema.json.
 */
public final class JsonSchemaValidator {

    private static final String SCHEMA_RESOURCE = "/schema/claim_v1.schema.json";
    private static final ObjectMapper OM = new ObjectMapper();

    private final JsonNode schema;

    public JsonSchemaValidator() {
        this(loadSchemaFromClasspath(SCHEMA_RESOURCE));
    }

    public JsonSchemaValidator(JsonNode schema) {
        this.schema = schema;
    }

    /**
     * Validate claim JSON (as string or pre-parsed object). Returns empty list if valid,
     * or list of error messages.
     */
    public List<String> validate(String claimJson) {
        try {
            return validate(OM.readTree(claimJson));
        } catch (IOException e) {
            return List.of("Invalid JSON: " + e.getMessage());
        }
    }

    /**
     * Validate claim as JsonNode. Returns empty list if valid, or list of error messages.
     */
    public List<String> validate(JsonNode claim) {
        if (claim == null || !claim.isObject()) {
            return List.of("Claim must be a JSON object");
        }
        ObjectNode obj = (ObjectNode) claim;
        List<String> errors = new ArrayList<>();

        JsonNode requiredNode = schema.path("required");
        if (requiredNode.isArray()) {
            for (JsonNode r : requiredNode) {
                String key = r.asText();
                if (!obj.has(key) || obj.get(key).isNull()) {
                    errors.add("Missing required field: " + key);
                }
            }
        }

        JsonNode props = schema.path("properties");
        if (!props.isObject()) {
            return errors;
        }
        ObjectNode propsObj = (ObjectNode) props;
        Set<String> allowed = StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(propsObj.fields(), 0), false)
            .map(e -> e.getKey())
            .collect(Collectors.toSet());

        for (String key : StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(obj.fields(), 0), false)
            .map(e -> e.getKey())
            .collect(Collectors.toList())) {
            if (!allowed.contains(key)) {
                errors.add("Disallowed property: " + key);
                continue;
            }
            JsonNode value = obj.get(key);
            JsonNode propSchema = props.path(key);
            List<String> fieldErrors = validateType(key, value, propSchema);
            errors.addAll(fieldErrors);
        }

        return Collections.unmodifiableList(errors);
    }

    private List<String> validateType(String key, JsonNode value, JsonNode propSchema) {
        List<String> errors = new ArrayList<>();
        String type = propSchema.has("type") ? propSchema.path("type").asText() : null;
        if (type != null) {
            switch (type) {
                case "string" -> {
                    if (value != null && !value.isTextual()) {
                        errors.add("Field '" + key + "' must be a string");
                    }
                }
                case "object" -> {
                    if (value != null && !value.isObject()) {
                        errors.add("Field '" + key + "' must be an object");
                    }
                }
                case "array" -> {
                    if (value != null && !value.isArray()) {
                        errors.add("Field '" + key + "' must be an array");
                    } else if (value != null && value.isArray()) {
                        JsonNode items = propSchema.path("items");
                        if (items.isObject() && "string".equals(items.path("type").asText(null))) {
                            for (int i = 0; i < value.size(); i++) {
                                if (!value.get(i).isTextual()) {
                                    errors.add("Field '" + key + "'[" + i + "] must be a string");
                                }
                            }
                        }
                    }
                }
                default -> { }
            }
        }
        if (propSchema.has("enum")) {
            ArrayNode enumArr = (ArrayNode) propSchema.get("enum");
            if (value != null && value.isTextual()) {
                String v = value.asText();
                boolean found = false;
                for (JsonNode e : enumArr) {
                    if (e.asText().equals(v)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    errors.add("Field '" + key + "' must be one of: " + enumArr);
                }
            }
        }
        return errors;
    }

    /**
     * Returns true if the claim JSON is valid.
     */
    public boolean isValid(String claimJson) {
        return validate(claimJson).isEmpty();
    }

    public boolean isValid(JsonNode claim) {
        return validate(claim).isEmpty();
    }

    private static JsonNode loadSchemaFromClasspath(String resource) {
        try (InputStream in = JsonSchemaValidator.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Schema not found on classpath: " + resource);
            }
            return OM.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load claim schema: " + resource, e);
        }
    }
}
