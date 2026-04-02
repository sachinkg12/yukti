package io.yukti.explain.core.canonical;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Yukti canonical JSON: deterministic key ordering and typed numeric encoding (plain decimal strings at fixed scale).
 * Not RFC 8785—encodes numerics as strings to avoid cross-language float drift. Output is UTF-8 bytes with trailing newline.
 * Number scales: USD-like keys → 2, cpp-like → 3, points-like → 0, else default 6.
 */
public final class YuktiCanonicalizer {

    private static final ObjectMapper OM = new ObjectMapper();
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final int DEFAULT_SCALE = 6;

    private YuktiCanonicalizer() {}

    /**
     * Canonicalize a JSON object/array (as string or already parsed) to UTF-8 bytes.
     * Appends a single newline at the end.
     *
     * @param json JSON string or JsonNode
     * @return UTF-8 canonical form including trailing newline
     */
    public static byte[] canonicalize(String json) throws IOException {
        JsonNode node = OM.readTree(json);
        return canonicalize(node);
    }

    /**
     * Canonicalize a JsonNode to UTF-8 bytes with trailing newline.
     */
    public static byte[] canonicalize(JsonNode node) {
        StringBuilder sb = new StringBuilder();
        append(node, null, sb);
        sb.append('\n');
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void append(JsonNode node, String currentKey, StringBuilder sb) {
        if (node == null || node.isMissingNode()) {
            sb.append("null");
            return;
        }
        switch (node.getNodeType()) {
            case NULL -> sb.append("null");
            case BOOLEAN -> sb.append(node.asBoolean() ? "true" : "false");
            case NUMBER -> appendNumber(node, currentKey, sb);
            case STRING -> appendString(node.asText(), sb);
            case ARRAY -> {
                sb.append('[');
                for (int i = 0; i < node.size(); i++) {
                    if (i > 0) sb.append(',');
                    append(node.get(i), null, sb);
                }
                sb.append(']');
            }
            case OBJECT -> {
                List<String> keys = new ArrayList<>();
                node.fieldNames().forEachRemaining(keys::add);
                keys.sort(String::compareTo); // lexicographic
                sb.append('{');
                for (int i = 0; i < keys.size(); i++) {
                    if (i > 0) sb.append(',');
                    String key = keys.get(i);
                    appendString(key, sb);
                    sb.append(':');
                    append(node.get(key), key, sb);
                }
                sb.append('}');
            }
            default -> sb.append("null");
        }
    }

    private static void appendNumber(JsonNode node, String key, StringBuilder sb) {
        BigDecimal bd;
        if (node.isIntegralNumber()) {
            bd = node.decimalValue();
        } else {
            bd = node.decimalValue();
        }
        int scale = scaleForKey(key);
        BigDecimal scaled = bd.setScale(scale, ROUNDING);
        String s = scaled.toPlainString();
        sb.append(s);
    }

    private static int scaleForKey(String key) {
        if (key == null) return DEFAULT_SCALE;
        String lower = key.toLowerCase();
        if (lower.contains("usd")) return 2;
        if (lower.contains("cpp")) return 3;
        if (lower.contains("point")) return 0;
        return DEFAULT_SCALE;
    }

    private static void appendString(String value, StringBuilder sb) {
        if (value == null) {
            sb.append("null");
            return;
        }
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c >= 0x20 && c != 0x7f) {
                        sb.append(c);
                    } else {
                        sb.append("\\u").append(String.format("%04x", (int) c));
                    }
                }
            }
        }
        sb.append('"');
    }
}
