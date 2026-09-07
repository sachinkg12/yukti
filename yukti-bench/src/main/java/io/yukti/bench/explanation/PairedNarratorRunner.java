package io.yukti.bench.explanation;

import io.yukti.bench.BenchmarkHarness.BenchProfile;
import io.yukti.bench.ProfileGenerator;
import io.yukti.catalog.ClasspathCatalogSource;
import io.yukti.core.api.Catalog;
import io.yukti.core.api.Optimizer;
import io.yukti.core.domain.Category;
import io.yukti.core.domain.GoalType;
import io.yukti.core.domain.Money;
import io.yukti.core.domain.OptimizationRequest;
import io.yukti.core.domain.OptimizationResult;
import io.yukti.core.domain.Period;
import io.yukti.core.domain.SpendProfile;
import io.yukti.core.domain.UserConstraints;
import io.yukti.core.domain.UserGoal;
import io.yukti.core.explainability.LlmProvider;
import io.yukti.core.explainability.StructuredExplanation;
import io.yukti.engine.explainability.EvidenceGraphBuilder;
import io.yukti.engine.explainability.StructuredExplanationBuilder;
import io.yukti.engine.explainability.llm.LlmProviderId;
import io.yukti.engine.explainability.llm.LlmProviderRegistry;
import io.yukti.engine.optimizer.OptimizerRegistry;
import io.yukti.evaluation.judge.LlmPairwiseJudge;
import io.yukti.evaluation.judge.PreferenceJudge;
import io.yukti.evaluation.runner.EvaluationConfig;
import io.yukti.evaluation.runner.EvaluationJsonWriter;
import io.yukti.evaluation.runner.EvaluationResults;
import io.yukti.evaluation.runner.ExplanationEvaluator;
import io.yukti.evaluation.runner.MatchedPair;
import io.yukti.evaluation.runner.MatchedPairScore;
import io.yukti.evaluation.runner.NarratorVariant;
import io.yukti.evaluation.runner.PerInstanceEvaluation;
import io.yukti.evaluation.verifier.VerifierReport;
import io.yukti.evaluation.verifier.VerifierReportFactory;
import io.yukti.explain.core.claims.Claim;
import io.yukti.explain.core.claims.ClaimSchema;
import io.yukti.explain.core.claims.ClaimVerificationFailure;
import io.yukti.explain.core.claims.ClaimVerifier;
import io.yukti.explain.core.claims.VerificationReport;
import io.yukti.explain.core.evidence.graph.EvidenceGraph;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Orchestrates the paired explanation evaluation across catalog profiles, goals,
 * narrator variants, and LLM models.
 *
 * <p>For each (profile, goal):
 * <ol>
 *   <li>Run the MILP optimizer to get an {@link OptimizationResult}.</li>
 *   <li>Build a {@link StructuredExplanation} (with the evidence graph).</li>
 *   <li>For each configured LLM model, call the model once with the grounded prompt
 *       and once with the ungrounded prompt. Capture raw text in both cases.</li>
 *   <li>Use lenient claim parsing so partial output is preserved when a single
 *       claim is malformed.</li>
 *   <li>Run the production {@code ClaimVerifier} on the parsed claims; run the
 *       heuristic taxonomy detector for per-category analysis; run fluency
 *       metrics on the concatenated claim text (or the raw LLM output when no
 *       claims parsed).</li>
 * </ol>
 *
 * <p>After all instances are evaluated, build natural matched pairs of grounded
 * vs ungrounded explanations for the same (profile, goal, model). Each matched
 * pair is judged twice with swapped order to neutralize judge position bias.
 * See {@link io.yukti.evaluation.runner.ExplanationEvaluator#judgeMatchedPairs}.
 */
public final class PairedNarratorRunner {

    private static final String CATALOG_VERSION = "1.0";
    private static final String CATALOG_RESOURCE = "catalog/catalog-v1.json";
    private static final String DEFAULT_OPTIMIZER_ID = "milp-v1";

    private final LlmProviderRegistry registry;
    private final ExplanationEvaluator evaluator;
    private final StructuredExplanationBuilder builder;
    private final Catalog catalog;
    private final OptimizerRegistry optimizerRegistry;
    private final EvidenceGraphBuilder evidenceGraphBuilder = new EvidenceGraphBuilder();
    private final ClaimVerifier claimVerifier = new ClaimVerifier();

    /**
     * Side-channel capture of (claims, allowedEntities, allowedNumbers, per-claim
     * verifier verdict) per evaluated instance. Used by {@link ExplanationEvalRunner}
     * when the rater-extraction system property is set; otherwise unused. Populated
     * in {@link #evaluateOne} without affecting the primary eval output.
     */
    private final List<RaterCaptureRow> raterCapture = new ArrayList<>();

    /** One row per evaluated instance with raw claims + evidence allowlists + per-id evidence type map. */
    public record RaterCaptureRow(
        String profileId,
        String goal,
        String variant,
        String modelId,
        List<String> allowedEntities,
        List<String> allowedNumbers,
        java.util.Map<String, String> evidenceIdToType,
        List<RaterCaptureClaim> claims
    ) {}

    /** One claim with its raw cited fields and per-claim verifier verdict. */
    public record RaterCaptureClaim(
        String claimId,
        String claimType,
        String text,
        List<String> citedEvidenceIds,
        List<String> citedEntities,
        List<String> citedNumbers,
        boolean verifierPassed,
        List<String> verifierErrors
    ) {}

    public PairedNarratorRunner() {
        this(LlmProviderRegistry.defaultRegistry(), new ExplanationEvaluator(),
             new StructuredExplanationBuilder(), new OptimizerRegistry(),
             loadDefaultCatalog());
    }

    public PairedNarratorRunner(
        LlmProviderRegistry registry,
        ExplanationEvaluator evaluator,
        StructuredExplanationBuilder builder,
        OptimizerRegistry optimizerRegistry,
        Catalog catalog
    ) {
        this.registry = registry;
        this.evaluator = evaluator;
        this.builder = builder;
        this.optimizerRegistry = optimizerRegistry;
        this.catalog = catalog;
    }

    public EvaluationResults run(EvaluationConfig config) {
        List<BenchProfile> profiles = ProfileGenerator.generate(config.profileCount(), config.seed(), "eval-");
        List<GoalType> goals = List.of(GoalType.CASHBACK, GoalType.FLEX_POINTS, GoalType.PROGRAM_POINTS);
        Optimizer optimizer = optimizerRegistry.get(DEFAULT_OPTIMIZER_ID);

        List<PerInstanceEvaluation> perInstance = new ArrayList<>();
        int processed = 0;
        for (BenchProfile p : profiles) {
            for (GoalType goal : goals) {
                EvidenceBundle bundle = optimizeAndBuild(p, goal, optimizer);
                StructuredExplanation evidence = bundle.explanation();
                EvidenceGraph graph = bundle.graph();
                String groundedPrompt = NarratorPrompts.groundedPrompt(evidence, graph);
                String ungroundedPrompt = NarratorPrompts.ungroundedPrompt(evidence);
                for (LlmProviderId modelId : config.narratorModels()) {
                    Optional<LlmProvider> providerOpt = registry.get(modelId);
                    if (providerOpt.isEmpty()) {
                        System.err.println("Skipping " + modelId + ": no credentials");
                        continue;
                    }
                    LlmProvider provider = providerOpt.get();
                    RawNarration grounded = callAndParse(provider, groundedPrompt, modelId, p.id(),
                        goal, NarratorVariant.GROUNDED);
                    // Optional repair pass: if the grounded narration has parsed claims
                    // that fail the production verifier, send the model the specific
                    // error list and let it correct itself. This is the realistic
                    // production deployment shape; the first-attempt verifier rate
                    // is still reported separately as the no-repair ablation.
                    grounded = maybeRepair(provider, grounded, graph, modelId, p.id(),
                        goal, groundedPrompt, config.groundedRepairAttempts());
                    perInstance.add(evaluateOne(p.id(), goal, NarratorVariant.GROUNDED, modelId, evidence, graph,
                        grounded));
                    perInstance.add(evaluateOne(p.id(), goal, NarratorVariant.UNGROUNDED, modelId, evidence, graph,
                        callAndParse(provider, ungroundedPrompt, modelId, p.id(), goal, NarratorVariant.UNGROUNDED)));
                }
                processed++;
                if (processed % 3 == 0) {
                    System.out.println("Processed " + processed + " profile/goal pairs ("
                        + perInstance.size() + " evaluations)");
                    // Incremental save so crashes during long runs do not lose all work.
                    saveCheckpoint(perInstance);
                }
            }
        }

        List<MatchedPairScore> matchedScores = judgeMatchedPairs(perInstance, config);
        return new EvaluationResults(Instant.now(), config, perInstance, List.of(), matchedScores);
    }

    private RawNarration callAndParse(LlmProvider provider, String prompt, LlmProviderId modelId,
                                      String profileId, GoalType goal, NarratorVariant variant) {
        try {
            String raw = provider.generate(prompt);
            if (raw == null) raw = "";
            String json = extractJsonArray(raw);
            List<Claim> claims = ClaimSchema.parseClaimsJsonLenient(json);
            boolean schemaFailed = claims.isEmpty() && !json.isBlank();
            return new RawNarration(raw, claims, schemaFailed);
        } catch (RuntimeException e) {
            System.err.println("Provider call failed for " + profileId + "/" + goal + "/" + variant
                + "/" + modelId + ": " + e.getMessage());
            return new RawNarration("", List.of(), true);
        }
    }

    private PerInstanceEvaluation evaluateOne(String profileId, GoalType goal, NarratorVariant variant,
                                              LlmProviderId modelId, StructuredExplanation evidence,
                                              EvidenceGraph graph, RawNarration narration) {
        String renderedText = renderForFluency(narration);
        VerifierReport verifierReport = runVerifier(graph, narration.claims());
        captureForRaters(profileId, goal, variant, modelId, graph, narration.claims());
        return evaluator.evaluate(profileId, goal.name(), variant, modelId, evidence,
            narration.claims(), renderedText,
            verifierReport, narration.schemaFailed(), "heuristic");
    }

    /**
     * Capture per-claim data + evidence allowlists for the rater study extraction
     * pipeline. Re-runs the verifier so we get per-claim pass/fail, which the
     * aggregated VerifierReport hides. The verifier is sub-millisecond
     * ({@link io.yukti.bench.verifier.VerifierMicroBench}), so this is free.
     *
     * <p>Side-channel: does not affect the primary eval output.
     */
    private void captureForRaters(String profileId, GoalType goal, NarratorVariant variant,
                                  LlmProviderId modelId, EvidenceGraph graph, List<Claim> claims) {
        if (claims == null || claims.isEmpty()) return;
        VerificationReport perClaim = claimVerifier.verify(graph, claims);
        Map<String, List<String>> errorsByClaimId = new HashMap<>();
        if (!perClaim.passed()) {
            for (ClaimVerificationFailure f : perClaim.claimErrors()) {
                errorsByClaimId.put(f.claimId(), f.errors());
            }
        }
        List<RaterCaptureClaim> rcClaims = new ArrayList<>();
        for (Claim c : claims) {
            List<String> errs = errorsByClaimId.getOrDefault(c.claimId(), List.of());
            rcClaims.add(new RaterCaptureClaim(
                c.claimId(),
                c.claimType().name(),
                c.text(),
                c.citedEvidenceIds(),
                c.citedEntities(),
                c.citedNumbers(),
                errs.isEmpty(),
                errs
            ));
        }
        // Sort allowlists for stable serialization
        List<String> entities = new ArrayList<>(new TreeSet<>(graph.getAllowedEntities()));
        List<String> numbers = new ArrayList<>(new TreeSet<>(graph.getAllowedNumbers()));
        // Build evidence id -> type map so downstream rater tooling can show
        // human-readable type labels instead of opaque SHA-256 hashes.
        java.util.Map<String, String> evidenceIdToType = new java.util.TreeMap<>();
        for (var node : graph.getNodes()) {
            evidenceIdToType.put(node.evidenceId(), node.type());
        }
        raterCapture.add(new RaterCaptureRow(
            profileId, goal.name(), variant.name(), modelId.name(),
            entities, numbers, evidenceIdToType, rcClaims
        ));
    }

    /** Exposes the side-channel capture for downstream serialization. */
    public List<RaterCaptureRow> raterCapture() {
        return raterCapture;
    }

    /**
     * Run the production {@link ClaimVerifier} against the parsed claims and adapt
     * the report into the eval side {@link VerifierReport}. If no claims were parsed
     * we report an all pass with zero claims rather than synthesising a failure;
     * the {@code schemaFailed} flag on the instance separately signals that case.
     */
    private VerifierReport runVerifier(EvidenceGraph graph, List<Claim> claims) {
        if (claims == null || claims.isEmpty()) {
            return VerifierReport.allPass(0);
        }
        VerificationReport prodReport = claimVerifier.verify(graph, claims);
        return VerifierReportFactory.from(prodReport, claims.size());
    }

    /**
     * If the grounded narration has verifier errors, re-prompt the model up to
     * {@code maxAttempts} times with the error list appended.
     *
     * <p>Returns the narration with the lowest {@code failingClaims} count seen
     * across the initial attempt and all repair attempts (NOT the most recent
     * attempt — a worse final attempt is discarded). A pass at any point
     * short-circuits the loop. Schema failures and zero-claim outputs are not
     * repaired.
     */
    private RawNarration maybeRepair(LlmProvider provider, RawNarration initial,
                                     EvidenceGraph graph, LlmProviderId modelId,
                                     String profileId, GoalType goal,
                                     String basePrompt, int maxAttempts) {
        if (maxAttempts <= 0) return initial;
        if (initial.claims().isEmpty()) return initial;
        VerifierReport report = runVerifier(graph, initial.claims());
        if (report.passed()) return initial;

        RawNarration best = initial;
        VerifierReport bestReport = report;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String repairPrompt = buildRepairPrompt(basePrompt, bestReport);
            RawNarration repaired = callAndParse(provider, repairPrompt, modelId, profileId, goal,
                NarratorVariant.GROUNDED);
            if (repaired.claims().isEmpty()) break;
            VerifierReport repairedReport = runVerifier(graph, repaired.claims());
            if (repairedReport.failingClaims() < bestReport.failingClaims()) {
                best = repaired;
                bestReport = repairedReport;
            }
            if (repairedReport.passed()) return repaired;
        }
        return best;
    }

    private static String buildRepairPrompt(String basePrompt, VerifierReport report) {
        StringBuilder sb = new StringBuilder(basePrompt);
        sb.append("\n\nYour previous output failed the claim verifier with these errors:\n");
        int shown = 0;
        for (String err : report.allErrors()) {
            if (shown++ >= 10) break;
            sb.append("- ").append(err).append('\n');
        }
        sb.append("\nProduce a corrected JSON array. Cite only evidence ids, entities, and ")
          .append("numbers listed in the prompt above. Use exactly the strings provided.\n");
        return sb.toString();
    }

    /** Use claim text if available, otherwise fall back to raw LLM output. */
    private static String renderForFluency(RawNarration narration) {
        if (!narration.claims().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Claim c : narration.claims()) {
                if (c.text() != null && !c.text().isBlank()) sb.append(c.text()).append(' ');
            }
            return sb.toString().trim();
        }
        return narration.rawText();
    }

    private static String extractJsonArray(String text) {
        String trimmed = text.trim();
        int fenceStart = trimmed.indexOf("```");
        if (fenceStart >= 0) {
            int fenceEnd = trimmed.indexOf("```", fenceStart + 3);
            if (fenceEnd > fenceStart) {
                String inner = trimmed.substring(fenceStart + 3, fenceEnd).trim();
                if (inner.startsWith("json")) inner = inner.substring(4).trim();
                return inner;
            }
        }
        int start = trimmed.indexOf('[');
        int end = trimmed.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private EvidenceBundle optimizeAndBuild(BenchProfile p, GoalType goal, Optimizer optimizer) {
        SpendProfile spend = toSpendProfile(p);
        OptimizationRequest req = new OptimizationRequest(
            spend,
            UserGoal.of(goal),
            UserConstraints.defaults(),
            Map.of()
        );
        OptimizationResult result = optimizer.optimize(req, catalog);
        String primaryCurrency = (goal == GoalType.PROGRAM_POINTS) ? "AVIOS" : null;
        StructuredExplanation explanation = builder.build(result, CATALOG_VERSION, goal, primaryCurrency);
        // Build the full EvidenceGraph here (StructuredExplanation only carries the
        // digest and evidenceIds; the verifier needs the full graph with
        // allowedEntities, allowedNumbers, and node types).
        EvidenceGraph graph = evidenceGraphBuilder.build(result);
        return new EvidenceBundle(explanation, graph);
    }

    /** Holds the eval-facing explanation and the production verifier-facing graph. */
    private record EvidenceBundle(StructuredExplanation explanation, EvidenceGraph graph) {}

    private static SpendProfile toSpendProfile(BenchProfile p) {
        Map<Category, Money> amounts = new EnumMap<>(Category.class);
        p.spend().forEach((cat, v) -> amounts.put(cat, Money.usd(v)));
        return new SpendProfile(p.monthly() ? Period.MONTHLY : Period.ANNUAL, amounts);
    }

    private List<MatchedPairScore> judgeMatchedPairs(List<PerInstanceEvaluation> instances, EvaluationConfig config) {
        if (config.judgeSampleCount() <= 0) return List.of();
        Optional<LlmProvider> judgeProvider = registry.get(config.judgeModel());
        if (judgeProvider.isEmpty()) {
            System.err.println("Judge model " + config.judgeModel() + " has no credentials, skipping judge pass");
            return List.of();
        }
        PreferenceJudge judge = new LlmPairwiseJudge(judgeProvider.get());
        List<MatchedPair> matched = evaluator.buildMatchedPairs(instances);
        System.out.println("Built " + matched.size() + " matched pairs from "
            + instances.size() + " instances; judging up to " + config.judgeSampleCount() + " pairs (each judged twice)");
        return evaluator.judgeMatchedPairs(matched, judge, config.judgeSampleCount(),
            pair -> "Profile " + pair.profileId() + " goal " + pair.goal());
    }

    private static Catalog loadDefaultCatalog() {
        try {
            return new ClasspathCatalogSource(CATALOG_RESOURCE).load(CATALOG_VERSION);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load default catalog", e);
        }
    }

    private static final java.nio.file.Path CHECKPOINT_PATH =
        java.nio.file.Paths.get("artifacts/bench/v2/explanation_eval_checkpoint.json");

    /**
     * Save the current per-instance list to a checkpoint file. Used as defensive
     * snapshotting during long runs so a crash at the end does not wipe progress.
     */
    private static void saveCheckpoint(List<PerInstanceEvaluation> instances) {
        try {
            new EvaluationJsonWriter().writeSummary(
                new io.yukti.evaluation.runner.EvaluationSummarizer().summarize(instances),
                CHECKPOINT_PATH
            );
        } catch (Exception e) {
            // checkpoint best effort; do not crash the run
            System.err.println("Checkpoint save failed: " + e.getMessage());
        }
    }
}
