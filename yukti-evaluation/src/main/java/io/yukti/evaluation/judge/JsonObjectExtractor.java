package io.yukti.evaluation.judge;

import java.util.Optional;

/**
 * Extracts the first top level JSON object from arbitrary text. Brace balanced,
 * string aware (skips braces inside JSON strings), and resilient to extra
 * prose, markdown fences, or multiple JSON blocks.
 *
 * <p>Returns the substring between the first balanced opening and closing brace.
 * Returns empty if no balanced object is found.
 *
 * <p>The earlier implementation used a greedy regex from first '{' to last '}',
 * which would fail when the LLM emitted multiple JSON objects or prose that
 * contained '{' or '}'.
 */
public final class JsonObjectExtractor {

    private JsonObjectExtractor() {}

    public static Optional<String> firstObject(String text) {
        if (text == null) return Optional.empty();
        int len = text.length();
        int start = -1;
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (inString) {
                if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                if (depth > 0) {
                    depth--;
                    if (depth == 0 && start >= 0) {
                        return Optional.of(text.substring(start, i + 1));
                    }
                }
            }
        }
        return Optional.empty();
    }
}
