package io.yukti.engine.parser;

import java.util.List;
import java.util.Map;

/**
 * Interprets a user's goal prompt into a single UserGoal and human-readable
 * rationale. Only used when goalPrompt is non-blank; caller must not invoke
 * when user provided nothing.
 */
public interface GoalInterpreter {

    /**
     * Input from the request for merging with parsed goal (preferredCurrencies, cppOverrides).
     */
    record ExplicitGoalInput(
        List<String> preferredCurrencies,
        Map<String, Double> cppOverrides
    ) {}

    /**
     * Interpret the user's free-text goal prompt into a UserGoal and explanation.
     *
     * @param goalPrompt non-blank user input (e.g. "future travel", "maximize money")
     * @param explicit explicit preferredCurrencies and cppOverrides from request
     * @return interpretation with userGoal and rationale; never null
     */
    GoalInterpretation interpret(String goalPrompt, ExplicitGoalInput explicit);
}
