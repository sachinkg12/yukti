package io.yukti.evaluation.runner;

import io.yukti.core.explainability.LlmProvider;
import io.yukti.core.explainability.StructuredExplanation;
import io.yukti.engine.explainability.llm.LlmProviderId;
import io.yukti.evaluation.fluency.FluencyMetric;
import io.yukti.evaluation.fluency.FluencyMetricRegistry;
import io.yukti.evaluation.fluency.FluencyScore;
import io.yukti.evaluation.hallucination.EvidenceBoundDetector;
import io.yukti.evaluation.hallucination.HallucinationDetector;
import io.yukti.evaluation.hallucination.HallucinationReport;
import io.yukti.evaluation.judge.PreferenceJudge;
import io.yukti.evaluation.verifier.VerifierReport;
import io.yukti.explain.core.claims.Claim;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Orchestrates a single instance evaluation: given a structured explanation and a
 * narrator output, produces a {@link PerInstanceEvaluation}.
 *
 * <p>Open Closed: callers inject the detector, metrics, and judge. The orchestrator
 * does not know which models or rules are in use.
 */
public final class ExplanationEvaluator {

    private final HallucinationDetector detector;
    private final FluencyMetricRegistry fluencyMetrics;

    public ExplanationEvaluator() {
        this(new EvidenceBoundDetector(), FluencyMetricRegistry.defaultRegistry());
    }

    public ExplanationEvaluator(HallucinationDetector detector, FluencyMetricRegistry fluencyMetrics) {
        if (detector == null) throw new IllegalArgumentException("detector must be non-null");
        if (fluencyMetrics == null) throw new IllegalArgumentException("fluencyMetrics must be non-null");
        this.detector = detector;
        this.fluencyMetrics = fluencyMetrics;
    }

    public PerInstanceEvaluation evaluate(
        String profileId,
        String goal,
        NarratorVariant variant,
        LlmProviderId modelId,
        StructuredExplanation evidence,
        List<Claim> claims,
        String renderedText
    ) {
        return evaluate(profileId, goal, variant, modelId, evidence, claims, renderedText,
            VerifierReport.allPass(claims == null ? 0 : claims.size()),
            false, "heuristic");
    }

    /**
     * Full overload including the production verifier report and instance metadata.
     * Used by the bench runner; the shorter overload is kept for tests that do not
     * exercise the production verifier path.
     */
    public PerInstanceEvaluation evaluate(
        String profileId,
        String goal,
        NarratorVariant variant,
        LlmProviderId modelId,
        StructuredExplanation evidence,
        List<Claim> claims,
        String renderedText,
        VerifierReport verifierReport,
        boolean schemaFailed,
        String fluencySource
    ) {
        HallucinationReport report = detector.detect(evidence, claims);
        List<FluencyScore> scores = new ArrayList<>();
        for (FluencyMetric m : fluencyMetrics.all()) {
            scores.add(m.score(renderedText));
        }
        return new PerInstanceEvaluation(
            profileId, goal, variant, modelId, renderedText, report, scores,
            verifierReport, schemaFailed, fluencySource,
            claims != null ? claims : List.of());
    }

    /**
     * @deprecated Use {@link #buildMatchedPairs} + {@link #judgeMatchedPairs} for the
     *             counterbalanced paired preference protocol. This method samples arbitrary
     *             cross variant pairs without counterbalancing and is retained only for
     *             backward compatibility. Do not mix its output with matched-pair results
     *             in a single report.
     */
    @Deprecated
    public List<JudgedPair> judgePairs(
        List<PerInstanceEvaluation> instances,
        PreferenceJudge judge,
        int maxPairs,
        Function<PerInstanceEvaluation, String> contextSummarizer
    ) {
        if (judge == null) return List.of();
        List<JudgedPair> out = new ArrayList<>();
        int count = 0;
        for (int i = 0; i < instances.size() && count < maxPairs; i++) {
            for (int j = i + 1; j < instances.size() && count < maxPairs; j++) {
                PerInstanceEvaluation a = instances.get(i);
                PerInstanceEvaluation b = instances.get(j);
                if (!a.profileId().equals(b.profileId()) || !a.goal().equals(b.goal())) continue;
                if (a.variant() == b.variant()) continue;
                String context = contextSummarizer.apply(a);
                var vote = judge.judge(context, a.renderedText(), b.renderedText());
                out.add(new JudgedPair(a.profileId(), a.goal(), a.variant(), b.variant(), vote));
                count++;
            }
        }
        return out;
    }

    /**
     * Build natural matched pairs from per instance evaluations. Each matched pair has
     * exactly one grounded and one ungrounded instance with the same (profile, goal, model).
     *
     * <p>Pairs are returned in deterministic order, sorted by (profileId, goal, modelId).
     * Instances without a counterpart are skipped. Duplicate keys are kept as the last seen.
     */
    public List<MatchedPair> buildMatchedPairs(List<PerInstanceEvaluation> instances) {
        // TreeMap gives deterministic lexicographic key order across JVM runs.
        Map<String, PerInstanceEvaluation> grounded = new TreeMap<>();
        Map<String, PerInstanceEvaluation> ungrounded = new TreeMap<>();
        for (PerInstanceEvaluation e : instances) {
            String key = e.profileId() + "|" + e.goal() + "|" + e.modelId().name();
            if (e.variant() == NarratorVariant.GROUNDED) {
                grounded.put(key, e);
            } else if (e.variant() == NarratorVariant.UNGROUNDED) {
                ungrounded.put(key, e);
            }
        }
        List<MatchedPair> out = new ArrayList<>();
        for (var entry : grounded.entrySet()) {
            PerInstanceEvaluation u = ungrounded.get(entry.getKey());
            if (u == null) continue;
            PerInstanceEvaluation g = entry.getValue();
            out.add(new MatchedPair(g.profileId(), g.goal(), g.modelId(), g, u));
        }
        return out;
    }

    /**
     * Judge each matched pair twice with swapped order to neutralize judge position bias.
     * Returns counterbalanced scores. Iteration is capped at {@code maxPairs} natural
     * pairs (each consumes two judge calls).
     */
    public List<MatchedPairScore> judgeMatchedPairs(
        List<MatchedPair> pairs,
        PreferenceJudge judge,
        int maxPairs,
        Function<MatchedPair, String> contextSummarizer
    ) {
        if (judge == null || pairs == null || pairs.isEmpty()) return List.of();
        List<MatchedPairScore> out = new ArrayList<>();
        int limit = Math.min(maxPairs, pairs.size());
        for (int i = 0; i < limit; i++) {
            MatchedPair p = pairs.get(i);
            String context = contextSummarizer.apply(p);
            // Ordering 1: grounded as A, ungrounded as B
            var vote1 = judge.judge(context, p.grounded().renderedText(), p.ungrounded().renderedText());
            OrderedJudgement order1 = new OrderedJudgement(
                NarratorVariant.GROUNDED, NarratorVariant.UNGROUNDED, vote1);
            // Ordering 2: ungrounded as A, grounded as B
            var vote2 = judge.judge(context, p.ungrounded().renderedText(), p.grounded().renderedText());
            OrderedJudgement order2 = new OrderedJudgement(
                NarratorVariant.UNGROUNDED, NarratorVariant.GROUNDED, vote2);
            out.add(MatchedPairScore.build(p.profileId(), p.goal(), p.modelId(), order1, order2));
        }
        return out;
    }
}
