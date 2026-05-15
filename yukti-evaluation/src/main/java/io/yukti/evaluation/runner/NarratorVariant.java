package io.yukti.evaluation.runner;

/**
 * Narrator variant used in a paired evaluation run. The two variants share the
 * same underlying optimization result.
 */
public enum NarratorVariant {

    /** LlmNarrator with full evidence graph and four gate verifier on output. */
    GROUNDED,

    /** UngroundedLlmNarrator with no evidence graph and no allowlist. */
    UNGROUNDED,

    /** NaiveNarrator template only. Useful as a reference floor. */
    DETERMINISTIC
}
