package io.yukti.engine.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yukti.core.domain.GoalType;
import io.yukti.core.domain.RewardCurrencyType;
import io.yukti.core.domain.UserGoal;
import io.yukti.core.explainability.LlmProvider;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Optional LLM-backed goal interpreter. Validates LLM output against supported
 * enums only; on any failure or invalid output falls back to deterministic.
 * Gate with GOAL_LLM_ENABLED so we do not call LLM unless opted in.
 */
public final class LlmGoalInterpreter implements GoalInterpreter {

    private static final ObjectMapper OM = new ObjectMapper();
    private static final Pattern CODE_BLOCK = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private static final String SUPPORTED_GOALS = "CASHBACK (maximize cash back), FLEX_POINTS (flexible transferable points), PROGRAM_POINTS (airline/hotel miles)";
    private static final String SUPPORTED_PRIMARY = "AA_MILES, AVIOS (only when goalType is PROGRAM_POINTS; otherwise null)";

    private final LlmProvider provider;
    private final GoalInterpreter fallback;

    public LlmGoalInterpreter(LlmProvider provider, GoalInterpreter fallback) {
        this.provider = provider;
        this.fallback = fallback;
    }

    @Override
    public GoalInterpretation interpret(String goalPrompt, ExplicitGoalInput explicit) {
        if (goalPrompt == null || goalPrompt.isBlank()) {
            return fallback.interpret(goalPrompt, explicit);
        }
        String prompt = buildPrompt(goalPrompt);
        try {
            String output = provider.generate(prompt);
            if (output == null || output.isBlank()) return fallback.interpret(goalPrompt, explicit);
            String json = extractJson(output);
            if (json == null || json.isBlank()) return fallback.interpret(goalPrompt, explicit);
            return parseAndValidate(goalPrompt.trim(), json, explicit);
        } catch (Exception e) {
            return fallback.interpret(goalPrompt, explicit);
        }
    }

    private static String buildPrompt(String userMessage) {
        return """
            You interpret a user's short goal description into exactly one supported reward goal.
            Supported goalType values: %s.
            Supported primaryCurrency (only for PROGRAM_POINTS): %s.
            Output ONLY valid JSON in this exact shape, no other text:
            {"goalType":"CASHBACK|FLEX_POINTS|PROGRAM_POINTS","primaryCurrency":null or "AA_MILES" or "AVIOS","rationale":"One short sentence explaining what you interpreted."}
            If the user wants to maximize money or cash, use CASHBACK and primaryCurrency null.
            If the user wants travel or miles (without specifying airline), use PROGRAM_POINTS and primaryCurrency "AVIOS".
            User message: %s
            """.formatted(SUPPORTED_GOALS, SUPPORTED_PRIMARY, userMessage);
    }

    private GoalInterpretation parseAndValidate(String userPrompt, String json, ExplicitGoalInput explicit) {
        try {
            JsonNode root = OM.readTree(json);
            if (root == null || !root.isObject()) return fallback.interpret(userPrompt, explicit);
            String goalTypeStr = getText(root, "goalType");
            String primaryStr = getTextOrNull(root, "primaryCurrency");
            String rationale = getText(root, "rationale");
            if (goalTypeStr == null || goalTypeStr.isBlank()) return fallback.interpret(userPrompt, explicit);

            GoalType goalType = parseGoalType(goalTypeStr);
            if (goalType == null) return fallback.interpret(userPrompt, explicit);
            if (goalType != GoalType.PROGRAM_POINTS && primaryStr != null && !primaryStr.isBlank()) primaryStr = null;
            RewardCurrencyType primary = null;
            if (goalType == GoalType.PROGRAM_POINTS && primaryStr != null && !primaryStr.isBlank()) {
                try {
                    primary = RewardCurrencyType.valueOf(primaryStr.toUpperCase().replace("-", "_"));
                } catch (IllegalArgumentException e) {
                    primary = RewardCurrencyType.AVIOS;
                }
            }
            UserGoal userGoal = primary != null
                ? new UserGoal(goalType, Optional.of(primary), parseList(explicit != null ? explicit.preferredCurrencies() : null), parseCpp(explicit != null ? explicit.cppOverrides() : null))
                : new UserGoal(goalType, Optional.empty(), parseList(explicit != null ? explicit.preferredCurrencies() : null), parseCpp(explicit != null ? explicit.cppOverrides() : null));
            String safeRationale = (rationale != null && !rationale.isBlank()) ? rationale.trim() : buildDefaultRationale(goalType, primary);
            return new GoalInterpretation(userGoal, userPrompt, goalType.name(), primary != null ? primary.name() : null, safeRationale);
        } catch (Exception e) {
            return fallback.interpret(userPrompt, explicit);
        }
    }

    private static GoalType parseGoalType(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return GoalType.valueOf(s.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String buildDefaultRationale(GoalType gt, RewardCurrencyType primary) {
        switch (gt) {
            case CASHBACK: return "We interpreted your message as maximizing cash back.";
            case FLEX_POINTS: return "We interpreted your message as flexible transferable points.";
            case PROGRAM_POINTS:
                String c = primary != null ? primary.name().replace("_", " ") : "program points";
                return "We interpreted your message as travel rewards (program points with " + c + ").";
            default: return "We interpreted your message as " + gt.name() + ".";
        }
    }

    private static String getText(JsonNode n, String key) {
        JsonNode v = n != null && n.isObject() ? n.get(key) : null;
        return v != null && v.isTextual() ? v.asText().trim() : null;
    }

    private static String getTextOrNull(JsonNode n, String key) {
        JsonNode v = n != null && n.isObject() ? n.get(key) : null;
        if (v == null || v.isNull()) return null;
        return v.isTextual() ? v.asText().trim() : null;
    }

    private static List<RewardCurrencyType> parseList(List<String> raw) {
        if (raw == null) return List.of();
        return raw.stream()
            .map(s -> {
                try {
                    return RewardCurrencyType.valueOf(s.toUpperCase().replace("-", "_"));
                } catch (IllegalArgumentException e) {
                    return null;
                }
            })
            .filter(java.util.Objects::nonNull)
            .toList();
    }

    private static Map<RewardCurrencyType, BigDecimal> parseCpp(Map<String, Double> raw) {
        if (raw == null) return Map.of();
        Map<RewardCurrencyType, BigDecimal> out = new java.util.EnumMap<>(RewardCurrencyType.class);
        for (Map.Entry<String, Double> e : raw.entrySet()) {
            try {
                out.put(RewardCurrencyType.valueOf(e.getKey().toUpperCase().replace("-", "_")), BigDecimal.valueOf(e.getValue()));
            } catch (IllegalArgumentException ignored) {}
        }
        return out;
    }

    private static String extractJson(String output) {
        if (output == null) return null;
        String trimmed = output.trim();
        var m = CODE_BLOCK.matcher(trimmed);
        if (m.find()) return m.group(1).trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) return trimmed.substring(start, end + 1);
        return trimmed;
    }
}
