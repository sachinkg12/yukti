package io.yukti.engine.parser;

import io.yukti.core.domain.GoalType;
import io.yukti.core.domain.ParsedPreferences;
import io.yukti.core.domain.RewardCurrencyType;
import io.yukti.core.domain.UserGoal;

import java.util.List;
import java.util.Map;

/**
 * Interprets goal prompt using the deterministic keyword parser only.
 * Builds a short, factual rationale (no hallucination).
 */
public final class DeterministicGoalInterpreter implements GoalInterpreter {

    private final PreferenceParserV1 parser;

    public DeterministicGoalInterpreter(PreferenceParserV1 parser) {
        this.parser = parser;
    }

    @Override
    public GoalInterpretation interpret(String goalPrompt, ExplicitGoalInput explicit) {
        if (goalPrompt == null || goalPrompt.isBlank()) {
            UserGoal fallback = UserGoal.of(GoalType.CASHBACK);
            return new GoalInterpretation(
                fallback,
                goalPrompt != null ? goalPrompt : "",
                GoalType.CASHBACK.name(),
                null,
                "We used the default goal (maximize cash back)."
            );
        }
        String trimmed = goalPrompt.trim();
        ParsedPreferences parsed = parser.parse(trimmed);
        List<String> preferred = (explicit != null && explicit.preferredCurrencies() != null) ? explicit.preferredCurrencies() : List.of();
        Map<String, Double> cppOverrides = (explicit != null && explicit.cppOverrides() != null) ? explicit.cppOverrides() : Map.of();
        UserGoal userGoal = parser.mergePreferringParsedGoal(parsed, preferred, cppOverrides);
        String rationale = buildRationale(userGoal, trimmed);
        String primaryCurrency = userGoal.getPrimaryCurrency().map(RewardCurrencyType::name).orElse(null);
        return new GoalInterpretation(
            userGoal,
            trimmed,
            userGoal.getGoalType().name(),
            primaryCurrency,
            rationale
        );
    }

    private static String buildRationale(UserGoal userGoal, String userPrompt) {
        GoalType gt = userGoal.getGoalType();
        switch (gt) {
            case CASHBACK:
                return "We interpreted your message as maximizing cash back.";
            case FLEX_POINTS:
                return "We interpreted your message as flexible transferable points.";
            case PROGRAM_POINTS:
                String currency = userGoal.getPrimaryCurrency().map(c -> c.name().replace("_", " ")).orElse("program points");
                return "We interpreted your message as travel rewards (program points with " + currency + ").";
            default:
                return "We interpreted your message as " + gt.name() + ".";
        }
    }
}
