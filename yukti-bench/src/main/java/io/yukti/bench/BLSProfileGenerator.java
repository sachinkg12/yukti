package io.yukti.bench;

import io.yukti.core.domain.Category;

import java.util.*;

/**
 * Generates benchmark profiles using distributional parameters from the
 * U.S. Bureau of Labor Statistics Consumer Expenditure Survey (CES).
 *
 * <p>BLS CES publishes mean annual expenditures by income quintile for
 * categories that map to Yukti's spend categories. We use published means
 * and coefficients of variation to parameterize log-normal distributions
 * and sample 50 profiles (10 per quintile) with seed=2026 for reproducibility.
 *
 * <p>Category mapping:
 * <ul>
 *   <li>Food at home → GROCERIES</li>
 *   <li>Food away from home → DINING</li>
 *   <li>Gasoline, other fuels → GAS</li>
 *   <li>Public/other transportation → TRAVEL</li>
 *   <li>Entertainment → ONLINE (proxy for discretionary online)</li>
 *   <li>All other expenditures → OTHER</li>
 * </ul>
 *
 * <p>Source: BLS CES 2022 Annual Report, Table 1101 (quintiles of income
 * before taxes). Values are approximate mid-range estimates used for
 * benchmarking, not exact replication.
 *
 * <p>Deterministic: seeded RNG produces identical profiles across runs.
 */
public final class BLSProfileGenerator {

    private static final long DEFAULT_SEED = 2026;
    private static final String DEFAULT_PREFIX = "bls-";

    /**
     * BLS CES quintile parameters: (mean, cv) per category per quintile.
     * cv = coefficient of variation (std/mean), typically 0.3-0.6 for spending data.
     *
     * Quintiles: Q1 (lowest 20%), Q2, Q3, Q4, Q5 (highest 20%).
     * Values in USD/year, approximate from CES 2022 tables.
     */
    private static final double[][][] QUINTILE_PARAMS = {
        // Q1: lowest 20% income (< ~$24K)
        {
            // {mean, cv} for: GROCERIES, DINING, GAS, TRAVEL, ONLINE, OTHER
            {3200, 0.40}, {1200, 0.50}, {1100, 0.45}, {400, 0.60}, {800, 0.50}, {2800, 0.40},
        },
        // Q2: second 20% (~$24K-$44K)
        {
            {4100, 0.35}, {2000, 0.45}, {1600, 0.40}, {800, 0.55}, {1200, 0.45}, {4200, 0.35},
        },
        // Q3: middle 20% (~$44K-$72K)
        {
            {5000, 0.35}, {2800, 0.40}, {2100, 0.40}, {1500, 0.50}, {1800, 0.40}, {5800, 0.35},
        },
        // Q4: fourth 20% (~$72K-$117K)
        {
            {6200, 0.30}, {3800, 0.40}, {2600, 0.35}, {2800, 0.45}, {2500, 0.40}, {8000, 0.30},
        },
        // Q5: highest 20% (> ~$117K)
        {
            {8500, 0.30}, {6000, 0.35}, {3200, 0.35}, {5500, 0.40}, {4000, 0.35}, {14000, 0.30},
        },
    };

    private static final Category[] MAPPED_CATEGORIES = {
        Category.GROCERIES, Category.DINING, Category.GAS,
        Category.TRAVEL, Category.ONLINE, Category.OTHER,
    };

    private BLSProfileGenerator() {}

    /**
     * Generate 50 BLS-derived profiles: 10 per income quintile.
     * Uses log-normal sampling with seeded RNG.
     *
     * @return list of 50 profiles with IDs "bls-q{1-5}-{00-09}"
     */
    public static List<BenchmarkHarness.BenchProfile> generate() {
        return generate(50, DEFAULT_SEED, DEFAULT_PREFIX);
    }

    /**
     * Generate BLS-derived profiles.
     *
     * @param count total profiles (must be divisible by 5)
     * @param seed RNG seed for reproducibility
     * @param prefix profile ID prefix
     * @return list of profiles in stable order
     */
    public static List<BenchmarkHarness.BenchProfile> generate(int count, long seed, String prefix) {
        if (count % 5 != 0) {
            throw new IllegalArgumentException("Count must be divisible by 5 (one group per quintile), got " + count);
        }
        int perQuintile = count / 5;
        Random rng = new Random(seed);
        List<BenchmarkHarness.BenchProfile> profiles = new ArrayList<>(count);

        for (int q = 0; q < 5; q++) {
            for (int i = 0; i < perQuintile; i++) {
                String id = prefix + "q" + (q + 1) + "-" + String.format("%02d", i);
                Map<Category, Double> spend = new EnumMap<>(Category.class);

                for (int c = 0; c < MAPPED_CATEGORIES.length; c++) {
                    double mean = QUINTILE_PARAMS[q][c][0];
                    double cv = QUINTILE_PARAMS[q][c][1];
                    double sampled = sampleLogNormal(rng, mean, cv);
                    // Round to nearest $100
                    double rounded = Math.round(sampled / 100.0) * 100.0;
                    if (rounded >= 100) {
                        spend.put(MAPPED_CATEGORIES[c], rounded);
                    }
                }

                // Ensure OTHER always has spend
                if (!spend.containsKey(Category.OTHER)) {
                    spend.put(Category.OTHER, 1000.0);
                }

                profiles.add(new BenchmarkHarness.BenchProfile(id, false, spend));
            }
        }

        return profiles;
    }

    /**
     * Sample from a log-normal distribution parameterized by arithmetic mean and CV.
     *
     * <p>For spending data, log-normal is a standard choice because:
     * (1) spending is non-negative, (2) the distribution is right-skewed,
     * (3) BLS reports means that are above medians, consistent with log-normal.
     *
     * <p>Given arithmetic mean μ and CV σ/μ:
     *   σ² = ln(1 + CV²)
     *   μ_ln = ln(μ) - σ²/2
     *   X ~ exp(μ_ln + σ * Z) where Z ~ N(0,1)
     */
    private static double sampleLogNormal(Random rng, double mean, double cv) {
        double sigmaSquared = Math.log(1 + cv * cv);
        double muLn = Math.log(mean) - sigmaSquared / 2.0;
        double sigmaLn = Math.sqrt(sigmaSquared);
        double z = rng.nextGaussian();
        return Math.exp(muLn + sigmaLn * z);
    }
}
