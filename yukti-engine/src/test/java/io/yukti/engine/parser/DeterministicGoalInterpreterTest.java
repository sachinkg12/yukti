package io.yukti.engine.parser;

import io.yukti.core.domain.GoalType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DeterministicGoalInterpreter: rationale is factual, no hallucination.
 */
class DeterministicGoalInterpreterTest {

    private final DeterministicGoalInterpreter interpreter = new DeterministicGoalInterpreter(new PreferenceParserV1());

    @Test
    void interpret_futureTravel_returnsProgramPointsAndRationale() {
        var in = new GoalInterpreter.ExplicitGoalInput(null, null);
        GoalInterpretation g = interpreter.interpret("future travel", in);
        assertEquals(GoalType.PROGRAM_POINTS, g.userGoal().getGoalType());
        assertEquals("future travel", g.userPrompt());
        assertEquals("PROGRAM_POINTS", g.interpretedGoalType());
        assertNotNull(g.primaryCurrency());
        assertTrue(g.rationale().contains("travel rewards"), "rationale must explain interpretation");
    }

    @Test
    void interpret_maximizeMoney_returnsCashbackAndRationale() {
        var in = new GoalInterpreter.ExplicitGoalInput(null, null);
        GoalInterpretation g = interpreter.interpret("maximize money", in);
        assertEquals(GoalType.CASHBACK, g.userGoal().getGoalType());
        assertTrue(g.rationale().toLowerCase().contains("cash back"));
    }

    @Test
    void interpret_blank_returnsFallbackRationale() {
        GoalInterpretation g = interpreter.interpret("   ", new GoalInterpreter.ExplicitGoalInput(null, null));
        assertEquals(GoalType.CASHBACK, g.userGoal().getGoalType());
        assertTrue(g.rationale().length() > 0);
    }
}
