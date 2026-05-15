package io.yukti.evaluation.runner;

import io.yukti.engine.explainability.llm.LlmProviderId;
import io.yukti.evaluation.fluency.FluencyScore;
import io.yukti.evaluation.verifier.VerifierGate;
import io.yukti.evaluation.verifier.VerifierReport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Aggregates {@link PerInstanceEvaluation} into per (variant, model) summary rows.
 *
 * <p>Reports both the production verifier pass rate (primary metric) and the
 * heuristic taxonomy detector's hallucination rate (analysis only). Counting
 * uses TreeMap for the per-gate breakdown so output ordering is deterministic
 * across JVM runs.
 */
public final class EvaluationSummarizer {

    private static final String FK = "flesch_kincaid_grade";
    private static final String FRE = "flesch_reading_ease";
    private static final String ASL = "avg_sentence_length";
    private static final String LD = "lexical_diversity";
    private static final String WC = "word_count";

    public List<EvaluationSummaryRow> summarize(List<PerInstanceEvaluation> instances) {
        Map<String, Accumulator> acc = new LinkedHashMap<>();
        for (PerInstanceEvaluation e : instances) {
            String key = e.variant().name() + "|" + e.modelId().name();
            acc.computeIfAbsent(key, k -> new Accumulator(e.variant(), e.modelId())).add(e);
        }
        List<EvaluationSummaryRow> rows = new ArrayList<>();
        for (Accumulator a : acc.values()) rows.add(a.toRow());
        return rows;
    }

    private static final class Accumulator {
        final NarratorVariant variant;
        final LlmProviderId modelId;
        int n = 0;
        double sumHallucRate = 0;
        double sumFk = 0, sumFre = 0, sumAsl = 0, sumLd = 0, sumWc = 0;
        int verifierPassed = 0;
        double sumVerifierFailureRate = 0;
        int schemaFailedCount = 0;
        // TreeMap keyed by gate name keeps the JSON output deterministic.
        Map<String, Integer> failuresByGate = new TreeMap<>();
        // Permissive-emission accumulators: total generated claims across the bucket
        // and total claims that the verifier accepted (would be shipped to the user).
        int totalGeneratedClaims = 0;
        int totalShippedClaims = 0;
        double sumShippedPerInstance = 0;

        Accumulator(NarratorVariant variant, LlmProviderId modelId) {
            this.variant = variant;
            this.modelId = modelId;
        }

        void add(PerInstanceEvaluation e) {
            n++;
            sumHallucRate += e.hallucinations().rate();
            for (FluencyScore s : e.fluencyScores()) {
                switch (s.metricId()) {
                    case FK -> sumFk += s.value();
                    case FRE -> sumFre += s.value();
                    case ASL -> sumAsl += s.value();
                    case LD -> sumLd += s.value();
                    case WC -> sumWc += s.value();
                    default -> { /* unknown metric, skip */ }
                }
            }
            // Schema-failed instances did not produce parseable claims, so the
            // verifier never ran. They must NOT count as a pass: a vacuous
            // VerifierReport.allPass(0) would otherwise let unparseable output
            // inflate the aggregate pass rate. We treat them as fully failing
            // (failure rate = 1.0) and exclude them from the passed count.
            VerifierReport vr = e.verifierReport();
            if (e.schemaFailed()) {
                schemaFailedCount++;
                sumVerifierFailureRate += 1.0;
                // Schema-failed instances ship 0 claims and generate 0 parseable
                // claims, so they contribute 0/0 to the permissive-emission totals.
            } else {
                if (vr.passed()) verifierPassed++;
                sumVerifierFailureRate += vr.failureRate();
                totalGeneratedClaims += vr.totalClaims();
                totalShippedClaims += vr.shippedClaims();
                sumShippedPerInstance += vr.shippedClaims();
            }
            for (Map.Entry<VerifierGate, Integer> entry : vr.failuresByGate().entrySet()) {
                failuresByGate.merge(entry.getKey().name(), entry.getValue(), Integer::sum);
            }
        }

        EvaluationSummaryRow toRow() {
            double d = Math.max(n, 1);
            // Claim survival rate = shipped / generated, aggregated at the bucket
            // level (not the mean of per-instance rates). This matches how the
            // primary deployment number is computed and the way the Python
            // bootstrap resamples claims/instances.
            double survival = totalGeneratedClaims > 0
                ? (double) totalShippedClaims / totalGeneratedClaims
                : 0.0;
            return new EvaluationSummaryRow(
                variant, modelId, n,
                sumHallucRate / d,
                sumFk / d, sumFre / d, sumAsl / d, sumLd / d, sumWc / d,
                verifierPassed / d,
                sumVerifierFailureRate / d,
                schemaFailedCount,
                failuresByGate,
                survival,
                sumShippedPerInstance / d,
                totalGeneratedClaims,
                totalShippedClaims
            );
        }
    }
}
