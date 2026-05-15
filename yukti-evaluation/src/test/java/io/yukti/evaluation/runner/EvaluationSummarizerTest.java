package io.yukti.evaluation.runner;

import io.yukti.engine.explainability.llm.LlmProviderId;
import io.yukti.evaluation.hallucination.HallucinationReport;
import io.yukti.evaluation.taxonomy.FailureCategory;
import io.yukti.evaluation.verifier.VerifierGate;
import io.yukti.evaluation.verifier.VerifierReport;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluationSummarizerTest {

    private PerInstanceEvaluation instance(NarratorVariant variant, LlmProviderId modelId,
                                           VerifierReport vr, boolean schemaFailed) {
        return new PerInstanceEvaluation(
            "p1", "CASHBACK", variant, modelId, "text",
            new HallucinationReport(0, 0, new EnumMap<>(FailureCategory.class), List.of()),
            List.of(),
            vr, schemaFailed, "heuristic"
        );
    }

    @Test
    void verifierPassRateAndGateCountsAggregated() {
        Map<VerifierGate, Integer> failures = new EnumMap<>(VerifierGate.class);
        failures.put(VerifierGate.EVIDENCE_EXISTENCE, 1);
        failures.put(VerifierGate.TYPE_RULES, 2);
        VerifierReport failing = new VerifierReport(false, 5, 3, failures, List.of("e1", "e2", "e3"));
        VerifierReport passing = VerifierReport.allPass(5);

        List<PerInstanceEvaluation> in = List.of(
            instance(NarratorVariant.GROUNDED, LlmProviderId.OPENAI_GPT4O_MINI, passing, false),
            instance(NarratorVariant.GROUNDED, LlmProviderId.OPENAI_GPT4O_MINI, passing, false),
            instance(NarratorVariant.GROUNDED, LlmProviderId.OPENAI_GPT4O_MINI, failing, true)
        );
        var rows = new EvaluationSummarizer().summarize(in);
        assertEquals(1, rows.size());
        var row = rows.get(0);
        assertEquals(3, row.instanceCount());
        assertEquals(2.0 / 3.0, row.verifierPassRate(), 1e-9);
        assertEquals(1, row.schemaFailedCount());
        assertEquals(1, (int) row.failuresByGate().get("EVIDENCE_EXISTENCE"));
        assertEquals(2, (int) row.failuresByGate().get("TYPE_RULES"));
    }

    @Test
    void shippedClaimsAndSurvivalRateAggregateCorrectly() {
        // Three instances, 10 claims each. One passes all 10. One has 4 failing.
        // One has 7 failing. Total generated = 30, total shipped = 10 + 6 + 3 = 19.
        // Survival rate = 19/30 = 63.3%. Mean shipped per instance = 19/3 ≈ 6.33.
        var pass = VerifierReport.allPass(10);
        var someFail = new VerifierReport(false, 10, 4, java.util.Map.of(), List.of("e", "e", "e", "e"));
        var mostFail = new VerifierReport(false, 10, 7, java.util.Map.of(), List.of("e", "e", "e", "e", "e", "e", "e"));
        var instances = List.of(
            instance(NarratorVariant.GROUNDED, LlmProviderId.OPENAI_GPT4O_MINI, pass, false),
            instance(NarratorVariant.GROUNDED, LlmProviderId.OPENAI_GPT4O_MINI, someFail, false),
            instance(NarratorVariant.GROUNDED, LlmProviderId.OPENAI_GPT4O_MINI, mostFail, false)
        );
        var row = new EvaluationSummarizer().summarize(instances).get(0);
        assertEquals(30, row.totalGeneratedClaims());
        assertEquals(19, row.totalShippedClaims());
        assertEquals(19.0 / 30.0, row.claimSurvivalRate(), 1e-9);
        assertEquals(19.0 / 3.0, row.meanShippedClaimsPerInstance(), 1e-9);
    }

    @Test
    void schemaFailedRowsContributeZeroShippedAndZeroGenerated() {
        // Schema-failed instances should not poison the survival denominator with
        // zero-claim instances. They contribute 0/0 to permissive emission.
        var schemaFail = VerifierReport.allPass(0); // empty, with schemaFailed=true on instance
        var realPass = VerifierReport.allPass(5);
        var instances = List.of(
            instance(NarratorVariant.GROUNDED, LlmProviderId.OPENAI_GPT4O_MINI, schemaFail, true),
            instance(NarratorVariant.GROUNDED, LlmProviderId.OPENAI_GPT4O_MINI, realPass, false)
        );
        var row = new EvaluationSummarizer().summarize(instances).get(0);
        assertEquals(5, row.totalGeneratedClaims(), "schema-failed contributes 0 generated");
        assertEquals(5, row.totalShippedClaims(), "schema-failed contributes 0 shipped");
        assertEquals(1.0, row.claimSurvivalRate(), 1e-9,
            "survival = 5/5 = 100% of the parseable claims");
        assertEquals(2.5, row.meanShippedClaimsPerInstance(), 1e-9,
            "(0 + 5) / 2 instances = 2.5");
    }

    @Test
    void schemaFailedInstanceIsNotCountedAsVerifierPass() {
        // Regression for issue 1: PairedNarratorRunner.runVerifier returns
        // VerifierReport.allPass(0) when there are no parseable claims. Combined
        // with schemaFailed=true, that previously let unparseable output count
        // as a verifier pass. The aggregator must exclude such rows from the
        // passed count and treat them as fully failing.
        VerifierReport vacuousPass = VerifierReport.allPass(0);
        List<PerInstanceEvaluation> in = List.of(
            instance(NarratorVariant.GROUNDED, LlmProviderId.OPENAI_GPT4O_MINI, vacuousPass, true),
            instance(NarratorVariant.GROUNDED, LlmProviderId.OPENAI_GPT4O_MINI, vacuousPass, true),
            instance(NarratorVariant.GROUNDED, LlmProviderId.OPENAI_GPT4O_MINI, VerifierReport.allPass(5), false)
        );
        var row = new EvaluationSummarizer().summarize(in).get(0);
        assertEquals(3, row.instanceCount());
        assertEquals(2, row.schemaFailedCount());
        assertEquals(1.0 / 3.0, row.verifierPassRate(), 1e-9,
            "only the parseable + passing instance counts as a pass");
        assertEquals(2.0 / 3.0, row.meanVerifierFailureRate(), 1e-9,
            "schema-failed rows contribute 1.0 to the mean failure rate");
    }

    @Test
    void legacyInstancesWithoutVerifierReportDefaultToPass() {
        // PerInstanceEvaluation legacy constructor defaults verifierReport to allPass(0)
        // and schemaFailed to false. The aggregator should treat that as passing.
        var legacy = new PerInstanceEvaluation(
            "p1", "CASHBACK", NarratorVariant.UNGROUNDED, LlmProviderId.OPENAI_GPT4O_MINI, "x",
            new HallucinationReport(0, 0, new EnumMap<>(FailureCategory.class), List.of()),
            List.of()
        );
        var rows = new EvaluationSummarizer().summarize(List.of(legacy));
        assertEquals(1.0, rows.get(0).verifierPassRate(), 1e-9);
        assertEquals(0, rows.get(0).schemaFailedCount());
        assertTrue(rows.get(0).failuresByGate().isEmpty());
    }
}
