package io.yukti.bench;

import io.yukti.core.domain.Category;

import java.util.*;

/**
 * Parametric profile generator with seeded RNG for reproducibility.
 * Generates benchmark profiles stratified by spend level, category dominance, and period.
 *
 * <p>Stratification:
 * <ul>
 *   <li>Spend level: Low ($8K-$18K), Medium ($18K-$40K), High ($40K-$90K) — ~33% each</li>
 *   <li>Category dominance: Single-dominant (one cat ≥50%), Multi-dominant (2-3 cats each 20-30%), Balanced (no cat >25%) — ~33% each</li>
 *   <li>Period: 80% annual, 20% monthly</li>
 *   <li>Active categories: 3-6 (weighted toward 4-5)</li>
 *   <li>Cap-boundary: ~15% of generated profiles near common caps ($6K, $25K grocery thresholds)</li>
 * </ul>
 */
public final class ProfileGenerator {

    private static final Category[] ALL_CATS = Category.values();
    private static final int[] COMMON_CAPS = {1500, 6000, 8000, 12000, 15000, 25000};

    private ProfileGenerator() {}

    /**
     * Generate deterministic profiles.
     * @param count number of profiles to generate
     * @param seed RNG seed for reproducibility
     * @param prefix profile ID prefix (e.g., "gen-" or "holdout-")
     * @return list of profiles in stable order
     */
    public static List<BenchmarkHarness.BenchProfile> generate(int count, long seed, String prefix) {
        Random rng = new Random(seed);
        List<BenchmarkHarness.BenchProfile> profiles = new ArrayList<>(count);

        int capBoundaryTarget = Math.max(1, (int) (count * 0.15));
        int capBoundaryCount = 0;

        for (int i = 0; i < count; i++) {
            // Stratify spend level: ~33% each
            SpendLevel level = SpendLevel.values()[i % 3];

            // Stratify dominance: ~33% each
            DominanceType dominance = DominanceType.values()[(i / 3) % 3];

            // Period: 80% annual, 20% monthly
            boolean monthly = (i % 5 == 4);

            // Active category count: weighted toward 4-5
            int activeCats = pickActiveCatCount(rng);

            // Generate total annual spend within level range
            double totalAnnual = level.minAnnual + rng.nextDouble() * (level.maxAnnual - level.minAnnual);

            // Pick which categories are active
            List<Category> active = pickActiveCategories(rng, activeCats);

            // Distribute spend according to dominance pattern
            Map<Category, Double> spend = distributSpend(rng, active, totalAnnual, dominance);

            // Cap-boundary injection: ~15% of profiles
            if (capBoundaryCount < capBoundaryTarget && i % 7 == 0) {
                injectCapBoundary(rng, spend);
                capBoundaryCount++;
            }

            // For monthly profiles, divide by 12
            if (monthly) {
                Map<Category, Double> monthlySpend = new EnumMap<>(Category.class);
                spend.forEach((cat, amt) -> monthlySpend.put(cat, Math.round(amt / 12.0 * 100.0) / 100.0));
                spend = monthlySpend;
            }

            String id = prefix + String.format("%03d", i);
            profiles.add(new BenchmarkHarness.BenchProfile(id, monthly, spend));
        }

        return profiles;
    }

    private static int pickActiveCatCount(Random rng) {
        // Weighted: 3=15%, 4=35%, 5=35%, 6=15%
        double r = rng.nextDouble();
        if (r < 0.15) return 3;
        if (r < 0.50) return 4;
        if (r < 0.85) return 5;
        return 6;
    }

    private static List<Category> pickActiveCategories(Random rng, int count) {
        List<Category> pool = new ArrayList<>(Arrays.asList(ALL_CATS));
        Collections.shuffle(pool, rng);
        // Always include OTHER for non-degenerate selection
        List<Category> result = new ArrayList<>(count);
        result.add(Category.OTHER);
        pool.remove(Category.OTHER);
        for (int i = 0; result.size() < count && i < pool.size(); i++) {
            result.add(pool.get(i));
        }
        Collections.sort(result);
        return result;
    }

    private static Map<Category, Double> distributSpend(Random rng, List<Category> active,
                                                         double totalAnnual, DominanceType dominance) {
        Map<Category, Double> spend = new EnumMap<>(Category.class);
        double[] weights = new double[active.size()];

        switch (dominance) {
            case SINGLE_DOMINANT -> {
                // One category gets 50-65% of spend
                int dominant = rng.nextInt(active.size());
                for (int i = 0; i < weights.length; i++) {
                    weights[i] = (i == dominant) ? 0.50 + rng.nextDouble() * 0.15 : 0.05 + rng.nextDouble() * 0.10;
                }
            }
            case MULTI_DOMINANT -> {
                // 2-3 categories each get 20-30%
                int dominantCount = 2 + (rng.nextBoolean() ? 1 : 0);
                Set<Integer> dominants = new HashSet<>();
                while (dominants.size() < dominantCount) {
                    dominants.add(rng.nextInt(active.size()));
                }
                for (int i = 0; i < weights.length; i++) {
                    weights[i] = dominants.contains(i) ? 0.20 + rng.nextDouble() * 0.10 : 0.05 + rng.nextDouble() * 0.08;
                }
            }
            case BALANCED -> {
                // No category > 25%
                for (int i = 0; i < weights.length; i++) {
                    weights[i] = 0.10 + rng.nextDouble() * 0.15;
                }
            }
        }

        // Normalize weights to sum to 1.0
        double sum = 0;
        for (double w : weights) sum += w;
        for (int i = 0; i < weights.length; i++) {
            double amount = Math.round(totalAnnual * weights[i] / sum / 100.0) * 100.0;
            if (amount >= 100) {
                spend.put(active.get(i), amount);
            }
        }

        // Ensure at least OTHER has spend
        if (!spend.containsKey(Category.OTHER)) {
            spend.put(Category.OTHER, 1000.0);
        }

        return spend;
    }

    private static void injectCapBoundary(Random rng, Map<Category, Double> spend) {
        int capIdx = rng.nextInt(COMMON_CAPS.length);
        int cap = COMMON_CAPS[capIdx];
        // Pick a category to adjust to near the cap boundary
        Category target = Category.GROCERIES; // Most caps are on groceries
        if (cap <= 2000 && spend.containsKey(Category.ONLINE)) {
            target = Category.ONLINE;
        }
        // Randomly: slightly below, at, or slightly above cap
        double offset = (rng.nextInt(3) - 1) * 100.0; // -100, 0, or +100
        spend.put(target, (double) cap + offset);
    }

    private enum SpendLevel {
        LOW(8000, 18000),
        MEDIUM(18000, 40000),
        HIGH(40000, 90000);

        final double minAnnual;
        final double maxAnnual;

        SpendLevel(double min, double max) {
            this.minAnnual = min;
            this.maxAnnual = max;
        }
    }

    private enum DominanceType {
        SINGLE_DOMINANT,
        MULTI_DOMINANT,
        BALANCED
    }
}
