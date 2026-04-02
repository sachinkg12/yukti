package io.yukti.engine.parser;

import io.yukti.core.api.PreferenceParser;
import io.yukti.core.domain.*;

import java.util.*;

/**
 * Deterministic goal-prompt parser. Never overrides explicit request fields.
 * Rules (case-insensitive): cash/cashback->CASHBACK; aa/american airlines->PROGRAM AA_MILES;
 * flex/transfer/points->FLEX_POINTS; travel/trip/flight/miles->PROGRAM (default AVIOS). Tie: PROGRAM > FLEX > CASHBACK.
 */
public final class PreferenceParserV1 implements PreferenceParser {

    @Override
    public String id() {
        return "preference-parser-v1";
    }

    @Override
    public ParsedPreferences parse(String text) {
        if (text == null || text.isBlank()) {
            return new ParsedPreferences(UserGoal.of(GoalType.CASHBACK), UserConstraints.defaults(), Map.of());
        }
        String lower = text.trim().toLowerCase();
        GoalType goal = inferGoal(lower);
        String primary = inferPrimaryCurrency(lower, goal);
        if (goal == GoalType.PROGRAM_POINTS && primary == null) primary = inferPrimaryFromGoal(goal);
        List<RewardCurrencyType> preferred = List.of();
        Map<RewardCurrencyType, java.math.BigDecimal> cppOverrides = Map.of();
        UserGoal userGoal = buildUserGoal(goal, primary, preferred, cppOverrides);
        return new ParsedPreferences(userGoal, UserConstraints.defaults(), Map.of("raw", text));
    }

    /**
     * Merge when AI-assist goal prompt is present: parsed goal type and primary win;
     * explicit preferredCurrencies and cppOverrides are still applied.
     */
    public UserGoal mergePreferringParsedGoal(
        ParsedPreferences parsed,
        List<String> explicitPreferredCurrencies,
        Map<String, Double> explicitCppOverrides
    ) {
        var ug = parsed.getUserGoal();
        GoalType goal = ug.getGoalType();
        Optional<RewardCurrencyType> primary = ug.getPrimaryCurrency();
        List<RewardCurrencyType> preferred = parseCurrencies(explicitPreferredCurrencies);
        Map<RewardCurrencyType, java.math.BigDecimal> cppOverrides = parseCppOverrides(explicitCppOverrides);
        if (goal == GoalType.PROGRAM_POINTS && primary.isPresent()) {
            return new UserGoal(goal, primary, preferred, cppOverrides);
        }
        return new UserGoal(goal, primary, preferred, cppOverrides);
    }

    /** Merge parser output with explicit goal. Explicit fields win. */
    public UserGoal mergeWithExplicit(
        ParsedPreferences parsed,
        String explicitGoalType,
        String explicitPrimaryCurrency,
        List<String> explicitPreferredCurrencies,
        Map<String, Double> explicitCppOverrides
    ) {
        GoalType goal = explicitGoalType != null && !explicitGoalType.isBlank()
            ? GoalType.valueOf(explicitGoalType.toUpperCase())
            : parsed.getUserGoal().getGoalType();
        String primary;
        if (goal == GoalType.PROGRAM_POINTS && explicitPrimaryCurrency != null && !explicitPrimaryCurrency.isBlank()) {
            primary = explicitPrimaryCurrency;
        } else if (goal == GoalType.PROGRAM_POINTS && parsed.getUserGoal().getPrimaryCurrency().isPresent()) {
            primary = parsed.getUserGoal().getPrimaryCurrency().get().name();
        } else {
            primary = inferPrimaryFromGoal(goal);
        }
        List<RewardCurrencyType> preferred = parseCurrencies(explicitPreferredCurrencies);
        Map<RewardCurrencyType, java.math.BigDecimal> cppOverrides = parseCppOverrides(explicitCppOverrides);
        if (goal == GoalType.PROGRAM_POINTS && primary != null) {
            try {
                return new UserGoal(
                    goal,
                    Optional.of(RewardCurrencyType.valueOf(primary.toUpperCase().replace("-", "_"))),
                    preferred,
                    cppOverrides
                );
            } catch (IllegalArgumentException e) {
                return new UserGoal(goal, Optional.empty(), preferred, cppOverrides);
            }
        }
        return new UserGoal(goal, Optional.empty(), preferred, cppOverrides);
    }

    private GoalType inferGoal(String lower) {
        boolean flex = lower.contains("flex") || lower.contains("transfer") || lower.contains("points");
        boolean program = lower.contains("aa") || lower.contains("american airlines")
            || lower.contains("avios") || lower.contains("british airways") || lower.contains("iberia")
            || lower.contains("aer lingus") || lower.contains("qatar")
            || (!flex && (lower.contains("travel") || lower.contains("trip") || lower.contains("flight") || lower.contains("miles")));
        boolean cash = lower.contains("cash") || lower.contains("cashback");
        if (program) return GoalType.PROGRAM_POINTS;
        if (flex) return GoalType.FLEX_POINTS;
        if (cash) return GoalType.CASHBACK;
        return GoalType.CASHBACK;
    }

    private String inferPrimaryCurrency(String lower, GoalType goal) {
        if (goal != GoalType.PROGRAM_POINTS) return null;
        if (lower.contains("aa") || lower.contains("american airlines")) return "AA_MILES";
        if (lower.contains("avios") || lower.contains("british airways") || lower.contains("iberia")
            || lower.contains("aer lingus") || lower.contains("qatar")) return "AVIOS";
        return null;
    }

    private String inferPrimaryFromGoal(GoalType goal) {
        // RewardsBench v1 and paper §2.3.1: PROGRAM_POINTS default primary = AVIOS.
        return goal == GoalType.PROGRAM_POINTS ? "AVIOS" : null;
    }

    private UserGoal buildUserGoal(GoalType goal, String primary, List<RewardCurrencyType> preferred,
                                   Map<RewardCurrencyType, java.math.BigDecimal> cppOverrides) {
        if (goal == GoalType.PROGRAM_POINTS && primary != null) {
            try {
                return new UserGoal(
                    goal,
                    Optional.of(RewardCurrencyType.valueOf(primary.toUpperCase().replace("-", "_"))),
                    preferred,
                    cppOverrides
                );
            } catch (IllegalArgumentException ignored) {}
        }
        return new UserGoal(goal, Optional.empty(), preferred, cppOverrides);
    }

    private List<RewardCurrencyType> parseCurrencies(List<String> raw) {
        if (raw == null) return List.of();
        List<RewardCurrencyType> out = new ArrayList<>();
        for (String s : raw) {
            try {
                out.add(RewardCurrencyType.valueOf(s.toUpperCase().replace("-", "_")));
            } catch (IllegalArgumentException ignored) {}
        }
        return out;
    }

    private Map<RewardCurrencyType, java.math.BigDecimal> parseCppOverrides(Map<String, Double> raw) {
        if (raw == null) return Map.of();
        Map<RewardCurrencyType, java.math.BigDecimal> out = new EnumMap<>(RewardCurrencyType.class);
        for (Map.Entry<String, Double> e : raw.entrySet()) {
            try {
                RewardCurrencyType t = RewardCurrencyType.valueOf(e.getKey().toUpperCase().replace("-", "_"));
                out.put(t, java.math.BigDecimal.valueOf(e.getValue()));
            } catch (IllegalArgumentException ignored) {}
        }
        return out;
    }
}
