package io.yukti.engine.parser;

import io.yukti.core.domain.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PreferenceParserV1 unit tests: AA miles, cashback, flex points mapping.
 */
class PreferenceParserV1Test {

    private final PreferenceParserV1 parser = new PreferenceParserV1();

    @Test
    void cashback_from_cash() {
        ParsedPreferences p = parser.parse("I want cash");
        assertEquals(GoalType.CASHBACK, p.getUserGoal().getGoalType());
        assertTrue(p.getUserGoal().getPrimaryCurrency().isEmpty());
    }

    @Test
    void cashback_from_cashback() {
        ParsedPreferences p = parser.parse("maximize cashback");
        assertEquals(GoalType.CASHBACK, p.getUserGoal().getGoalType());
    }

    @Test
    void flex_points_from_flex() {
        ParsedPreferences p = parser.parse("flex points for travel");
        assertEquals(GoalType.FLEX_POINTS, p.getUserGoal().getGoalType());
        assertTrue(p.getUserGoal().getPrimaryCurrency().isEmpty());
    }

    @Test
    void flex_points_from_transfer() {
        ParsedPreferences p = parser.parse("transferable points");
        assertEquals(GoalType.FLEX_POINTS, p.getUserGoal().getGoalType());
    }

    @Test
    void flex_points_from_points() {
        ParsedPreferences p = parser.parse("I like points");
        assertEquals(GoalType.FLEX_POINTS, p.getUserGoal().getGoalType());
    }

    @Test
    void program_points_aa_from_aa() {
        ParsedPreferences p = parser.parse("AA miles for travel");
        assertEquals(GoalType.PROGRAM_POINTS, p.getUserGoal().getGoalType());
        assertTrue(p.getUserGoal().getPrimaryCurrency().isPresent());
        assertEquals(RewardCurrencyType.AA_MILES, p.getUserGoal().getPrimaryCurrency().get());
    }

    @Test
    void program_points_aa_from_american_airlines() {
        ParsedPreferences p = parser.parse("American Airlines miles");
        assertEquals(GoalType.PROGRAM_POINTS, p.getUserGoal().getGoalType());
        assertEquals(RewardCurrencyType.AA_MILES, p.getUserGoal().getPrimaryCurrency().orElseThrow());
    }

    @Test
    void program_points_avios_from_avios() {
        ParsedPreferences p = parser.parse("Avios for travel");
        assertEquals(GoalType.PROGRAM_POINTS, p.getUserGoal().getGoalType());
        assertEquals(RewardCurrencyType.AVIOS, p.getUserGoal().getPrimaryCurrency().orElseThrow());
    }

    @Test
    void program_points_from_future_travel() {
        ParsedPreferences p = parser.parse("future travel");
        assertEquals(GoalType.PROGRAM_POINTS, p.getUserGoal().getGoalType());
        assertEquals(RewardCurrencyType.AVIOS, p.getUserGoal().getPrimaryCurrency().orElseThrow());
    }

    @Test
    void tie_break_program_beats_flex() {
        ParsedPreferences p = parser.parse("AA flex points");
        assertEquals(GoalType.PROGRAM_POINTS, p.getUserGoal().getGoalType());
        assertEquals(RewardCurrencyType.AA_MILES, p.getUserGoal().getPrimaryCurrency().orElseThrow());
    }

    @Test
    void tie_break_flex_beats_cashback() {
        ParsedPreferences p = parser.parse("flex cash");
        assertEquals(GoalType.FLEX_POINTS, p.getUserGoal().getGoalType());
    }

    @Test
    void empty_or_blank_returns_cashback_default() {
        assertEquals(GoalType.CASHBACK, parser.parse(null).getUserGoal().getGoalType());
        assertEquals(GoalType.CASHBACK, parser.parse("").getUserGoal().getGoalType());
        assertEquals(GoalType.CASHBACK, parser.parse("   ").getUserGoal().getGoalType());
    }

    @Test
    void mergeWithExplicit_explicit_goalType_wins() {
        ParsedPreferences parsed = parser.parse("aa miles");
        UserGoal merged = parser.mergeWithExplicit(
            parsed,
            "FLEX_POINTS",  // explicit overrides parsed PROGRAM_POINTS
            null,
            null,
            null
        );
        assertEquals(GoalType.FLEX_POINTS, merged.getGoalType());
    }

    @Test
    void mergeWithExplicit_explicit_primaryCurrency_wins() {
        ParsedPreferences parsed = parser.parse("flex");
        UserGoal merged = parser.mergeWithExplicit(
            parsed,
            "PROGRAM_POINTS",
            "AA_MILES",  // explicit primary
            null,
            null
        );
        assertEquals(GoalType.PROGRAM_POINTS, merged.getGoalType());
        assertEquals(RewardCurrencyType.AA_MILES, merged.getPrimaryCurrency().orElseThrow());
    }

    @Test
    void mergeWithExplicit_parsed_used_when_explicit_absent() {
        ParsedPreferences parsed = parser.parse("american airlines miles");
        UserGoal merged = parser.mergeWithExplicit(
            parsed,
            null,  // no explicit goalType -> use parsed
            null,  // no explicit primary -> use parsed
            null,
            null
        );
        assertEquals(GoalType.PROGRAM_POINTS, merged.getGoalType());
        assertEquals(RewardCurrencyType.AA_MILES, merged.getPrimaryCurrency().orElseThrow());
    }

    @Test
    void mergeWithExplicit_case_insensitive() {
        ParsedPreferences p1 = parser.parse("CASH");
        assertEquals(GoalType.CASHBACK, p1.getUserGoal().getGoalType());

        ParsedPreferences p2 = parser.parse("AA");
        assertEquals(GoalType.PROGRAM_POINTS, p2.getUserGoal().getGoalType());
    }
}
