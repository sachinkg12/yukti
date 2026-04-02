package io.yukti.core.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Rewards breakdown by currency (points/miles earned per currency).
 * Extended: per-category, credits, cap notes, evidence.
 */
public final class RewardsBreakdown {
    private final Map<RewardCurrency, Points> byCurrency;
    private final Map<Category, Points> pointsByCategory;
    private final Money creditsUSD;
    private final List<String> capNotes;
    private final List<EvidenceBlock> evidence;

    /** Constructor for backward compatibility (points only). */
    public RewardsBreakdown(Map<RewardCurrency, Points> byCurrency) {
        this(byCurrency, Map.of(), Money.zeroUsd(), List.of(), List.of());
    }

    public RewardsBreakdown(
        Map<RewardCurrency, Points> byCurrency,
        Map<Category, Points> pointsByCategory,
        Money creditsUSD,
        List<String> capNotes,
        List<EvidenceBlock> evidence
    ) {
        this.byCurrency = Map.copyOf(Objects.requireNonNull(byCurrency));
        this.pointsByCategory = (pointsByCategory != null && !pointsByCategory.isEmpty())
            ? Collections.unmodifiableMap(new EnumMap<>(pointsByCategory))
            : Map.of();
        this.creditsUSD = creditsUSD != null ? creditsUSD : Money.zeroUsd();
        this.capNotes = capNotes != null ? List.copyOf(capNotes) : List.of();
        this.evidence = evidence != null ? List.copyOf(evidence) : List.of();
    }

    public Map<RewardCurrency, Points> getByCurrency() {
        return byCurrency;
    }

    public Points get(RewardCurrency currency) {
        return byCurrency.getOrDefault(currency, Points.of(java.math.BigDecimal.ZERO));
    }

    public Map<Category, Points> getPointsByCategory() {
        return pointsByCategory;
    }

    public Money getCreditsUSD() {
        return creditsUSD;
    }

    public List<String> getCapNotes() {
        return capNotes;
    }

    public List<EvidenceBlock> getEvidence() {
        return evidence;
    }
}
