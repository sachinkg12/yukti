package io.yukti.engine.optimizer;

import io.yukti.core.api.Optimizer;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Registry of Optimizer implementations. OCP: add optimizers without changing orchestration.
 * Config: env YUKTI_OPTIMIZER_ID (or yukti_OPTIMIZER_ID), or system property yukti.optimizer, or env YUKTI_OPTIMIZER.
 * Default: "milp-v1". get(unknownId) throws IllegalArgumentException.
 */
public final class OptimizerRegistry {
    private static final Logger LOG = Logger.getLogger(OptimizerRegistry.class.getName());
    private static final String CONFIG_KEY = "yukti.optimizer";
    private static final String ENV_KEY = "YUKTI_OPTIMIZER";
    private static final String ENV_KEY_ALT = "YUKTI_OPTIMIZER_ID";
    private static final String DEFAULT_ID = "milp-v1";

    private final Map<String, Optimizer> byId = new LinkedHashMap<>();

    public OptimizerRegistry() {
        tryRegister(new MilpOptimizer());
        tryRegister(new LpRelaxationOptimizer());
        register(new GreedyPortfolioOptimizerV1());
        register(new CapAwareGreedyOptimizer());
        register(new ExhaustiveSearchOptimizer());
        register(new SingleBestPerCategoryBaseline());
        register(new ContentBasedTopKBaseline());
        register(new TopKPopularBaseline());
        register(new RandomKBaseline());
        register(new AhpMcdmBaseline());
        register(new AhpMcdmBaseline(0.30, 0.35, 0.15, 0.20, "-fee-heavy"));
        register(new AhpMcdmBaseline(0.30, 0.20, 0.30, 0.20, "-coverage-heavy"));
        register(new AhpPairwiseBaseline());
        register(new RuleBasedRecommender());
        register(new SimulatedAnnealingOptimizer());
    }

    private void tryRegister(Optimizer optimizer) {
        try {
            register(optimizer);
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            LOG.log(Level.WARNING,
                "Could not register {0}: OR-Tools native libraries not available ({1}). "
                + "Heuristic optimizers remain available.",
                new Object[]{optimizer.id(), e.getMessage()});
        }
    }

    public void register(Optimizer optimizer) {
        byId.put(optimizer.id(), optimizer);
    }

    /** Returns optimizer for id. Throws IllegalArgumentException if unknown. */
    public Optimizer get(String id) {
        Optimizer o = byId.get(id);
        if (o == null) {
            throw new IllegalArgumentException("Unknown optimizer id: " + id + ". Available: " + String.join(", ", byId.keySet()));
        }
        return o;
    }

    public Optional<Optimizer> getOptional(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Optimizer getOrDefault(String id) {
        return byId.getOrDefault(id, byId.get(DEFAULT_ID));
    }

    public Optimizer select() {
        String id = System.getProperty(CONFIG_KEY);
        if (id == null || id.isBlank()) id = System.getenv(ENV_KEY);
        if (id == null || id.isBlank()) id = System.getenv(ENV_KEY_ALT);
        if (id == null || id.isBlank()) id = DEFAULT_ID;
        return get(id);
    }

    public Set<String> availableIds() {
        return Collections.unmodifiableSet(byId.keySet());
    }
}
