package io.yukti.evaluation.judge;

import java.util.Objects;

public record PreferenceVote(Winner winner, String rationale, double confidence) {
    public enum Winner { A, B, TIE }

    public PreferenceVote {
        Objects.requireNonNull(winner);
        Objects.requireNonNull(rationale);
        if (confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("confidence must be in [0,1]");
        }
    }
}
