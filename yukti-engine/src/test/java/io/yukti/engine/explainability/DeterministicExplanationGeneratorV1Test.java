package io.yukti.engine.explainability;

import io.yukti.core.domain.Category;
import io.yukti.core.domain.GoalType;
import io.yukti.core.domain.Period;
import io.yukti.core.explainability.ExplanationGenerator;
import io.yukti.core.explainability.NarrativeExplanation;
import io.yukti.core.explainability.StructuredExplanation;
import io.yukti.core.explainability.evidence.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DeterministicExplanationGeneratorV1Test {

    private final ExplanationGenerator generator = new DeterministicExplanationGeneratorV1();

    private StructuredExplanation fixture() {
        Map<String, BigDecimal> cpp = new LinkedHashMap<>();
        cpp.put("AA_MILES", new BigDecimal("1.4"));
        cpp.put("USD", new BigDecimal("1.0"));
        Map<String, BigDecimal> util = new LinkedHashMap<>();
        util.put("amex-gold", new BigDecimal("0.7"));
        AssumptionEvidence assumption = new AssumptionEvidence(
            cpp,
            Map.of(),
            util,
            GoalType.PROGRAM_POINTS,
            "AA_MILES"
        );
        WinnerByCategoryEvidence winner = new WinnerByCategoryEvidence(
            Category.GROCERIES,
            "amex-bcp",
            "amex-bce",
            new BigDecimal("180.00"),
            new BigDecimal("120.00"),
            new BigDecimal("60.00")
        );
        FeeBreakEvenEvidence fee = new FeeBreakEvenEvidence(
            "amex-bcp",
            new BigDecimal("95.00"),
            new BigDecimal("0"),
            new BigDecimal("250.00"),
            new BigDecimal("155.00")
        );
        CapHitEvidence capHit = new CapHitEvidence(
            "amex-bcp",
            Category.GROCERIES,
            new BigDecimal("6000"),
            Period.ANNUAL,
            new BigDecimal("6000"),
            new BigDecimal("2000"),
            "amex-bce"
        );
        PortfolioStopEvidence stop = new PortfolioStopEvidence("MAX_CARDS_REACHED", "Reached maxCards=3");

        StructuredExplanation.Breakdown breakdown = new StructuredExplanation.Breakdown(
            new BigDecimal("450.00"),
            new BigDecimal("50.00"),
            new BigDecimal("95.00"),
            new BigDecimal("405.00")
        );

        return new StructuredExplanation(
            "1.0",
            GoalType.CASHBACK,
            null,
            Map.of(Category.GROCERIES, "amex-bcp", Category.DINING, "amex-bcp", Category.OTHER, "citi-double-cash"),
            List.of("amex-bcp", "citi-double-cash"),
            breakdown,
            List.of(assumption, winner, fee, capHit, stop),
            "",
            List.of()
        );
    }

    @Test
    void generate_deterministic() {
        StructuredExplanation s = fixture();
        NarrativeExplanation r1 = generator.generate(s);
        NarrativeExplanation r2 = generator.generate(s);
        assertEquals(r1.fullText(), r2.fullText());
    }

    @Test
    void generate_producesStableClaimIds() {
        StructuredExplanation s = fixture();
        NarrativeExplanation r1 = generator.generate(s);
        NarrativeExplanation r2 = generator.generate(s);
        assertEquals(r1.claims().size(), r2.claims().size());
        for (int i = 0; i < r1.claims().size(); i++) {
            assertEquals(r1.claims().get(i).claimId(), r2.claims().get(i).claimId(),
                "Claim id at index " + i + " must be stable across runs");
        }
    }

    @Test
    void generate_orderingDeterminism() {
        StructuredExplanation s = fixture();
        List<EvidenceBlock> shuffled = List.of(
            (EvidenceBlock) s.evidenceBlocks().get(4),
            (EvidenceBlock) s.evidenceBlocks().get(2),
            (EvidenceBlock) s.evidenceBlocks().get(0),
            (EvidenceBlock) s.evidenceBlocks().get(3),
            (EvidenceBlock) s.evidenceBlocks().get(1)
        );
        StructuredExplanation shuffledStructured = new StructuredExplanation(
            s.catalogVersion(),
            s.goalType(),
            s.primaryCurrencyOrNull(),
            s.allocationByCategory(),
            s.portfolioCardIds(),
            s.breakdown(),
            shuffled,
            s.evidenceGraphDigest(),
            s.evidenceIds()
        );
        NarrativeExplanation r1 = generator.generate(s);
        NarrativeExplanation r2 = generator.generate(shuffledStructured);
        assertEquals(r1.fullText(), r2.fullText(), "Output must be identical regardless of evidence order");
    }

    @Test
    void generate_snapshotMatch() throws IOException {
        StructuredExplanation s = fixture();
        NarrativeExplanation r = generator.generate(s);
        String actual = r.fullText().trim().replace("\r\n", "\n").replace("\r", "\n");
        String expected = loadSnapshot("explain_v1_case1.txt").trim().replace("\r\n", "\n").replace("\r", "\n");
        assertEquals(expected, actual, () -> "Snapshot mismatch.\n--- Expected ---\n" + expected + "\n--- Actual ---\n" + actual);
    }

    private String loadSnapshot(String name) throws IOException {
        try (var in = getClass().getClassLoader().getResourceAsStream("snapshots/" + name)) {
            if (in == null) throw new IllegalStateException("Snapshot not found: snapshots/" + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
