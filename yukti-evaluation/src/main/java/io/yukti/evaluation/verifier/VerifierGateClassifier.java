package io.yukti.evaluation.verifier;

import java.util.regex.Pattern;

/**
 * Maps a raw {@code ClaimVerifier} error message to a {@link VerifierGate}.
 *
 * <p>The mapping is by prefix match on the strings that the production
 * {@code ClaimVerifier} emits. Keeping this in evaluation rather than in
 * yukti-explain-core avoids changing the production verifier API.
 *
 * <p>Gate 4 uses a stricter pattern (anchored ClaimType + "must cite" + "evidence")
 * rather than a loose "contains" check, so unrelated future error messages that
 * happen to mention those words are not mis-bucketed as TYPE_RULES.
 */
public final class VerifierGateClassifier {

    // Production gate 4 emits either
    //   "<ClaimType> must cite [<TYPE>, ...] evidence"   (requiredEvidenceTypes)
    //   "<ClaimType> must cite <TYPE> evidence"          (requiredEvidenceTypesAll)
    // ClaimType is an enum: only upper-case letters and underscores.
    private static final Pattern TYPE_RULES_PATTERN =
        Pattern.compile("^[A-Z_]+ must cite .+ evidence$");

    private VerifierGateClassifier() {}

    public static VerifierGate classify(String errorMessage) {
        if (errorMessage == null) return VerifierGate.OTHER;
        if (errorMessage.startsWith("citedEvidenceId not in graph")) {
            return VerifierGate.EVIDENCE_EXISTENCE;
        }
        if (errorMessage.startsWith("citedEntity not allowed")) {
            return VerifierGate.ENTITY_ALLOWLIST;
        }
        if (errorMessage.startsWith("citedNumber not allowed")) {
            return VerifierGate.NUMBER_BINDING;
        }
        if (TYPE_RULES_PATTERN.matcher(errorMessage).matches()) {
            return VerifierGate.TYPE_RULES;
        }
        return VerifierGate.OTHER;
    }
}
