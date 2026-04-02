package io.yukti.engine.explainability;

import io.yukti.core.domain.GoalType;
import io.yukti.core.domain.OptimizationResult;
import io.yukti.core.explainability.ExplanationGenerator;
import io.yukti.core.explainability.Narrator;
import io.yukti.core.explainability.NarrationException;
import io.yukti.core.explainability.NarrativeExplanation;
import io.yukti.core.explainability.StructuredExplanation;
import io.yukti.explain.core.claims.ClaimsDigest;
import io.yukti.explain.core.claims.Claim;
import io.yukti.explain.core.claims.ClaimVerifier;
import io.yukti.explain.core.claims.NormalizedClaim;
import io.yukti.explain.core.claims.NormalizedClaimParser;
import io.yukti.explain.core.claims.VerificationReport;
import io.yukti.explain.core.claims.VerificationResult;
import io.yukti.explain.core.evidence.graph.EvidenceGraph;

import java.util.List;

/**
 * Produces narrative from optimization result. Narrative is always from verified claims only.
 * Optional LLM path: gated by feature flag (disabled by default), schema validation, and ClaimVerifier.
 * If validation or verification fails, falls back to deterministic claims.
 */
public final class ExplanationServiceV1 {
    private static final String ENV_NARRATION_MODE = "NARRATION_MODE";
    private static final String ENV_LLM_CLAIMS_ENABLED = "NARRATION_LLM_ENABLED";
    private static final String MODE_LLM = "llm";

    private final ExplanationGenerator generator;
    private final Narrator narrator;
    private final LlmClaimGenerator llmClaimGenerator;
    private final boolean llmEnabled;
    private final StructuredExplanationBuilder builder;
    private final EvidenceGraphBuilder graphBuilder = new EvidenceGraphBuilder();
    private final ClaimVerifier verifier = new ClaimVerifier();

    /** Default: no LLM claim generator, feature flag from env (default false). */
    public ExplanationServiceV1(ExplanationGenerator generator, Narrator narrator, StructuredExplanationBuilder builder) {
        this(generator, narrator, builder, null, isLlmClaimsEnabledEnv());
    }

    /** For tests and API: inject LlmClaimGenerator and flag. */
    public ExplanationServiceV1(
        ExplanationGenerator generator,
        Narrator narrator,
        StructuredExplanationBuilder builder,
        LlmClaimGenerator llmClaimGenerator,
        boolean llmEnabled
    ) {
        this.generator = generator;
        this.narrator = narrator;
        this.builder = builder;
        this.llmClaimGenerator = llmClaimGenerator;
        this.llmEnabled = llmEnabled;
    }

    private static boolean isLlmClaimsEnabledEnv() {
        return "true".equalsIgnoreCase(System.getenv(ENV_LLM_CLAIMS_ENABLED));
    }

    public NarrativeExplanation explain(
        OptimizationResult result,
        String catalogVersion,
        GoalType goalType,
        String primaryCurrencyOrNull
    ) {
        return explain(result, catalogVersion, goalType, primaryCurrencyOrNull, false);
    }

    /** For tests: set useLlmNarration=true to bypass env check for LLM attempt. */
    public NarrativeExplanation explain(
        OptimizationResult result,
        String catalogVersion,
        GoalType goalType,
        String primaryCurrencyOrNull,
        boolean useLlmNarration
    ) {
        StructuredExplanation structured = builder.build(result, catalogVersion, goalType, primaryCurrencyOrNull);
        EvidenceGraph graph = graphBuilder.build(result);
        NarrativeExplanation deterministic = generator.generate(structured);

        // 1) LLM claim path (schema v1 + verify): feature flag and generator required
        if ((useLlmNarration || llmEnabled) && llmClaimGenerator != null) {
            try {
                String json = llmClaimGenerator.generateClaimsJson(structured);
                List<NormalizedClaim> claims = NormalizedClaimParser.parseArray(json);
                if (!claims.isEmpty()) {
                    VerificationReport report = verifier.verifyNormalized(graph, claims);
                    if (report.passed()) {
                        return enrichWithVerification(renderFromNormalizedClaims(structured, claims), graph);
                    }
                }
            } catch (LlmClaimException | RuntimeException e) {
                // Fallback to deterministic
            }
        }

        // 2) Legacy narrator path (no schema v1 validation)
        boolean attemptLegacyLlm = useLlmNarration || MODE_LLM.equalsIgnoreCase(System.getenv(ENV_NARRATION_MODE));
        if (attemptLegacyLlm && narrator != null) {
            try {
                List<Claim> llmClaims = narrator.narrate(structured);
                if (llmClaims != null && !llmClaims.isEmpty()) {
                    VerificationResult vr = VerificationResult.fromReport(verifier.verify(graph, llmClaims));
                    if (vr.passed()) {
                        return enrichWithVerification(generator.renderFromClaims(structured, llmClaims), graph);
                    }
                }
            } catch (NarrationException | RuntimeException e) {
                // Fallback to deterministic
            }
        }

        NarrativeExplanation out = enrichWithVerification(deterministic, graph);
        // Fail closed: deterministic path must pass verification (paper §5.2).
        VerificationReport detReport = verifier.verify(graph, out.claims());
        if (!detReport.passed()) {
            throw new IllegalStateException("Deterministic claim verification failed (fail closed): " + detReport.allViolations());
        }
        return out;
    }

    private NarrativeExplanation enrichWithVerification(NarrativeExplanation explanation, EvidenceGraph graph) {
        VerificationReport report = verifier.verify(graph, explanation.claims());
        String claimsDigest = ClaimsDigest.compute(explanation.claims());
        int count = explanation.claims().size();
        int errorCount = report.allViolations().size();
        String status = report.passed() ? "PASS" : "FAIL";
        return new NarrativeExplanation(
            explanation.claims(),
            explanation.summary(),
            explanation.allocationTable(),
            explanation.details(),
            explanation.assumptions(),
            explanation.fullText(),
            explanation.evidenceGraphDigest(),
            explanation.evidenceIds(),
            claimsDigest,
            status,
            count,
            errorCount
        );
    }

    private NarrativeExplanation renderFromNormalizedClaims(StructuredExplanation structured, List<NormalizedClaim> normalizedClaims) {
        List<Claim> claims = normalizedClaims.stream()
            .map(nc -> new Claim(
                nc.claimId(),
                nc.claimType(),
                ClaimRenderer.renderClaimLine(nc),
                nc.citedEvidenceIds(),
                nc.citedEntities(),
                nc.citedNumbers()
            ))
            .toList();
        return generator.renderFromClaims(structured, claims);
    }
}
