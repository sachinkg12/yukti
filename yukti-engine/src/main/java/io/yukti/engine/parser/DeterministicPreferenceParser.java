package io.yukti.engine.parser;

import io.yukti.core.api.PreferenceParser;
import io.yukti.core.domain.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * Deterministic preference parser. Phase-1: structured JSON-like or key=value.
 * Optional AI later for free-text.
 */
public final class DeterministicPreferenceParser implements PreferenceParser {
    @Override
    public String id() {
        return "deterministic";
    }

    @Override
    public ParsedPreferences parse(String text) throws Exception {
        if (text == null || text.isBlank()) {
            return new ParsedPreferences(
                UserGoal.of(GoalType.CASHBACK),
                UserConstraints.defaults(),
                Map.of()
            );
        }
        text = text.trim();
        GoalType goal = parseGoal(text);
        UserGoal userGoal = UserGoal.of(goal);
        UserConstraints constraints = UserConstraints.defaults();
        Map<String, String> raw = new HashMap<>();
        raw.put("raw", text);
        return new ParsedPreferences(userGoal, constraints, raw);
    }

    private GoalType parseGoal(String text) {
        String upper = text.toUpperCase();
        if (upper.contains("FLEX") || upper.contains("POINTS") && upper.contains("CHASE")) return GoalType.FLEX_POINTS;
        if (upper.contains("AA") || upper.contains("MILES") || upper.contains("PROGRAM")) return GoalType.PROGRAM_POINTS;
        return GoalType.CASHBACK;
    }
}
