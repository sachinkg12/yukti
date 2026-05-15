package io.yukti.evaluation.fluency;

/**
 * Computes a fluency style metric over an explanation text.
 *
 * <p>Open Closed: add new metrics (perplexity, type token ratio, BLEU against a
 * reference) by adding implementations of this interface and registering them in
 * {@link FluencyMetricRegistry}.
 */
public interface FluencyMetric {

    /** Stable identifier for serialization (CSV, JSON output). */
    String id();

    /** Compute the metric over a single explanation text. */
    FluencyScore score(String text);
}
