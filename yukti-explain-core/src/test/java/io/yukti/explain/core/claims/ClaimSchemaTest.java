package io.yukti.explain.core.claims;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClaimSchemaTest {

    @Test
    void parseClaimsJson_validArray_returnsClaims() {
        String json = """
            [
              {"claimId": "c1", "claimType": "COMPARISON", "text": "amex-bcp wins GROCERIES", "citedEvidenceIds": ["e1"], "citedEntities": ["amex-bcp", "GROCERIES"], "citedNumbers": ["60.00"]},
              {"claimId": "c2", "claimType": "THRESHOLD", "text": "Net value $405", "citedEvidenceIds": [], "citedEntities": [], "citedNumbers": ["405"]}
            ]
            """;
        List<Claim> claims = ClaimSchema.parseClaimsJson(json);
        assertEquals(2, claims.size());
        assertEquals("c1", claims.get(0).claimId());
        assertEquals(ClaimType.COMPARISON, claims.get(0).claimType());
        assertEquals(List.of("e1"), claims.get(0).citedEvidenceIds());
        assertEquals("c2", claims.get(1).claimId());
        assertEquals(ClaimType.THRESHOLD, claims.get(1).claimType());
    }

    @Test
    void parseClaimsJson_invalidJson_throws() {
        assertThrows(IllegalArgumentException.class, () -> ClaimSchema.parseClaimsJson("not json"));
        assertThrows(IllegalArgumentException.class, () -> ClaimSchema.parseClaimsJson("[{invalid}]"));
    }

    @Test
    void parseClaimsJson_blank_returnsEmpty() {
        assertTrue(ClaimSchema.parseClaimsJson("").isEmpty());
        assertTrue(ClaimSchema.parseClaimsJson("   ").isEmpty());
    }
}
