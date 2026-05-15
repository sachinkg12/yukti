package io.yukti.bench.explanation;

import io.yukti.engine.explainability.llm.LlmProviderId;
import io.yukti.evaluation.runner.EvaluationConfig;
import io.yukti.evaluation.runner.EvaluationJsonWriter;
import io.yukti.evaluation.runner.EvaluationResults;
import io.yukti.evaluation.runner.EvaluationSummarizer;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Entry point for the paired explanation evaluation. Wires the optimizer benchmark
 * results into the evaluation runner and writes JSON outputs for downstream analysis.
 *
 * <p>Usage (from project root):
 * <pre>
 *   ./gradlew :yukti-bench:runExplanationEval
 * </pre>
 *
 * <p>This entry point is intentionally thin. It exists so that production benchmarks
 * remain untouched and the evaluation only runs when explicitly requested. Open Closed
 * is preserved: adding a new narrator variant means adding a new entry in NarratorVariant
 * and wiring it in {@link PairedNarratorRunner}, no changes to this main method.
 */
public final class ExplanationEvalRunner {

    private ExplanationEvalRunner() {}

    public static void main(String[] args) throws IOException {
        Path resultsFile = args.length > 0
            ? Paths.get(args[0])
            : Paths.get("artifacts/bench/v2/explanation_eval_results.json");
        Path summaryFile = args.length > 1
            ? Paths.get(args[1])
            : Paths.get("artifacts/bench/v2/explanation_eval_summary.json");

        // Read via io.yukti.* so the JavaExec task's shared forwarding block
        // (yukti-bench/build.gradle.kts:21-25) actually delivers the property
        // from `gradle -D...` to the forked JVM. The legacy unprefixed name is
        // accepted as a fallback for any saved command lines that still use it.
        String mode = System.getProperty("io.yukti.eval.config",
            System.getProperty("yukti.eval.config", "default"));
        EvaluationConfig baseConfig = switch (mode) {
            case "smoke" -> EvaluationConfig.smokeConfig();
            case "large" -> EvaluationConfig.largeConfig();
            default -> EvaluationConfig.defaultConfig();
        };
        // Optional repair-loop override: -Dio.yukti.eval.repair=N rebuilds the
        // selected config with groundedRepairAttempts=N. Lets the same mode be
        // used for the pre-repair baseline and the post-repair ablation without
        // forking config presets.
        int repair = Integer.parseInt(System.getProperty("io.yukti.eval.repair", "-1"));
        // Optional narrator override: -Dio.yukti.eval.narrator=ID1,ID2 replaces
        // narratorModels with the comma-separated LlmProviderId list. Useful to
        // run the smoke/default config against a single frontier model without
        // adding a new preset.
        String narratorOverride = System.getProperty("io.yukti.eval.narrator");
        List<LlmProviderId> narratorModels = baseConfig.narratorModels();
        if (narratorOverride != null && !narratorOverride.isBlank()) {
            List<LlmProviderId> parsed = new ArrayList<>();
            for (String tok : narratorOverride.split(",")) {
                String t = tok.trim();
                if (!t.isEmpty()) parsed.add(LlmProviderId.valueOf(t));
            }
            if (!parsed.isEmpty()) narratorModels = parsed;
        }
        EvaluationConfig config = (repair < 0 && narratorModels == baseConfig.narratorModels())
            ? baseConfig
            : new EvaluationConfig(
                narratorModels,
                baseConfig.judgeModel(),
                baseConfig.profileCount(),
                baseConfig.judgeSampleCount(),
                baseConfig.seed(),
                repair < 0 ? baseConfig.groundedRepairAttempts() : repair);
        System.out.println("Running explanation eval with config: " + mode
            + " (profiles=" + config.profileCount()
            + ", narratorModels=" + config.narratorModels().size()
            + ", judgeSamples=" + config.judgeSampleCount()
            + ", groundedRepairAttempts=" + config.groundedRepairAttempts() + ")");
        PairedNarratorRunner runner = new PairedNarratorRunner();
        EvaluationResults results = runner.run(config);

        EvaluationJsonWriter writer = new EvaluationJsonWriter();
        // Write summary first so we always have at least the aggregate even if the
        // full results write fails.
        try {
            writer.writeSummary(new EvaluationSummarizer().summarize(results.perInstance()), summaryFile);
            System.out.println("Wrote " + summaryFile);
        } catch (Exception e) {
            System.err.println("Summary write failed: " + e.getMessage());
        }
        try {
            writer.writeResults(results, resultsFile);
            System.out.println("Wrote " + resultsFile);
        } catch (Exception e) {
            System.err.println("Results write failed: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("Instances: " + results.perInstance().size());
        System.out.println("Judged pairs: " + results.judgedPairs().size());
    }
}
