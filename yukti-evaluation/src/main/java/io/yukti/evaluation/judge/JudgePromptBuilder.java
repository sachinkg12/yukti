package io.yukti.evaluation.judge;

/**
 * Builds the prompt for an LLM judge. Kept separate so the prompt can be evolved
 * without touching the judge implementation.
 */
public final class JudgePromptBuilder {

    private JudgePromptBuilder() {}

    public static String build(String contextSummary, String explanationA, String explanationB) {
        return """
            You are an impartial judge comparing two explanations of a credit card portfolio
            recommendation. Choose the explanation that is most useful to a user reading it.
            Consider:
              - Faithfulness to the context (cites only entities and numbers consistent with the optimization)
              - Clarity (clean, unambiguous prose)
              - Helpfulness (says something concrete about WHY the cards were chosen)

            Do not let length or position influence you. Either explanation may be in slot A or slot B.

            Output ONLY a single JSON object on one line. No commentary. No markdown.
            Schema: {"winner": "A" | "B" | "TIE", "rationale": "<one short sentence>", "confidence": <float 0.0 to 1.0>}

            Context (the optimizer's actual decision):
            """ + contextSummary + """

            Explanation A:
            """ + explanationA + """

            Explanation B:
            """ + explanationB + """

            Output the JSON object only.
            """;
    }
}
