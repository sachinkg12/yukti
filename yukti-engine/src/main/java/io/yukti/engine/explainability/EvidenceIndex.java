package io.yukti.engine.explainability;

import io.yukti.core.domain.Category;
import io.yukti.core.explainability.evidence.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Extracts evidence by type for deterministic rendering. Sorts by spec order.
 * Supports both typed evidence and legacy (domain.EvidenceBlock).
 */
public final class EvidenceIndex {
    private final List<EvidenceBlock> blocks;

    public EvidenceIndex(List<EvidenceBlock> blocks) {
        this.blocks = blocks != null ? new ArrayList<>(blocks) : List.of();
    }

    public Optional<AssumptionEvidence> firstAssumption() {
        return blocks.stream()
            .filter(AssumptionEvidence.class::isInstance)
            .map(AssumptionEvidence.class::cast)
            .findFirst();
    }

    public List<PortfolioStopEvidence> portfolioStops() {
        return blocks.stream()
            .filter(PortfolioStopEvidence.class::isInstance)
            .map(PortfolioStopEvidence.class::cast)
            .sorted(Comparator.comparing(PortfolioStopEvidence::reasonCode))
            .toList();
    }

    public List<FeeBreakEvenEvidence> feeBreakEvens() {
        return blocks.stream()
            .filter(FeeBreakEvenEvidence.class::isInstance)
            .map(FeeBreakEvenEvidence.class::cast)
            .sorted(Comparator.comparing(FeeBreakEvenEvidence::cardId))
            .toList();
    }

    public List<WinnerByCategoryEvidence> winnerByCategory() {
        return blocks.stream()
            .filter(WinnerByCategoryEvidence.class::isInstance)
            .map(WinnerByCategoryEvidence.class::cast)
            .sorted(Comparator.comparing(WinnerByCategoryEvidence::cat))
            .toList();
    }

    public List<CapHitEvidence> capHits() {
        return blocks.stream()
            .filter(CapHitEvidence.class::isInstance)
            .map(CapHitEvidence.class::cast)
            .sorted(Comparator.comparing(CapHitEvidence::cat).thenComparing(CapHitEvidence::cardId))
            .toList();
    }

    /** Legacy blocks with given type (LegacyEvidenceBlock only; typed evidence excluded). */
    public List<EvidenceBlock> legacyWithType(String type) {
        return blocks.stream()
            .filter(b -> b instanceof LegacyEvidenceBlock && type.equals(b.type()))
            .toList();
    }

    /** Sorted for deterministic rendering per spec. */
    public List<EvidenceBlock> sortedForRendering() {
        List<EvidenceBlock> out = new ArrayList<>();
        firstAssumption().ifPresent(out::add);
        out.addAll(portfolioStops());
        out.addAll(feeBreakEvens());
        out.addAll(winnerByCategory());
        out.addAll(capHits());
        // Legacy blocks not in above - append at end, grouped by type
        Set<EvidenceBlock> added = new HashSet<>(out);
        for (String t : List.of("ASSUMPTION", "PORTFOLIO_STOP", "FEE_BREAK_EVEN", "WINNER_BY_CATEGORY", "CAP_HIT", "ALLOCATION")) {
            legacyWithType(t).stream().filter(b -> !added.contains(b)).forEach(b -> { out.add(b); added.add(b); });
        }
        blocks.stream().filter(b -> !added.contains(b)).forEach(out::add);
        return out;
    }
}
