package io.yukti.engine.explainability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.yukti.core.domain.*;
import io.yukti.core.explainability.StructuredExplanation;
import io.yukti.explain.core.claims.NormalizedClaim;
import io.yukti.explain.core.evidence.graph.EvidenceGraph;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Snapshot tests for deterministic ClaimGenerator and ClaimRenderer.
 * 1) Claims JSON for fixed fixtures; 2) Rendered narrative for deterministic path.
 */
class ClaimGeneratorTest {

    private static final ObjectMapper OM = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    @Test
    void generate_deterministicSameInputSameClaims() {
        OptimizationResult result = fixture1();
        StructuredExplanation se = new StructuredExplanationBuilder().build(result, "v1", io.yukti.core.domain.GoalType.CASHBACK, null);
        EvidenceGraph eg = new EvidenceGraphBuilder().build(result);
        List<NormalizedClaim> a = ClaimGenerator.generate(se, eg);
        List<NormalizedClaim> b = ClaimGenerator.generate(se, eg);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).claimId(), b.get(i).claimId());
            assertEquals(a.get(i).claimType(), b.get(i).claimType());
        }
    }

    @Test
    void generate_producesComparisonAllocationCapSwitchFeeAssumption() {
        OptimizationResult result = fixture1();
        StructuredExplanation se = new StructuredExplanationBuilder().build(result, "v1", io.yukti.core.domain.GoalType.CASHBACK, null);
        EvidenceGraph eg = new EvidenceGraphBuilder().build(result);
        List<NormalizedClaim> claims = ClaimGenerator.generate(se, eg);
        Set<io.yukti.explain.core.claims.ClaimType> types = new HashSet<>();
        for (NormalizedClaim c : claims) types.add(c.claimType());
        assertTrue(types.contains(io.yukti.explain.core.claims.ClaimType.COMPARISON));
        assertTrue(types.contains(io.yukti.explain.core.claims.ClaimType.ALLOCATION));
        assertTrue(types.contains(io.yukti.explain.core.claims.ClaimType.CAP_SWITCH));
        assertTrue(types.contains(io.yukti.explain.core.claims.ClaimType.FEE_JUSTIFICATION));
        assertTrue(types.contains(io.yukti.explain.core.claims.ClaimType.ASSUMPTION));
    }

    @Test
    void snapshot_claimsJson_fixture1() throws Exception {
        OptimizationResult result = fixture1();
        StructuredExplanation se = new StructuredExplanationBuilder().build(result, "v1", io.yukti.core.domain.GoalType.CASHBACK, null);
        EvidenceGraph eg = new EvidenceGraphBuilder().build(result);
        List<NormalizedClaim> claims = ClaimGenerator.generate(se, eg);
        String json = claimsToStableJson(claims);
        String expected = loadSnapshot("claim_generator_claims_fixture1.json");
        assertEquals(expected.trim(), json.trim(), "Claims JSON snapshot mismatch");
    }

    @Test
    void snapshot_narrative_fixture1() throws Exception {
        OptimizationResult result = fixture1();
        StructuredExplanation se = new StructuredExplanationBuilder().build(result, "v1", io.yukti.core.domain.GoalType.CASHBACK, null);
        EvidenceGraph eg = new EvidenceGraphBuilder().build(result);
        List<NormalizedClaim> claims = ClaimGenerator.generate(se, eg);
        String narrative = ClaimRenderer.render(claims);
        String expected = loadSnapshot("claim_renderer_narrative_fixture1.txt");
        assertEquals(expected.trim(), narrative.trim(), "Narrative snapshot mismatch");
    }

    @Test
    void snapshot_claimsJson_fixture2() throws Exception {
        OptimizationResult result = fixture2();
        StructuredExplanation se = new StructuredExplanationBuilder().build(result, "v1", io.yukti.core.domain.GoalType.FLEX_POINTS, "USD");
        EvidenceGraph eg = new EvidenceGraphBuilder().build(result);
        List<NormalizedClaim> claims = ClaimGenerator.generate(se, eg);
        String json = claimsToStableJson(claims);
        String expected = loadSnapshot("claim_generator_claims_fixture2.json");
        assertEquals(expected.trim(), json.trim(), "Claims JSON snapshot mismatch");
    }

    @Test
    void snapshot_narrative_fixture2() throws Exception {
        OptimizationResult result = fixture2();
        StructuredExplanation se = new StructuredExplanationBuilder().build(result, "v1", io.yukti.core.domain.GoalType.FLEX_POINTS, "USD");
        EvidenceGraph eg = new EvidenceGraphBuilder().build(result);
        List<NormalizedClaim> claims = ClaimGenerator.generate(se, eg);
        String narrative = ClaimRenderer.render(claims);
        String expected = loadSnapshot("claim_renderer_narrative_fixture2.txt");
        assertEquals(expected.trim(), narrative.trim(), "Narrative snapshot mismatch");
    }

    @Test
    void snapshot_claimsJson_fixture3() throws Exception {
        OptimizationResult result = fixture3();
        StructuredExplanation se = new StructuredExplanationBuilder().build(result, "v1", io.yukti.core.domain.GoalType.PROGRAM_POINTS, null);
        EvidenceGraph eg = new EvidenceGraphBuilder().build(result);
        List<NormalizedClaim> claims = ClaimGenerator.generate(se, eg);
        String json = claimsToStableJson(claims);
        String expected = loadSnapshot("claim_generator_claims_fixture3.json");
        assertEquals(expected.trim(), json.trim(), "Claims JSON snapshot mismatch");
    }

    @Test
    void snapshot_narrative_fixture3() throws Exception {
        OptimizationResult result = fixture3();
        StructuredExplanation se = new StructuredExplanationBuilder().build(result, "v1", io.yukti.core.domain.GoalType.PROGRAM_POINTS, null);
        EvidenceGraph eg = new EvidenceGraphBuilder().build(result);
        List<NormalizedClaim> claims = ClaimGenerator.generate(se, eg);
        String narrative = ClaimRenderer.render(claims);
        String expected = loadSnapshot("claim_renderer_narrative_fixture3.txt");
        assertEquals(expected.trim(), narrative.trim(), "Narrative snapshot mismatch");
    }

    private static String claimsToStableJson(List<NormalizedClaim> claims) throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        for (NormalizedClaim c : claims) {
            Map<String, Object> m = new TreeMap<>();
            m.put("claimId", c.claimId());
            m.put("claimType", c.claimType().name());
            m.put("normalizedFields", new TreeMap<>(c.normalizedFields()));
            m.put("citedEvidenceIds", new ArrayList<>(c.citedEvidenceIds()));
            m.put("citedEntities", new ArrayList<>(c.citedEntities()));
            m.put("citedNumbers", new ArrayList<>(c.citedNumbers()));
            if (c.renderTemplateId() != null) m.put("renderTemplateId", c.renderTemplateId());
            list.add(m);
        }
        return OM.writeValueAsString(list);
    }

    private static String loadSnapshot(String name) throws Exception {
        try (var in = ClaimGeneratorTest.class.getClassLoader().getResourceAsStream("snapshots/claim_deterministic/" + name)) {
            if (in != null) return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        // Fallback: read from src when not on classpath (works whether cwd is repo root or yukti-engine)
        String subpath = "snapshots/claim_deterministic/" + name;
        for (String base : new String[] { "src/test/resources", "yukti-engine/src/test/resources" }) {
            java.nio.file.Path p = java.nio.file.Path.of(base, subpath).normalize();
            if (java.nio.file.Files.isRegularFile(p)) return java.nio.file.Files.readString(p);
        }
        throw new IllegalStateException(
            "Snapshot not found: snapshots/claim_deterministic/" + name
                + ". Run with -DwriteSnapshots=true to generate into build/snapshots/claim_deterministic/ and copy to src/test/resources/snapshots/claim_deterministic/");
    }

    /** Run with -DwriteSnapshots=true to regenerate snapshot files into build/snapshots/claim_deterministic/ */
    @Test
    void writeSnapshotsWhenRequested() throws Exception {
        if (!"true".equals(System.getProperty("writeSnapshots"))) return;
        java.nio.file.Path dir = java.nio.file.Path.of("build/snapshots/claim_deterministic");
        java.nio.file.Files.createDirectories(dir);
        for (int i = 1; i <= 3; i++) {
            OptimizationResult result = i == 1 ? fixture1() : (i == 2 ? fixture2() : fixture3());
            var goal = i == 1 ? io.yukti.core.domain.GoalType.CASHBACK : (i == 2 ? io.yukti.core.domain.GoalType.FLEX_POINTS : io.yukti.core.domain.GoalType.PROGRAM_POINTS);
            String primary = i == 2 ? "USD" : null;
            StructuredExplanation se = new StructuredExplanationBuilder().build(result, "v1", goal, primary);
            EvidenceGraph eg = new EvidenceGraphBuilder().build(result);
            List<NormalizedClaim> claims = ClaimGenerator.generate(se, eg);
            java.nio.file.Files.writeString(dir.resolve("claim_generator_claims_fixture" + i + ".json"), claimsToStableJson(claims));
            java.nio.file.Files.writeString(dir.resolve("claim_renderer_narrative_fixture" + i + ".txt"), ClaimRenderer.render(claims));
        }
        fail("Snapshots written to build/snapshots/claim_deterministic/ - copy to src/test/resources/snapshots/claim_deterministic/ and re-run");
    }

    private static OptimizationResult fixture1() {
        Map<Category, String> allocation = new LinkedHashMap<>();
        allocation.put(Category.GROCERIES, "amex-bcp");
        allocation.put(Category.DINING, "amex-bcp");
        allocation.put(Category.OTHER, "citi-double-cash");
        ObjectiveBreakdown breakdown = new ObjectiveBreakdown(
            Money.usd("450"),
            Money.usd("50"),
            Money.usd("95")
        );
        List<EvidenceBlock> blocks = List.of(
            new EvidenceBlock("WINNER_BY_CATEGORY", "amex-bcp", "GROCERIES", "amex-bcp wins GROCERIES over amex-bce: delta $60.00"),
            new EvidenceBlock("WINNER_BY_CATEGORY", "amex-bcp", "DINING", "amex-bcp wins DINING: delta $30.00"),
            new EvidenceBlock("WINNER_BY_CATEGORY", "citi-double-cash", "OTHER", "citi-double-cash wins OTHER: delta $20.00"),
            new EvidenceBlock("CAP_HIT", "amex-bcp", "GROCERIES", "amex-bcp: cap $6000 on GROCERIES, applied $6000, remaining $2000, fallback amex-bce"),
            new EvidenceBlock("FEE_BREAK_EVEN", "amex-bcp", "", "amex-bcp: fee $95, credits $0, incremental earn $250, net delta $155"),
            new EvidenceBlock("ASSUMPTION", "valuation", "valuation", "Goal: CASHBACK, cpp: {USD=1.0}")
        );
        return new OptimizationResult(
            List.of("amex-bcp", "citi-double-cash"),
            allocation,
            breakdown,
            blocks,
            "Narrative.",
            List.of()
        );
    }

    private static OptimizationResult fixture2() {
        Map<Category, String> allocation = new LinkedHashMap<>();
        allocation.put(Category.GROCERIES, "citi-double-cash");
        allocation.put(Category.DINING, "amex-gold");
        ObjectiveBreakdown breakdown = new ObjectiveBreakdown(
            Money.usd("200"),
            Money.usd("0"),
            Money.usd("250")
        );
        List<EvidenceBlock> blocks = List.of(
            new EvidenceBlock("WINNER_BY_CATEGORY", "citi-double-cash", "GROCERIES", "citi-double-cash wins GROCERIES: delta $100.00"),
            new EvidenceBlock("WINNER_BY_CATEGORY", "amex-gold", "DINING", "amex-gold wins DINING over amex-bcp: delta $45.00"),
            new EvidenceBlock("FEE_BREAK_EVEN", "amex-gold", "", "amex-gold: fee $250, credits $120, incremental earn $380, net delta $250"),
            new EvidenceBlock("ASSUMPTION", "valuation", "valuation", "Goal: FLEX_POINTS, primary: USD, cpp: {USD=1.0}")
        );
        return new OptimizationResult(
            List.of("citi-double-cash", "amex-gold"),
            allocation,
            breakdown,
            blocks,
            "Narrative.",
            List.of()
        );
    }

    private static OptimizationResult fixture3() {
        Map<Category, String> allocation = new LinkedHashMap<>();
        allocation.put(Category.GROCERIES, "amex-bcp");
        allocation.put(Category.OTHER, "amex-bcp");
        ObjectiveBreakdown breakdown = new ObjectiveBreakdown(
            Money.usd("350"),
            Money.usd("0"),
            Money.usd("95")
        );
        List<EvidenceBlock> blocks = List.of(
            new EvidenceBlock("WINNER_BY_CATEGORY", "amex-bcp", "GROCERIES", "amex-bcp wins GROCERIES: delta $180.00"),
            new EvidenceBlock("WINNER_BY_CATEGORY", "amex-bcp", "OTHER", "amex-bcp wins OTHER: delta $50.00"),
            new EvidenceBlock("FEE_BREAK_EVEN", "amex-bcp", "", "amex-bcp: fee $95, credits $0, incremental earn $250, net delta $155"),
            new EvidenceBlock("ASSUMPTION", "valuation", "valuation", "Goal: PROGRAM_POINTS, cpp: {AA_MILES=1.4}")
        );
        return new OptimizationResult(
            List.of("amex-bcp"),
            allocation,
            breakdown,
            blocks,
            "Narrative.",
            List.of()
        );
    }
}
