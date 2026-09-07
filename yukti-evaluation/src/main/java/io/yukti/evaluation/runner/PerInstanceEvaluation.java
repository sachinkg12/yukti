package io.yukti.evaluation.runner;

import io.yukti.engine.explainability.llm.LlmProviderId;
import io.yukti.evaluation.fluency.FluencyScore;
import io.yukti.evaluation.hallucination.HallucinationReport;
import io.yukti.evaluation.verifier.VerifierReport;
import io.yukti.explain.core.claims.Claim;

import java.util.List;
import java.util.Objects;

/**
 * Evaluation output for one (profile, goal, narrator variant, model) tuple.
 *
 * <p>Carries two independent verdicts on the rendered text:
 * <ul>
 *   <li>{@code verifierReport} is the production {@code ClaimVerifier}'s four gate
 *       output. This is the deployable, primary metric.
 *   <li>{@code hallucinations} is the heuristic taxonomy classifier used only for
 *       per category analysis.
 * </ul>
 *
 * <p>{@code schemaFailed} flags instances where the narrator could not even produce
 * parseable JSON claims; analysis can exclude these to keep verifier rates honest.
 * {@code fluencySource} records how {@code fluencyScores} were produced. Today it
 * is always {@code "heuristic"} (deterministic metrics over the rendered text);
 * the field exists so a future LLM-judged fluency pipeline can be distinguished
 * without breaking the result-file schema.
 */
public record PerInstanceEvaluation(
    String profileId,
    String goal,
    NarratorVariant variant,
    LlmProviderId modelId,
    String renderedText,
    HallucinationReport hallucinations,
    List<FluencyScore> fluencyScores,
    VerifierReport verifierReport,
    boolean schemaFailed,
    String fluencySource,
    List<Claim> claims
) {
    public PerInstanceEvaluation {
        Objects.requireNonNull(profileId);
        Objects.requireNonNull(goal);
        Objects.requireNonNull(variant);
        Objects.requireNonNull(modelId);
        Objects.requireNonNull(renderedText);
        Objects.requireNonNull(hallucinations);
        fluencyScores = fluencyScores != null ? List.copyOf(fluencyScores) : List.of();
        if (verifierReport == null) {
            verifierReport = VerifierReport.allPass(0);
        }
        if (fluencySource == null) {
            fluencySource = "unknown";
        }
        claims = claims != null ? List.copyOf(claims) : List.of();
    }

    /**
     * Backwards-compatible constructor (pre-claims field). Used by legacy callers
     * that did not pass structured claim objects through.
     */
    public PerInstanceEvaluation(
        String profileId,
        String goal,
        NarratorVariant variant,
        LlmProviderId modelId,
        String renderedText,
        HallucinationReport hallucinations,
        List<FluencyScore> fluencyScores,
        VerifierReport verifierReport,
        boolean schemaFailed,
        String fluencySource
    ) {
        this(profileId, goal, variant, modelId, renderedText, hallucinations, fluencyScores,
            verifierReport, schemaFailed, fluencySource, List.of());
    }

    /**
     * Legacy constructor for tests and any callers that have not yet been migrated
     * to include verifier metadata. Defaults verifierReport to a zero claim pass,
     * schemaFailed to false, and fluencySource to "unknown".
     */
    public PerInstanceEvaluation(
        String profileId,
        String goal,
        NarratorVariant variant,
        LlmProviderId modelId,
        String renderedText,
        HallucinationReport hallucinations,
        List<FluencyScore> fluencyScores
    ) {
        this(profileId, goal, variant, modelId, renderedText, hallucinations, fluencyScores,
            VerifierReport.allPass(0), false, "unknown", List.of());
    }
}
