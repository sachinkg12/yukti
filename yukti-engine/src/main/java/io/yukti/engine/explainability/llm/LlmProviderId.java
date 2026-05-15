package io.yukti.engine.explainability.llm;

/**
 * Identifiers for LLM providers used in evaluation. Open Closed: add new providers
 * by adding an enum value and registering an implementation in {@link LlmProviderRegistry}.
 */
public enum LlmProviderId {
    OPENAI_GPT4O,
    OPENAI_GPT4O_MINI,
    ANTHROPIC_CLAUDE_SONNET,
    ANTHROPIC_CLAUDE_HAIKU,
    GOOGLE_GEMINI_FLASH,
    GOOGLE_GEMINI_PRO,
    TOGETHER_LLAMA_70B,
    TOGETHER_QWEN_72B,
    MOCK
}
