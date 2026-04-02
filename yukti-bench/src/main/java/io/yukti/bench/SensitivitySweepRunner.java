package io.yukti.bench;

import io.yukti.core.domain.Money;
import io.yukti.core.domain.UserConstraints;
import io.yukti.engine.config.BenchRunConfig;
import io.yukti.engine.valuation.DefaultCppTableLoader;

import java.nio.file.Path;
import java.util.List;

/**
 * Runs sensitivity sweeps for robustness analysis (Paper Section VI).
 *
 * <p>Four sweep dimensions:
 * <ol>
 *   <li>CPP perturbation: scale non-cash CPP values by {0.6, 0.8, 1.0, 1.2, 1.4}</li>
 *   <li>Card count: maxCards ∈ {2, 3, 4}</li>
 *   <li>Fee budget: maxAnnualFee ∈ {$500, $1000, $2000}</li>
 *   <li>Credit utilization: multiplier ∈ {0.0, 0.25, 0.5, 0.75, 1.0} (MILP only)</li>
 * </ol>
 *
 * <p>For each configuration, runs MILP and Greedy on all 150 instances.
 * Results are written to artifacts/bench/v1/sensitivity/ for analysis.
 */
public class SensitivitySweepRunner {

    // Exhaustive search excluded: C(70,3)=54,834 portfolios per instance makes it
    // impractical for 9-config sensitivity sweep (~50 min/config × 9 = 7+ hours).
    // It's already verified to match MILP on all 600 instances in the main benchmark.
    private static final List<String> OPTIMIZER_IDS = List.of(
            "milp-v1",
            "lp-relaxation-v1",
            "cap-aware-greedy-v1",
            "greedy-v1",
            "single-best-per-category-v1",
            "content-based-top3",
            "top-k-popular",
            "random-k",
            "ahp-mcdm",
            "ahp-pairwise",
            "rule-based"
    );
    private static final double[] CPP_SCALES = {0.6, 0.8, 1.0, 1.2, 1.4};
    private static final int[] MAX_CARDS = {2, 3, 4};
    private static final double[] FEE_BUDGETS = {500.0, 1000.0, 2000.0};
    private static final double[] CREDIT_UTILIZATION = {0.0, 0.25, 0.5, 0.75, 1.0};

    public static void main(String[] args) throws Exception {
        Path baseDir = args.length > 0 ? Path.of(args[0]) : Path.of("artifacts/bench/v2/sensitivity");
        baseDir.toFile().mkdirs();

        // 1. CPP perturbation sweep
        System.out.println("=== CPP Perturbation Sweep ===");
        for (double scale : CPP_SCALES) {
            DefaultCppTableLoader.setCppScaleFactor(scale);
            String label = String.format("cpp_%.0f", scale * 100);
            for (String optId : OPTIMIZER_IDS) {
                BenchmarkHarness harness = new BenchmarkHarness(true, true, optId);
                Path out = baseDir.resolve(label + "_" + optId + ".json");
                harness.writeResultsJson(out);
                System.out.println("Wrote " + out);
            }
        }
        DefaultCppTableLoader.setCppScaleFactor(1.0); // Reset

        // 2. Card count sweep
        System.out.println("=== Card Count Sweep ===");
        for (int maxCards : MAX_CARDS) {
            UserConstraints constraints = new UserConstraints(maxCards, Money.usd(1000), true);
            String label = "cards_" + maxCards;
            for (String optId : OPTIMIZER_IDS) {
                BenchmarkHarness harness = new BenchmarkHarness(true, true, optId, false, constraints);
                Path out = baseDir.resolve(label + "_" + optId + ".json");
                harness.writeResultsJson(out);
                System.out.println("Wrote " + out);
            }
        }

        // 3. Fee budget sweep
        System.out.println("=== Fee Budget Sweep ===");
        for (double fee : FEE_BUDGETS) {
            UserConstraints constraints = new UserConstraints(3, Money.usd(fee), true);
            String label = String.format("fee_%.0f", fee);
            for (String optId : OPTIMIZER_IDS) {
                BenchmarkHarness harness = new BenchmarkHarness(true, true, optId, false, constraints);
                Path out = baseDir.resolve(label + "_" + optId + ".json");
                harness.writeResultsJson(out);
                System.out.println("Wrote " + out);
            }
        }

        // 4. Credit utilization sweep (MILP only)
        System.out.println("=== Credit Utilization Sweep ===");
        for (double util : CREDIT_UTILIZATION) {
            BenchRunConfig.setCreditUtilizationMultiplier(util);
            String label = String.format("credit_util_%.0f", util * 100);
            BenchmarkHarness harness = new BenchmarkHarness(true, true, "milp-v1");
            Path out = baseDir.resolve(label + "_milp-v1.json");
            harness.writeResultsJson(out);
            System.out.println("Wrote " + out);
        }
        BenchRunConfig.setCreditUtilizationMultiplier(1.0); // Reset

        System.out.println("\nSensitivity sweep complete. " + baseDir);
    }
}
