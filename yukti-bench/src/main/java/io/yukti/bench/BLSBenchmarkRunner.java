package io.yukti.bench;

import java.nio.file.Path;
import java.util.List;

/**
 * Runs all 11 optimizers on BLS-derived profiles.
 * Outputs one JSON per optimizer to artifacts/bench/v2/bls/.
 *
 * <p>This validates external generalization: do optimizer rankings hold
 * when profiles come from an independent source (BLS CES distributions)
 * rather than the curated/generated benchmark profiles?
 */
public class BLSBenchmarkRunner {

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
            "rule-based"
    );

    public static void main(String[] args) throws Exception {
        Path baseDir = args.length > 0 ? Path.of(args[0]) : Path.of("artifacts/bench/v2/bls");
        baseDir.toFile().mkdirs();

        List<BenchmarkHarness.BenchProfile> blsProfiles = BLSProfileGenerator.generate();
        System.out.println("Generated " + blsProfiles.size() + " BLS profiles");

        for (String optimizerId : OPTIMIZER_IDS) {
            System.out.println("Running " + optimizerId + " on BLS profiles...");
            BenchmarkHarness harness = new BenchmarkHarness(true, true, optimizerId, false, null, blsProfiles);
            Path out = baseDir.resolve(optimizerId + "_bls_bench_results.json");
            harness.writeResultsJson(out);
            System.out.println("Wrote " + out);
        }
        System.out.println("BLS benchmark complete.");
    }
}
