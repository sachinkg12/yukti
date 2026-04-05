package io.yukti.core.artifacts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Writes JSON with stable key ordering, stable list ordering, and stable number formatting
 * (USD-style keys: 2 decimals; others: 6 decimals) for reproducibility.
 * Always appends a newline at end of file.
 */
public final class CanonicalJsonWriter {

    private static final int SCALE_USD = 2;
    private static final int SCALE_GENERAL = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private CanonicalJsonWriter() {}

    /**
     * Writes the given map as canonical JSON to the path.
     * Keys are emitted in alphabetical order; list order is preserved; numbers use stable formatting.
     */
    public static void write(Path path, Map<String, ?> root) throws IOException {
        Files.createDirectories(path.getParent());
        try (Writer w = new OutputStreamWriter(Files.newOutputStream(path), StandardCharsets.UTF_8)) {
            writeMap(w, root, null);
            w.write("\n");
        }
    }

    /**
     * Returns UTF-8 bytes of the map as canonical JSON (compact, no newline at end).
     * Used for hashing (e.g. reproducibility certificate).
     */
    public static byte[] writeToBytes(Map<String, ?> root) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            writeMap(w, root, null);
        }
        return out.toByteArray();
    }

    /**
     * Writes the given map as canonical JSON (pretty-printed with 2-space indent) to the path.
     * Use for human-readable artifacts; key order and number formatting are still stable.
     */
    public static void writePretty(Path path, Map<String, ?> root) throws IOException {
        Files.createDirectories(path.getParent());
        try (Writer w = new OutputStreamWriter(Files.newOutputStream(path), StandardCharsets.UTF_8)) {
            writeMapPretty(w, root, null, 0);
            w.write("\n");
        }
    }

    private static void writeMap(Writer w, Map<String, ?> map, String parentKey) throws IOException {
        Map<String, ?> sorted = map instanceof TreeMap ? map : new TreeMap<>(map);
        w.write("{");
        int i = 0;
        for (Map.Entry<String, ?> e : sorted.entrySet()) {
            if (i++ > 0) w.write(",");
            writeString(w, e.getKey());
            w.write(":");
            writeValue(w, e.getKey(), e.getValue());
        }
        w.write("}");
    }

    private static void writeMapPretty(Writer w, Map<String, ?> map, String parentKey, int indent) throws IOException {
        Map<String, ?> sorted = map instanceof TreeMap ? map : new TreeMap<>(map);
        w.write("{\n");
        int i = 0;
        for (Map.Entry<String, ?> e : sorted.entrySet()) {
            if (i++ > 0) w.write(",\n");
            indent(w, indent + 1);
            writeString(w, e.getKey());
            w.write(": ");
            writeValuePretty(w, e.getKey(), e.getValue(), indent + 1);
        }
        w.write("\n");
        indent(w, indent);
        w.write("}");
    }

    private static void writeValue(Writer w, String key, Object value) throws IOException {
        if (value == null) {
            w.write("null");
            return;
        }
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, ?> m = (Map<String, ?>) value;
            writeMap(w, m, key);
            return;
        }
        if (value instanceof List) {
            writeList(w, key, (List<?>) value);
            return;
        }
        if (value instanceof Number) {
            w.write(formatNumber(key, (Number) value));
            return;
        }
        if (value instanceof Boolean) {
            w.write(((Boolean) value) ? "true" : "false");
            return;
        }
        if (value instanceof String) {
            writeString(w, (String) value);
            return;
        }
        writeString(w, String.valueOf(value));
    }

    private static void writeValuePretty(Writer w, String key, Object value, int indent) throws IOException {
        if (value == null) {
            w.write("null");
            return;
        }
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, ?> m = (Map<String, ?>) value;
            writeMapPretty(w, m, key, indent);
            return;
        }
        if (value instanceof List) {
            writeListPretty(w, key, (List<?>) value, indent);
            return;
        }
        if (value instanceof Number) {
            w.write(formatNumber(key, (Number) value));
            return;
        }
        if (value instanceof Boolean) {
            w.write(((Boolean) value) ? "true" : "false");
            return;
        }
        if (value instanceof String) {
            writeString(w, (String) value);
            return;
        }
        writeString(w, String.valueOf(value));
    }

    private static void writeList(Writer w, String parentKey, List<?> list) throws IOException {
        w.write("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) w.write(",");
            writeValue(w, parentKey, list.get(i));
        }
        w.write("]");
    }

    private static void writeListPretty(Writer w, String parentKey, List<?> list, int indent) throws IOException {
        w.write("[\n");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) w.write(",\n");
            indent(w, indent + 1);
            writeValuePretty(w, parentKey, list.get(i), indent + 1);
        }
        w.write("\n");
        indent(w, indent);
        w.write("]");
    }

    private static void indent(Writer w, int n) throws IOException {
        for (int i = 0; i < n; i++) w.write("  ");
    }

    /** USD-style keys: 2 decimals; otherwise 6 decimals for stability. */
    private static String formatNumber(String key, Number n) {
        int scale = (key != null && key.toLowerCase().contains("usd")) ? SCALE_USD : SCALE_GENERAL;
        BigDecimal bd = n instanceof BigDecimal ? (BigDecimal) n : BigDecimal.valueOf(n.doubleValue());
        bd = bd.setScale(scale, ROUNDING);
        return bd.stripTrailingZeros().toPlainString();
    }

    private static void writeString(Writer w, String s) throws IOException {
        w.write("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') w.write("\\\"");
            else if (c == '\\') w.write("\\\\");
            else if (c == '\n') w.write("\\n");
            else if (c == '\r') w.write("\\r");
            else if (c == '\t') w.write("\\t");
            else if (c < 0x20) w.write(String.format("\\u%04x", (int) c));
            else w.write(c);
        }
        w.write("\"");
    }
}
