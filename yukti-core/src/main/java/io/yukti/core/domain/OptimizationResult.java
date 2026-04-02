package io.yukti.core.domain;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result of portfolio optimization.
 */
public final class OptimizationResult {
    private final List<String> portfolioIds;
    private final Map<Category, String> allocation;
    private final ObjectiveBreakdown breakdown;
    private final List<EvidenceBlock> evidenceBlocks;
    private final String narrative;
    private final List<String> switchingNotes;
    private final SolverStatus solverStatus;

    public OptimizationResult(
        List<String> portfolioIds,
        Map<Category, String> allocation,
        ObjectiveBreakdown breakdown,
        List<EvidenceBlock> evidenceBlocks,
        String narrative,
        List<String> switchingNotes
    ) {
        this(portfolioIds, allocation, breakdown, evidenceBlocks, narrative,
             switchingNotes, SolverStatus.OPTIMAL);
    }

    public OptimizationResult(
        List<String> portfolioIds,
        Map<Category, String> allocation,
        ObjectiveBreakdown breakdown,
        List<EvidenceBlock> evidenceBlocks,
        String narrative,
        List<String> switchingNotes,
        SolverStatus solverStatus
    ) {
        this.portfolioIds = List.copyOf(Objects.requireNonNull(portfolioIds));
        this.allocation = Map.copyOf(Objects.requireNonNull(allocation));
        this.breakdown = Objects.requireNonNull(breakdown);
        this.evidenceBlocks = List.copyOf(Objects.requireNonNull(evidenceBlocks));
        this.narrative = Objects.requireNonNull(narrative);
        this.switchingNotes = List.copyOf(Objects.requireNonNull(switchingNotes));
        this.solverStatus = Objects.requireNonNull(solverStatus);
    }

    public List<String> getPortfolioIds() {
        return portfolioIds;
    }

    public Map<Category, String> getAllocation() {
        return allocation;
    }

    public ObjectiveBreakdown getBreakdown() {
        return breakdown;
    }

    public List<EvidenceBlock> getEvidenceBlocks() {
        return evidenceBlocks;
    }

    public String getNarrative() {
        return narrative;
    }

    public List<String> getSwitchingNotes() {
        return switchingNotes;
    }

    public SolverStatus getSolverStatus() {
        return solverStatus;
    }

    public static OptimizationResult empty(String message) {
        return new OptimizationResult(
            List.of(),
            Map.of(),
            new ObjectiveBreakdown(Money.zeroUsd(), Money.zeroUsd(), Money.zeroUsd()),
            List.of(),
            message,
            List.of(),
            SolverStatus.NOT_SOLVED
        );
    }
}
