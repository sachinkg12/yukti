package io.yukti.evaluation.taxonomy;

import io.yukti.core.explainability.StructuredExplanation;
import io.yukti.explain.core.claims.Claim;

import java.util.List;

/**
 * Classifies a hallucinated claim into one of the {@link FailureCategory} values.
 *
 * <p>Open Closed: callers depend on the interface. Add new classifiers (rule based,
 * LLM judged, regex driven) without changing the call sites.
 */
public interface FailureCategoryClassifier {

    /**
     * Classify a single claim against the structured explanation. Returns a list
     * because one claim can carry multiple distinct hallucinations (for example a
     * fabricated card name AND a fabricated dollar amount).
     */
    List<FailureCategory> classify(Claim claim, StructuredExplanation evidence);
}
