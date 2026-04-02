package io.yukti.explain.core.evidence.graph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EvidenceIdHelperTest {

    @Test
    void compute_sameInputs_producesSameId() {
        String id1 = EvidenceIdHelper.compute("WINNER_BY_CATEGORY", "amex-bcp", "GROCERIES", "amex-bcp wins GROCERIES: delta $60.00");
        String id2 = EvidenceIdHelper.compute("WINNER_BY_CATEGORY", "amex-bcp", "GROCERIES", "amex-bcp wins GROCERIES: delta $60.00");
        assertEquals(id1, id2);
    }

    @Test
    void compute_differentContent_sameStructuredFields_producesSameId() {
        // Content is display-only; EvidenceId is over type, cardId, category only (paper-aligned).
        String id1 = EvidenceIdHelper.compute("CAP_HIT", "amex-bcp", "GROCERIES", "cap $6000, applied $6000");
        String id2 = EvidenceIdHelper.compute("CAP_HIT", "amex-bcp", "GROCERIES", "cap $6000, applied $5000");
        assertEquals(id1, id2);
    }

    @Test
    void compute_differentType_producesDifferentId() {
        String id1 = EvidenceIdHelper.compute("WINNER_BY_CATEGORY", "c1", "cat", "content");
        String id2 = EvidenceIdHelper.compute("CAP_HIT", "c1", "cat", "content");
        assertNotEquals(id1, id2);
    }

    @Test
    void compute_returns64HexChars() {
        String id = EvidenceIdHelper.compute("TYPE", "card", "cat", "content");
        assertEquals(64, id.length());
        assertTrue(id.matches("[0-9a-f]{64}"));
    }
}
