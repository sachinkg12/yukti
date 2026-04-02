package io.yukti.bench;

import io.yukti.core.domain.Category;
import io.yukti.core.domain.EvidenceBlock;
import io.yukti.core.domain.OptimizationResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MilpEvidenceAdapterTest {

    @Test
    void fromMilpRun_mapsPortfolioAllocationBreakdownAndEvidence() {
        MilpRunWithEvidence run = new MilpRunWithEvidence(
            "light",
            "CASHBACK",
            List.of("amex-bcp", "citi-double-cash"),
            Map.of("GROCERIES", "amex-bcp", "OTHER", "citi-double-cash"),
            new MilpRunWithEvidence.BreakdownDto(450.0, 50.0, 95.0, 405.0),
            List.of(
                new MilpRunWithEvidence.EvidenceBlockDto("RESULT_BREAKDOWN", "", "", "Earn $450, net $405."),
                new MilpRunWithEvidence.EvidenceBlockDto("WINNER_BY_CATEGORY", "amex-bcp", "GROCERIES", "amex-bcp wins GROCERIES.")
            )
        );

        OptimizationResult result = MilpEvidenceAdapter.fromMilpRun(run);

        assertEquals(List.of("amex-bcp", "citi-double-cash"), result.getPortfolioIds());
        assertEquals("amex-bcp", result.getAllocation().get(Category.GROCERIES));
        assertEquals("citi-double-cash", result.getAllocation().get(Category.OTHER));
        assertEquals(405.0, result.getBreakdown().getNet().getAmount().doubleValue(), 1e-6);
        assertEquals(2, result.getEvidenceBlocks().size());
        EvidenceBlock first = result.getEvidenceBlocks().get(0);
        assertEquals("RESULT_BREAKDOWN", first.getType());
        assertEquals("Earn $450, net $405.", first.getContent());
        EvidenceBlock second = result.getEvidenceBlocks().get(1);
        assertEquals("WINNER_BY_CATEGORY", second.getType());
        assertEquals("amex-bcp", second.getCardId());
        assertEquals("GROCERIES", second.getCategory());
    }
}
