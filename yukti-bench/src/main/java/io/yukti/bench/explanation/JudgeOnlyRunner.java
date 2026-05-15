package io.yukti.bench.explanation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.yukti.core.explainability.LlmProvider;
import io.yukti.engine.explainability.llm.LlmProviderId;
import io.yukti.engine.explainability.llm.LlmProviderRegistry;
import io.yukti.evaluation.fluency.FluencyScore;
import io.yukti.evaluation.hallucination.HallucinationReport;
import io.yukti.evaluation.judge.LlmPairwiseJudge;
import io.yukti.evaluation.judge.PreferenceJudge;
import io.yukti.evaluation.runner.ExplanationEvaluator;
import io.yukti.evaluation.runner.MatchedPair;
import io.yukti.evaluation.runner.MatchedPairScore;
import io.yukti.evaluation.runner.NarratorVariant;
import io.yukti.evaluation.runner.PerInstanceEvaluation;
import io.yukti.evaluation.taxonomy.FailureCategory;
import io.yukti.evaluation.verifier.VerifierReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;

/**
 * Loads a previously saved {@code explanation_eval_results.json}, reconstructs a
 * minimal list of {@link PerInstanceEvaluation} stubs from the JSON tree (only
 * the fields needed for pair matching and judging), and runs the judge pass
 * separately. Writes matched scores to a standalone JSON file so the original
 * results file is not modified.
 *
 * <p>Used to recover from cases where the main runner's judge pass was skipped
 * or to re-judge with a different model without redoing the (expensive)
 * narrator pass.
 *
 * <p>Usage:
 * <pre>
 *   ./gradlew :yukti-bench:runJudgeOnly --args="\
 *     artifacts/bench/v2/explanation_eval_results.large-r1.json \
 *     artifacts/bench/v2/matched_pair_scores.large-r1.json \
 *     OPENAI_GPT4O \
 *     150"
 * </pre>
 *
 * <p>Args: results_in, scores_out, judge_model_id, judge_sample_count
 */
public final class JudgeOnlyRunner {

    private JudgeOnlyRunner() {}

    public static void main(String[] args) throws IOException {
        if (args.length < 4) {
            System.err.println("Usage: JudgeOnlyRunner <results_in.json> <scores_out.json> <judge_model_id> <judge_sample_count>");
            System.exit(2);
        }
        Path resultsIn = Paths.get(args[0]);
        Path scoresOut = Paths.get(args[1]);
        LlmProviderId judgeId = LlmProviderId.valueOf(args[2]);
        int judgeSampleCount = Integer.parseInt(args[3]);

        ObjectMapper om = createMapper();
        JsonNode root = om.readTree(resultsIn.toFile());

        List<PerInstanceEvaluation> instances = loadInstanceStubs(root);
        System.out.println("Loaded " + instances.size() + " instance stubs from " + resultsIn);

        ExplanationEvaluator evaluator = new ExplanationEvaluator();
        List<MatchedPair> matched = evaluator.buildMatchedPairs(instances);
        System.out.println("Built " + matched.size() + " matched pairs from " + instances.size() + " instances");

        if (matched.isEmpty()) {
            System.err.println("No matched pairs produced; aborting. Check that the input file has both GROUNDED and UNGROUNDED instances per (profile, goal, model).");
            System.exit(3);
        }

        LlmProviderRegistry reg = LlmProviderRegistry.defaultRegistry();
        Optional<LlmProvider> judgeProvider = reg.get(judgeId);
        if (judgeProvider.isEmpty()) {
            System.err.println("Judge model " + judgeId + " has no credentials (env var missing or blank); aborting.");
            System.exit(4);
        }
        System.out.println("Judging up to " + judgeSampleCount + " pairs (each judged twice for counterbalance, "
            + (Math.min(judgeSampleCount, matched.size()) * 2) + " total judge calls expected)");

        PreferenceJudge judge = new LlmPairwiseJudge(judgeProvider.get());
        List<MatchedPairScore> scores = evaluator.judgeMatchedPairs(matched, judge, judgeSampleCount,
            pair -> "Profile " + pair.profileId() + " goal " + pair.goal());

        System.out.println("Produced " + scores.size() + " matched scores");

        Files.createDirectories(scoresOut.getParent());
        var output = new JudgeOnlyOutput(Instant.now(), judgeId.name(), judgeSampleCount, scores);
        om.writeValue(scoresOut.toFile(), output);
        System.out.println("Wrote " + scoresOut);
    }

    /** Standalone output record so the scores file is self-describing. */
    public record JudgeOnlyOutput(
        Instant ranAt,
        String judgeModel,
        int judgeSampleCount,
        List<MatchedPairScore> matchedPairScores
    ) {}

    /**
     * Reconstruct minimal {@link PerInstanceEvaluation} objects from the JSON tree.
     * Only the fields needed for pair matching and judging are populated; verifier
     * and fluency fields are stubbed out with defaults since the judge does not
     * read them.
     */
    private static List<PerInstanceEvaluation> loadInstanceStubs(JsonNode root) {
        JsonNode arr = root.get("perInstance");
        if (arr == null || !arr.isArray()) {
            throw new IllegalArgumentException("input JSON missing perInstance array");
        }
        List<PerInstanceEvaluation> out = new ArrayList<>(arr.size());
        for (JsonNode n : arr) {
            String profileId = n.get("profileId").asText();
            String goal = n.get("goal").asText();
            String variantStr = n.get("variant").asText();
            String modelIdStr = n.get("modelId").asText();
            String renderedText = n.has("renderedText") ? n.get("renderedText").asText("") : "";
            NarratorVariant variant = NarratorVariant.valueOf(variantStr);
            LlmProviderId modelId = LlmProviderId.valueOf(modelIdStr);
            HallucinationReport halluc = new HallucinationReport(0, 0,
                new EnumMap<>(FailureCategory.class), List.of());
            List<FluencyScore> fluency = List.of();
            VerifierReport vr = VerifierReport.allPass(0);
            out.add(new PerInstanceEvaluation(
                profileId, goal, variant, modelId, renderedText,
                halluc, fluency, vr, false, "stub"
            ));
        }
        return out;
    }

    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        SimpleModule module = new SimpleModule();
        module.addSerializer(Instant.class, new com.fasterxml.jackson.databind.JsonSerializer<>() {
            @Override
            public void serialize(Instant value, com.fasterxml.jackson.core.JsonGenerator gen,
                                  com.fasterxml.jackson.databind.SerializerProvider provider) throws IOException {
                gen.writeString(value.toString());
            }
        });
        mapper.registerModule(module);
        return mapper;
    }
}
