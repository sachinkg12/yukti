package io.yukti.evaluation.runner;

import io.yukti.engine.explainability.llm.LlmProviderId;

import java.util.List;
import java.util.Objects;

/**
 * Configuration for one explanation evaluation sweep. Specifies which models to
 * use as narrators, which model to use as judge, and how many profiles to run.
 */
public record EvaluationConfig(
    List<LlmProviderId> narratorModels,
    LlmProviderId judgeModel,
    int profileCount,
    int judgeSampleCount,
    long seed,
    int groundedRepairAttempts
) {
    public EvaluationConfig {
        Objects.requireNonNull(narratorModels);
        Objects.requireNonNull(judgeModel);
        narratorModels = List.copyOf(narratorModels);
        if (profileCount <= 0) throw new IllegalArgumentException("profileCount must be > 0");
        if (judgeSampleCount < 0) throw new IllegalArgumentException("judgeSampleCount must be >= 0");
        if (groundedRepairAttempts < 0) {
            throw new IllegalArgumentException("groundedRepairAttempts must be >= 0");
        }
    }

    /**
     * Five argument constructor for callers and tests that predate the repair
     * loop. Defaults {@code groundedRepairAttempts} to 0 (no repair) to preserve
     * exact backward compatibility with prior eval runs.
     */
    public EvaluationConfig(
        List<LlmProviderId> narratorModels,
        LlmProviderId judgeModel,
        int profileCount,
        int judgeSampleCount,
        long seed
    ) {
        this(narratorModels, judgeModel, profileCount, judgeSampleCount, seed, 0);
    }

    /**
     * Tiny config for smoke tests. 3 profiles, 1 model, no judging. Used to verify the
     * pipeline end to end before paying for a real sweep.
     */
    public static EvaluationConfig smokeConfig() {
        return new EvaluationConfig(
            List.of(LlmProviderId.OPENAI_GPT4O_MINI),
            LlmProviderId.OPENAI_GPT4O_MINI,
            3,
            0,
            42L
        );
    }

    /** Cheap default for daily iteration. Uses the cost optimized variant of each family. */
    public static EvaluationConfig defaultConfig() {
        return new EvaluationConfig(
            List.of(
                LlmProviderId.OPENAI_GPT4O_MINI,
                LlmProviderId.ANTHROPIC_CLAUDE_HAIKU,
                LlmProviderId.GOOGLE_GEMINI_FLASH
            ),
            LlmProviderId.ANTHROPIC_CLAUDE_SONNET,
            20,
            50,
            42L
        );
    }

    /**
     * Large sweep config. Three narrators forming a small / open-weights mid /
     * frontier tier (gpt-4o-mini / Llama 3.3 70B / Sonnet 4.6), with the judge in
     * a fourth disjoint family (OpenAI GPT-4o) to avoid self-preference bias.
     *
     * <p>Defaults to {@code groundedRepairAttempts=1}. The {@code repair=0}
     * baseline run is launched via {@code -Dio.yukti.eval.repair=0}, which
     * {@link io.yukti.bench.explanation.ExplanationEvalRunner} layers on top of
     * this preset.
     */
    public static EvaluationConfig largeConfig() {
        return new EvaluationConfig(
            List.of(
                LlmProviderId.OPENAI_GPT4O_MINI,
                LlmProviderId.TOGETHER_LLAMA_70B,
                LlmProviderId.ANTHROPIC_CLAUDE_SONNET
            ),
            LlmProviderId.OPENAI_GPT4O,
            50,
            150,
            42L,
            1
        );
    }
}
