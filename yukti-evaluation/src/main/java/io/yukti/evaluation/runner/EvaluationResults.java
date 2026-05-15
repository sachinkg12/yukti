package io.yukti.evaluation.runner;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Aggregate output of one evaluation sweep.
 *
 * <p>{@code perInstance} carries every (profile, goal, variant, model) evaluation.
 * <p>{@code matchedPairScores} carries counterbalanced paired preference scores
 *    (one entry per (profile, goal, model) pair, built from grounded vs ungrounded).
 * <p>{@code judgedPairs} is the legacy single-ordering vote stream, kept for back
 *    compat with older analysis scripts. Under the matched pair flow this is
 *    typically empty. New analyses should use {@code matchedPairScores}.
 */
public record EvaluationResults(
    Instant ranAt,
    EvaluationConfig config,
    List<PerInstanceEvaluation> perInstance,
    List<JudgedPair> judgedPairs,
    List<MatchedPairScore> matchedPairScores
) {
    public EvaluationResults {
        Objects.requireNonNull(ranAt);
        Objects.requireNonNull(config);
        perInstance = perInstance != null ? List.copyOf(perInstance) : List.of();
        judgedPairs = judgedPairs != null ? List.copyOf(judgedPairs) : List.of();
        matchedPairScores = matchedPairScores != null ? List.copyOf(matchedPairScores) : List.of();
    }

    /** Legacy constructor for callers that have not adopted matched pair scoring yet. */
    public EvaluationResults(
        Instant ranAt,
        EvaluationConfig config,
        List<PerInstanceEvaluation> perInstance,
        List<JudgedPair> judgedPairs
    ) {
        this(ranAt, config, perInstance, judgedPairs, List.of());
    }
}
