package io.yukti.engine.parser;

import io.yukti.core.domain.UserGoal;

/**
 * Result of interpreting a user's goal prompt. Used only when the user provided
 * goalPrompt; never created or returned when user provided nothing (no hallucination).
 * Primary currency may be null when goal is not PROGRAM_POINTS.
 */
public record GoalInterpretation(
    UserGoal userGoal,
    String userPrompt,
    String interpretedGoalType,
    String primaryCurrency,
    String rationale
) {}
