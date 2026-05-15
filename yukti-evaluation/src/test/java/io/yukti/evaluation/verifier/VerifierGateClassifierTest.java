package io.yukti.evaluation.verifier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VerifierGateClassifierTest {

    @Test
    void evidenceExistenceErrorMapsToGate1() {
        assertEquals(VerifierGate.EVIDENCE_EXISTENCE,
            VerifierGateClassifier.classify("citedEvidenceId not in graph: abc"));
    }

    @Test
    void entityAllowlistErrorMapsToGate2() {
        assertEquals(VerifierGate.ENTITY_ALLOWLIST,
            VerifierGateClassifier.classify("citedEntity not allowed: SAPPHIRE_FOO"));
    }

    @Test
    void numberBindingErrorMapsToGate3() {
        assertEquals(VerifierGate.NUMBER_BINDING,
            VerifierGateClassifier.classify("citedNumber not allowed: 1234.56"));
    }

    @Test
    void typeRulesErrorMapsToGate4() {
        assertEquals(VerifierGate.TYPE_RULES,
            VerifierGateClassifier.classify("COMPARISON must cite [WINNER_BY_CATEGORY] evidence"));
        assertEquals(VerifierGate.TYPE_RULES,
            VerifierGateClassifier.classify("CAP_SWITCH must cite CAP_HIT evidence"));
    }

    @Test
    void unknownErrorMapsToOther() {
        assertEquals(VerifierGate.OTHER,
            VerifierGateClassifier.classify("some new error format"));
        assertEquals(VerifierGate.OTHER,
            VerifierGateClassifier.classify(null));
    }

    @Test
    void looseMentionsOfCiteAndEvidenceAreNotBucketedAsTypeRules() {
        // A future error like "could not cite this evidence" must not get
        // mis-bucketed into TYPE_RULES just because it contains those words.
        assertEquals(VerifierGate.OTHER,
            VerifierGateClassifier.classify("could not cite this evidence"));
        // Lower-case claim type or no anchor → OTHER.
        assertEquals(VerifierGate.OTHER,
            VerifierGateClassifier.classify("comparison must cite winner evidence"));
    }
}
