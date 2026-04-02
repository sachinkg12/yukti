package io.yukti.engine.optimizer;

import io.yukti.core.domain.Category;
import io.yukti.core.domain.Money;
import io.yukti.core.domain.ObjectiveBreakdown;

import java.util.*;

/**
 * Immutable value object holding a solved MILP output.
 * Contains the full solution: selected cards (y), allocation matrix (x),
 * piecewise capped/overflow segments (w, z), objective breakdown, and solver metadata.
 *
 * <p>Paper §2: y[i] ∈ {0,1} card selection; x[i,c] spend allocation;
 * w[i,c] capped portion; z[i,c] overflow portion.
 */
public final class MilpSolution {

    private final List<String> selectedCardIds;
    private final Map<String, Map<Category, Double>> x;
    private final Map<String, Map<Category, Double>> w;
    private final Map<String, Map<Category, Double>> z;
    private final ObjectiveBreakdown objectiveBreakdown;
    private final double objectiveValue;
    private final long solveTimeMs;
    private final String solverStatus;
    private final int numVariables;
    private final int numConstraints;
    private final int numBinaryVariables;

    public MilpSolution(
            List<String> selectedCardIds,
            Map<String, Map<Category, Double>> x,
            Map<String, Map<Category, Double>> w,
            Map<String, Map<Category, Double>> z,
            ObjectiveBreakdown objectiveBreakdown,
            double objectiveValue,
            long solveTimeMs,
            String solverStatus,
            int numVariables,
            int numConstraints,
            int numBinaryVariables) {
        this.selectedCardIds = List.copyOf(selectedCardIds);
        this.x = deepCopy(x);
        this.w = deepCopy(w);
        this.z = deepCopy(z);
        this.objectiveBreakdown = Objects.requireNonNull(objectiveBreakdown);
        this.objectiveValue = objectiveValue;
        this.solveTimeMs = solveTimeMs;
        this.solverStatus = Objects.requireNonNull(solverStatus);
        this.numVariables = numVariables;
        this.numConstraints = numConstraints;
        this.numBinaryVariables = numBinaryVariables;
    }

    public List<String> selectedCardIds() {
        return selectedCardIds;
    }

    /** Spend allocated to card in category. Returns 0.0 if no allocation. */
    public double x(String cardId, Category cat) {
        return x.getOrDefault(cardId, Map.of()).getOrDefault(cat, 0.0);
    }

    /** Capped portion of spend for card in category. Returns 0.0 if uncapped or no allocation. */
    public double w(String cardId, Category cat) {
        return w.getOrDefault(cardId, Map.of()).getOrDefault(cat, 0.0);
    }

    /** Overflow portion of spend for card in category. Returns 0.0 if no overflow. */
    public double z(String cardId, Category cat) {
        return z.getOrDefault(cardId, Map.of()).getOrDefault(cat, 0.0);
    }

    /**
     * For each category, the card with the highest allocation.
     * Ties broken by lexicographically smallest cardId (deterministic).
     * Matches the greedy optimizer's allocation format for API compatibility.
     */
    public Map<Category, String> allocationMap() {
        Map<Category, String> result = new EnumMap<>(Category.class);
        for (Category cat : Category.values()) {
            String bestCard = null;
            double bestAlloc = 0.0;
            for (String cardId : selectedCardIds) {
                double alloc = x(cardId, cat);
                if (alloc > bestAlloc || (alloc == bestAlloc && alloc > 0.0
                        && (bestCard == null || cardId.compareTo(bestCard) < 0))) {
                    bestAlloc = alloc;
                    bestCard = cardId;
                }
            }
            if (bestCard != null && bestAlloc > 0.01) {
                result.put(cat, bestCard);
            }
        }
        return result;
    }

    public ObjectiveBreakdown objectiveBreakdown() {
        return objectiveBreakdown;
    }

    public double objectiveValue() {
        return objectiveValue;
    }

    public long solveTimeMs() {
        return solveTimeMs;
    }

    public String solverStatus() {
        return solverStatus;
    }

    public int numVariables() {
        return numVariables;
    }

    public int numConstraints() {
        return numConstraints;
    }

    public int numBinaryVariables() {
        return numBinaryVariables;
    }

    /** Full allocation matrix (unmodifiable). */
    public Map<String, Map<Category, Double>> xMatrix() {
        return x;
    }

    /** Full capped-portion matrix (unmodifiable). */
    public Map<String, Map<Category, Double>> wMatrix() {
        return w;
    }

    /** Full overflow matrix (unmodifiable). */
    public Map<String, Map<Category, Double>> zMatrix() {
        return z;
    }

    private static Map<String, Map<Category, Double>> deepCopy(Map<String, Map<Category, Double>> src) {
        Map<String, Map<Category, Double>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Map<Category, Double>> entry : src.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableMap(new EnumMap<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }
}
