package io.yukti.core.explainability.evidence;

import io.yukti.core.domain.Category;

import java.math.BigDecimal;
import java.util.Objects;

public record WinnerByCategoryEvidence(
    Category cat,
    String winnerCardId,
    String runnerUpCardIdOrNull,
    BigDecimal winnerNetValueUsd,
    BigDecimal runnerUpNetValueUsdOrNull,
    BigDecimal deltaUsdOrNull
) implements EvidenceBlock {
    public WinnerByCategoryEvidence {
        Objects.requireNonNull(cat);
        Objects.requireNonNull(winnerCardId);
        Objects.requireNonNull(winnerNetValueUsd);
    }

    @Override
    public String type() { return "WINNER_BY_CATEGORY"; }

    @Override
    public String cardId() { return winnerCardId; }

    @Override
    public String category() { return cat.name(); }

    @Override
    public String content() {
        String delta = deltaUsdOrNull != null ? String.format("$%.2f", deltaUsdOrNull) : "N/A";
        String runner = runnerUpCardIdOrNull != null ? " over " + runnerUpCardIdOrNull : "";
        return String.format("%s wins %s%s: delta %s", winnerCardId, cat.name(), runner, delta);
    }
}
