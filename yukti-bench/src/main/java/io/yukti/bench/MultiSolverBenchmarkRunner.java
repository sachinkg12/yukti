package io.yukti.bench;

import java.nio.file.Path;
import java.util.List;

/**
 * Runs benchmark with all 11 optimizers and writes one JSON per optimizer.
 * Supports v1 (50 profiles) and v2 (200 profiles) via io.yukti.bench.version.
 * Supports subset filtering via io.yukti.bench.subset (main/holdout/all).
 */
public class MultiSolverBenchmarkRunner {
    private static final List<String> OPTIMIZER_IDS = List.of(
            "milp-v1",
            "lp-relaxation-v1",
            "cap-aware-greedy-v1",
            "greedy-v1",
            "exhaustive-search-v1",
            "single-best-per-category-v1",
            "content-based-top3",
            "top-k-popular",
            "random-k",
            "ahp-mcdm",
            "ahp-pairwise",
            "rule-based",
            "sa-v1"
    );

    public static void main(String[] args) throws Exception {
        String version = System.getProperty("io.yukti.bench.version", "v1");
        String subset = System.getProperty("io.yukti.bench.subset", "all");
        String penaltyProp = System.getProperty("io.yukti.bench.penalty", "strict");
        String creditsProp = System.getProperty("io.yukti.bench.credits", "on");
        boolean penaltyStrict = !"soft".equalsIgnoreCase(penaltyProp);
        boolean creditsOn = !"off".equalsIgnoreCase(creditsProp);
        String defaultDir = "v2".equals(version) ? "artifacts/bench/v2" : "artifacts/bench/v1";
        Path baseDir = args.length > 0 ? Path.of(args[0]) : Path.of(defaultDir);
        baseDir.toFile().mkdirs();

        String suffix = "all".equals(subset) ? "" : "_" + subset;

        for (String optimizerId : OPTIMIZER_IDS) {
            BenchmarkHarness harness = new BenchmarkHarness(penaltyStrict, creditsOn, optimizerId);
            // Filter profiles by subset
            List<BenchmarkHarness.BenchResult> results = harness.run();
            if ("main".equals(subset)) {
                results = results.stream()
                    .filter(r -> !r.profileId().startsWith("holdout-"))
                    .toList();
            } else if ("holdout".equals(subset)) {
                results = results.stream()
                    .filter(r -> r.profileId().startsWith("holdout-"))
                    .toList();
            }

            Path perSolver = baseDir.resolve(optimizerId + suffix + "_bench_results.json");
            harness.writeResultsJson(perSolver);
            System.out.println("Wrote " + perSolver + " (" + results.size() + " results)");
        }
        System.out.println("Solver comparison complete (" + version + ", subset=" + subset + ").");
    }
}
