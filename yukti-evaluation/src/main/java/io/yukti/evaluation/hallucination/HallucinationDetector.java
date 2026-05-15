package io.yukti.evaluation.hallucination;

import io.yukti.core.explainability.StructuredExplanation;
import io.yukti.explain.core.claims.Claim;

import java.util.List;

/**
 * Detects hallucinations in a set of claims relative to a structured explanation
 * (which carries the evidence graph and allowed lists).
 *
 * <p>Open Closed: add new detection strategies (entity allowlist, number binding,
 * causal misattribution) by adding implementations of this interface.
 */
public interface HallucinationDetector {
    HallucinationReport detect(StructuredExplanation evidence, List<Claim> claims);
}
