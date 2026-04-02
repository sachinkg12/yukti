package io.yukti.explain.core.claims;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Claim schema v1 validation: valid claim passes, extra fields rejected.
 */
class JsonSchemaValidatorTest {

    private static final String VALID_CLAIM = """
        {
          "claimId": "abc123",
          "claimType": "COMPARISON",
          "normalizedFields": {"winnerCardId": "amex-bcp", "category": "GROCERIES"},
          "citedEvidenceIds": ["e1", "e2"],
          "citedEntities": ["amex-bcp", "GROCERIES"],
          "citedNumbers": ["60.00"]
        }
        """;

    private final JsonSchemaValidator validator = new JsonSchemaValidator();

    @Test
    void validClaim_passesValidation() {
        List<String> errors = validator.validate(VALID_CLAIM);
        assertTrue(errors.isEmpty(), "Valid claim should have no errors: " + errors);
        assertTrue(validator.isValid(VALID_CLAIM));
    }

    @Test
    void validClaim_withOptionalRenderTemplateId_passes() {
        String withOptional = """
            {
              "claimId": "x",
              "claimType": "THRESHOLD",
              "normalizedFields": {},
              "citedEvidenceIds": [],
              "citedEntities": [],
              "citedNumbers": [],
              "renderTemplateId": "threshold-v1"
            }
            """;
        List<String> errors = validator.validate(withOptional);
        assertTrue(errors.isEmpty(), "Claim with optional renderTemplateId should pass: " + errors);
    }

    @Test
    void claimWithExtraTopLevelField_rejected() {
        String withExtra = """
            {
              "claimId": "x",
              "claimType": "COMPARISON",
              "normalizedFields": {},
              "citedEvidenceIds": [],
              "citedEntities": [],
              "citedNumbers": [],
              "extraField": "not allowed"
            }
            """;
        List<String> errors = validator.validate(withExtra);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("extraField") && e.contains("Disallowed")),
            "Should report disallowed property: " + errors);
        assertFalse(validator.isValid(withExtra));
    }

    @Test
    void claimMissingRequiredField_rejected() {
        String missingClaimType = """
            {
              "claimId": "x",
              "normalizedFields": {},
              "citedEvidenceIds": [],
              "citedEntities": [],
              "citedNumbers": []
            }
            """;
        List<String> errors = validator.validate(missingClaimType);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("claimType") && e.contains("Missing")),
            "Should report missing required field: " + errors);
    }

    @Test
    void claimInvalidClaimType_rejected() {
        String badType = """
            {
              "claimId": "x",
              "claimType": "INVALID_TYPE",
              "normalizedFields": {},
              "citedEvidenceIds": [],
              "citedEntities": [],
              "citedNumbers": []
            }
            """;
        List<String> errors = validator.validate(badType);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("claimType")),
            "Should report invalid enum: " + errors);
    }

    @Test
    void notAnObject_rejected() {
        List<String> errors = validator.validate("[1,2,3]");
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("object")),
            "Should require JSON object: " + errors);
    }

    @Test
    void claimTypeRules_requiredEvidenceTypes() {
        assertEquals(Set.of(ClaimTypeRules.WINNER_BY_CATEGORY), ClaimTypeRules.requiredEvidenceTypes(ClaimType.COMPARISON));
        assertEquals(Set.of(ClaimTypeRules.CAP_HIT), ClaimTypeRules.requiredEvidenceTypes(ClaimType.CAP_SWITCH));
        assertEquals(Set.of(ClaimTypeRules.FEE_BREAK_EVEN), ClaimTypeRules.requiredEvidenceTypes(ClaimType.FEE_JUSTIFICATION));
        assertEquals(Set.of(ClaimTypeRules.ASSUMPTION), ClaimTypeRules.requiredEvidenceTypes(ClaimType.ASSUMPTION));
        assertTrue(ClaimTypeRules.requiredEvidenceTypes(ClaimType.THRESHOLD).isEmpty());
        assertTrue(ClaimTypeRules.requiredEvidenceTypes(ClaimType.ALLOCATION).isEmpty());
    }
}
