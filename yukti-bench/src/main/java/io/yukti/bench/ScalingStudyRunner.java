package io.yukti.bench;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yukti.catalog.ClasspathCatalogSource;
import io.yukti.catalog.impl.ImmutableCatalog;
import io.yukti.core.api.Catalog;
import io.yukti.core.api.Card;
import io.yukti.core.api.Optimizer;
import io.yukti.core.api.ValuationPolicy;
import io.yukti.core.domain.*;
import io.yukti.engine.optimizer.OptimizerRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Scaling study: measures solve time as catalog size and card limit grow.
 *
 * <p>Tests catalog sizes: {20, 40, 70, 100, 150} cards.
 * For sizes &lt;= 70, subsamples from the real catalog.
 * For sizes &gt; 70, duplicates cards with perturbed IDs.
 * Card limits L ∈ {2, 3, 4, 5}.
 * Optimizers: MILP, Greedy, LP Relaxation.
 * Profiles: 50 curated profiles × 3 goals = 150 instances.
 *
 * <p>Output: JSON with timing statistics per (catalog_size, card_limit, optimizer).
 */
public class ScalingStudyRunner {

    private static final int[] CATALOG_SIZES = {20, 40, 70, 100, 150};
    private static final int[] CARD_LIMITS = {2, 3, 4, 5};
    private static final List<String> OPTIMIZER_IDS = List.of("milp-v1", "cap-aware-greedy-v1", "lp-relaxation-v1");
    private static final long SEED = 2026;

    public static void main(String[] args) throws Exception {
        Path outDir = args.length > 0 ? Path.of(args[0]) : Path.of("artifacts/bench/v2/scaling");
        outDir.toFile().mkdirs();

        Catalog baseCatalog = new ClasspathCatalogSource("catalog/catalog-v1.json").load("1.0");
        List<Card> baseCards = new ArrayList<>(baseCatalog.cards());
        System.out.println("Base catalog: " + baseCards.size() + " cards");

        // Use curated 50 profiles for scaling study (deterministic, representative)
        List<BenchmarkHarness.BenchProfile> profiles = ProfileGenerator.generate(50, SEED, "scale-");

        OptimizerRegistry registry = new OptimizerRegistry();

        List<Map<String, Object>> allResults = new ArrayList<>();

        for (int catalogSize : CATALOG_SIZES) {
            List<Card> scaledCards = buildScaledCatalog(baseCards, catalogSize);
            List<ValuationPolicy> policies = new ArrayList<>(baseCatalog.valuationPolicies());
            Catalog scaledCatalog = new ImmutableCatalog(baseCatalog.version(), scaledCards, policies);
            System.out.printf("\n=== Catalog size: %d cards ===%n", catalogSize);

            for (int cardLimit : CARD_LIMITS) {
                UserConstraints constraints = new UserConstraints(cardLimit, Money.usd(1000), true);

                for (String optId : OPTIMIZER_IDS) {
                    Optimizer optimizer = registry.get(optId);
                    List<Long> timesNs = new ArrayList<>();

                    for (BenchmarkHarness.BenchProfile p : profiles) {
                        Map<Category, Money> amounts = new EnumMap<>(Category.class);
                        p.spend().forEach((cat, v) -> amounts.put(cat, Money.usd(v)));
                        SpendProfile sp = new SpendProfile(p.monthly() ? Period.MONTHLY : Period.ANNUAL, amounts);

                        for (GoalType goal : List.of(GoalType.CASHBACK, GoalType.FLEX_POINTS, GoalType.PROGRAM_POINTS)) {
                            UserGoal ug = goal == GoalType.PROGRAM_POINTS
                                ? new UserGoal(GoalType.PROGRAM_POINTS, Optional.of(RewardCurrencyType.AVIOS), List.of(), Map.of())
                                : UserGoal.of(goal);
                            OptimizationRequest req = new OptimizationRequest(sp, ug, constraints, Map.of());

                            long start = System.nanoTime();
                            optimizer.optimize(req, scaledCatalog);
                            long elapsed = System.nanoTime() - start;
                            timesNs.add(elapsed);
                        }
                    }

                    double[] timesMs = timesNs.stream().mapToDouble(t -> t / 1e6).sorted().toArray();
                    int n = timesMs.length;

                    Map<String, Object> entry = new TreeMap<>();
                    entry.put("catalogSize", catalogSize);
                    entry.put("cardLimit", cardLimit);
                    entry.put("optimizerId", optId);
                    entry.put("instances", n);
                    entry.put("medianMs", round(timesMs[n / 2]));
                    entry.put("p95Ms", round(timesMs[(int) (n * 0.95)]));
                    entry.put("maxMs", round(timesMs[n - 1]));
                    entry.put("meanMs", round(Arrays.stream(timesMs).average().orElse(0)));
                    allResults.add(entry);

                    System.out.printf("  size=%d L=%d %s: median=%.1f ms, P95=%.1f ms, max=%.1f ms%n",
                        catalogSize, cardLimit, optId,
                        timesMs[n / 2], timesMs[(int) (n * 0.95)], timesMs[n - 1]);
                }
            }
        }

        // Write results
        Map<String, Object> output = new TreeMap<>();
        output.put("studyType", "scaling");
        output.put("catalogSizes", CATALOG_SIZES);
        output.put("cardLimits", CARD_LIMITS);
        output.put("optimizerIds", OPTIMIZER_IDS);
        output.put("profileCount", profiles.size());
        output.put("goalsPerProfile", 3);
        output.put("results", allResults);

        Path outFile = outDir.resolve("scaling_study.json");
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(outFile.toFile(), output);
        System.out.println("\nWrote " + outFile);
    }

    /**
     * Build a catalog of the target size.
     * For sizes <= base: deterministic subsample.
     * For sizes > base: duplicate cards with perturbed IDs and slight fee variation.
     */
    private static List<Card> buildScaledCatalog(List<Card> baseCards, int targetSize) {
        Random rng = new Random(SEED + targetSize);
        if (targetSize <= baseCards.size()) {
            // Deterministic subsample: shuffle with seed, take first targetSize
            List<Card> shuffled = new ArrayList<>(baseCards);
            Collections.shuffle(shuffled, rng);
            return shuffled.subList(0, targetSize);
        }

        // Start with all base cards, then add duplicates
        List<Card> result = new ArrayList<>(baseCards);
        int needed = targetSize - baseCards.size();
        for (int i = 0; i < needed; i++) {
            Card source = baseCards.get(i % baseCards.size());
            result.add(new SyntheticCard(source, "synth-" + i + "-" + source.id()));
        }
        return result;
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    /** Synthetic card wrapping a real card with a different ID for scaling. */
    private static final class SyntheticCard implements Card {
        private final Card delegate;
        private final String syntheticId;

        SyntheticCard(Card delegate, String syntheticId) {
            this.delegate = delegate;
            this.syntheticId = syntheticId;
        }

        @Override public String id() { return syntheticId; }
        @Override public String displayName() { return delegate.displayName() + " (synth)"; }
        @Override public String issuer() { return delegate.issuer(); }
        @Override public Money annualFee() { return delegate.annualFee(); }
        @Override public Money statementCreditsAnnual() { return delegate.statementCreditsAnnual(); }
        @Override public List<? extends io.yukti.core.api.RewardsRule> rules() {
            return delegate.rules();
        }
    }
}
