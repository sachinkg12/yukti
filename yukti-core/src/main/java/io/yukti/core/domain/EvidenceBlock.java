package io.yukti.core.domain;

import java.util.Objects;

/**
 * Legacy evidence block. Implements EvidenceBlock for backward compatibility.
 * Prefer typed evidence (AssumptionEvidence, WinnerByCategoryEvidence, etc.) where possible.
 */
@Deprecated
public final class EvidenceBlock {
    private final String type;
    private final String cardId;
    private final String category;
    private final String content;

    public EvidenceBlock(String type, String cardId, String category, String content) {
        this.type = Objects.requireNonNull(type);
        this.cardId = Objects.requireNonNull(cardId);
        this.category = Objects.requireNonNull(category);
        this.content = Objects.requireNonNull(content);
    }

    public String getType() { return type; }
    public String getCardId() { return cardId; }
    public String getCategory() { return category; }
    public String getContent() { return content; }
}
