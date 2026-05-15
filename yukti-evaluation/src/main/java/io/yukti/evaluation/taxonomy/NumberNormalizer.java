package io.yukti.evaluation.taxonomy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes numeric strings to a canonical form for hallucination detection.
 *
 * <p>The LLM may cite "$6,000.00", "6000", "6000.00", or "$6000" for the same fact.
 * Without normalization, all of those except one would be falsely flagged as fabricated.
 *
 * <p>Canonical form is the {@link BigDecimal} {@link BigDecimal#toPlainString()} with
 * two decimal places. Examples:
 * <ul>
 *   <li>"$6,000.00" → "6000.00"</li>
 *   <li>"6000" → "6000.00"</li>
 *   <li>"6000.0" → "6000.00"</li>
 *   <li>"6,000" → "6000.00"</li>
 * </ul>
 *
 * <p>Percentages and rates are normalized separately because "6%" and "0.06" both
 * represent the same earn rate. See {@link #normalize(String)} for full rules.
 *
 * <p><b>Locale assumption: US English only.</b> The implementation strips comma as a
 * thousands separator. It will misparse European formatted text where the comma is
 * the decimal separator (e.g. "6.000,00" meaning six thousand). Callers must
 * ensure inputs are US formatted. The judge prompt is English and outputs are
 * dollar denominated, so US formatting is the right tradeoff here.
 */
public final class NumberNormalizer {

    private static final Pattern NUMERIC = Pattern.compile("-?\\d+(?:,\\d{3})*(?:\\.\\d+)?");

    private NumberNormalizer() {}

    /**
     * Normalize a string that may represent a dollar amount or count to canonical form.
     * Returns null if the string contains no parseable numeric value.
     */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        // strip currency symbols and percentage signs
        String stripped = trimmed.replace("$", "").replace("%", "").replace(",", "").trim();
        if (stripped.isEmpty()) return null;
        try {
            BigDecimal value = new BigDecimal(stripped);
            return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Extract all numeric tokens from arbitrary text. Each is returned in canonical form.
     * Useful for pulling numbers out of evidence block content strings such as
     * "amex-bcp: cap $6000.00 on GROCERIES, applied $6000.00, remaining $0.00".
     */
    public static java.util.List<String> extractAll(String text) {
        if (text == null || text.isEmpty()) return java.util.List.of();
        java.util.List<String> out = new java.util.ArrayList<>();
        Matcher m = NUMERIC.matcher(text);
        while (m.find()) {
            String canon = normalize(m.group());
            if (canon != null) out.add(canon);
        }
        return out;
    }
}
