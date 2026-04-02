package io.yukti.bench;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Runs AHP weight sensitivity sweep for Paper Section VI (Table VII-C).
 *
 * <p>Three weight configurations registered in OptimizerRegistry:
 * <ol>
 *   <li>ahp-mcdm (default): (0.45, 0.20, 0.15, 0.20)</li>
 *   <li>ahp-mcdm-fee-heavy: (0.30, 0.35, 0.15, 0.20)</li>
 *   <li>ahp-mcdm-coverage-heavy: (0.30, 0.20, 0.30, 0.20)</li>
 * </ol>
 *
 * <p>For each configuration, runs the benchmark harness on all 150 instances.
 * Gap vs MILP is computed downstream by generate_paper_data.py.
 */
public class AhpWeightSweepRunner {

    private static final List<String> AHP_OPTIMIZER_IDS = List.of(
        "ahp-mcdm",
        "ahp-mcdm-fee-heavy",
        "ahp-mcdm-coverage-heavy"
    );

    public static void main(String[] args) throws Exception {
        Path baseDir = args.length > 0 ? Path.of(args[0]) : Path.of("artifacts/bench/v2/sensitivity");
        Files.createDirectories(baseDir);

        System.out.println("=== AHP Weight Sensitivity Sweep ===");

        for (String optimizerId : AHP_OPTIMIZER_IDS) {
            System.out.println("Running " + optimizerId + "...");
            BenchmarkHarness harness = new BenchmarkHarness(true, true, optimizerId);
            Path outFile = baseDir.resolve(optimizerId + "_bench_results.json");
            harness.writeResultsJson(outFile);
            System.out.println("  -> Wrote " + outFile);
        }

        System.out.println("\nAHP weight sweep complete. " + baseDir);
    }
}
