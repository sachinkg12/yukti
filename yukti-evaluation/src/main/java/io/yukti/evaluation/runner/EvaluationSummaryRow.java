package io.yukti.evaluation.runner;

import io.yukti.engine.explainability.llm.LlmProviderId;

import java.util.Map;
import java.util.Objects;

/**
 * One row of the aggregated summary: per (variant, model) verifier and taxonomy
 * rates plus mean fluency scores.
 *
 * <p>Two independent rates are reported.
 * <ul>
 *   <li>{@code verifierPassRate} is the share of instances that BOTH produced
 *       parseable claims AND had the production {@code ClaimVerifier} return
 *       passed=true. Schema-failed instances are counted as not-passed; the
 *       denominator is still the total instance count for the (variant, model)
 *       bucket. This is the primary grounding metric.
 *   <li>{@code meanHallucinationRate} is the heuristic taxonomy detector's mean
 *       rate, used only for per category analysis.
 * </ul>
 *
 * <p>{@code meanVerifierFailureRate} is the mean of per-instance failure rates
 * with a deliberately strict treatment of schema failures: each schema-failed
 * instance contributes 1.0, even though no claims were parsed. That makes this
 * field <em>instance-level strictness</em>. It is NOT directly comparable to
 * the claim-level density reported by the Python analysis scripts as
 * {@code verifier_claim_failure_rate}, which is {@code failingClaims /
 * totalClaims} aggregated across instances and contributes 0 for empty
 * (schema-failed) outputs. Pair the two with care.
 *
 * <p>{@code schemaFailedCount} reports the schema-failure count separately so
 * readers can see how much of any pass-rate gap is parse failure vs verified
 * failure. {@code failuresByGate} is the sum across all instances of how many
 * claims failed each of the four verifier gates (per-claim counting — a single
 * claim contributes at most one to each gate it triggered, even if the verifier
 * emitted multiple errors for that claim within a single gate).
 */
public record EvaluationSummaryRow(
    NarratorVariant variant,
    LlmProviderId modelId,
    int instanceCount,
    double meanHallucinationRate,
    double meanFleschKincaid,
    double meanFleschReadingEase,
    double meanSentenceLength,
    double meanLexicalDiversity,
    double meanWordCount,
    double verifierPassRate,
    double meanVerifierFailureRate,
    int schemaFailedCount,
    Map<String, Integer> failuresByGate,
    double claimSurvivalRate,
    double meanShippedClaimsPerInstance,
    int totalGeneratedClaims,
    int totalShippedClaims
) {
    public EvaluationSummaryRow {
        Objects.requireNonNull(variant);
        Objects.requireNonNull(modelId);
        if (instanceCount < 0) throw new IllegalArgumentException("instanceCount must be >= 0");
        failuresByGate = failuresByGate != null ? Map.copyOf(failuresByGate) : Map.of();
    }

    /**
     * Constructor pre-{@code claimSurvivalRate}/{@code meanShippedClaimsPerInstance}.
     * Defaults the new permissive-emission fields to zero so callers and tests written
     * before the co-primary metric was added still compile.
     */
    public EvaluationSummaryRow(
        NarratorVariant variant,
        LlmProviderId modelId,
        int instanceCount,
        double meanHallucinationRate,
        double meanFleschKincaid,
        double meanFleschReadingEase,
        double meanSentenceLength,
        double meanLexicalDiversity,
        double meanWordCount,
        double verifierPassRate,
        double meanVerifierFailureRate,
        int schemaFailedCount,
        Map<String, Integer> failuresByGate
    ) {
        this(variant, modelId, instanceCount, meanHallucinationRate, meanFleschKincaid,
            meanFleschReadingEase, meanSentenceLength, meanLexicalDiversity, meanWordCount,
            verifierPassRate, meanVerifierFailureRate, schemaFailedCount, failuresByGate,
            0.0, 0.0, 0, 0);
    }

    /**
     * Legacy constructor for callers and tests written before the verifier fields
     * were added. Defaults the new fields to zero / empty.
     */
    public EvaluationSummaryRow(
        NarratorVariant variant,
        LlmProviderId modelId,
        int instanceCount,
        double meanHallucinationRate,
        double meanFleschKincaid,
        double meanFleschReadingEase,
        double meanSentenceLength,
        double meanLexicalDiversity,
        double meanWordCount
    ) {
        this(variant, modelId, instanceCount, meanHallucinationRate, meanFleschKincaid,
            meanFleschReadingEase, meanSentenceLength, meanLexicalDiversity, meanWordCount,
            0.0, 0.0, 0, Map.of(),
            0.0, 0.0, 0, 0);
    }
}
