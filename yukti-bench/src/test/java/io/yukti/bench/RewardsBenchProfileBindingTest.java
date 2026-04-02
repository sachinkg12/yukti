package io.yukti.bench;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RewardsBench v1: profile count 50, ids exact match to profile_ids_v1.json, 10 monthly, determinism.
 */
class RewardsBenchProfileBindingTest {

    private String savedProfileIdsPath;

    @BeforeEach
    void setProfileIdsPathToTestResource() throws Exception {
        savedProfileIdsPath = System.getProperty("io.yukti.bench.profileIdsPath");
        Path resource = Path.of(getClass().getClassLoader().getResource("profile_ids_v1.json").toURI());
        System.setProperty("io.yukti.bench.profileIdsPath", resource.toString());
    }

    @AfterEach
    void restoreProfileIdsPath() {
        if (savedProfileIdsPath != null) {
            System.setProperty("io.yukti.bench.profileIdsPath", savedProfileIdsPath);
        } else {
            System.clearProperty("io.yukti.bench.profileIdsPath");
        }
    }

    @Test
    void profileCountIs50() throws Exception {
        BenchmarkHarness harness = new BenchmarkHarness();
        assertEquals(50, harness.getProfileIds().size());
    }

    @Test
    void profileIdsExactMatchWithCanonicalList() throws Exception {
        BenchmarkHarness harness = new BenchmarkHarness();
        List<String> generated = harness.getProfileIds();
        List<String> canonical = ProfileIdsLoader.load(
                Path.of(getClass().getClassLoader().getResource("profile_ids_v1.json").toURI()));
        assertEquals(canonical.size(), generated.size());
        assertTrue(java.util.Set.copyOf(canonical).equals(java.util.Set.copyOf(generated)),
                "Generated profile IDs must equal canonical set. Missing or extra: "
                        + symmetricDiff(java.util.Set.copyOf(canonical), java.util.Set.copyOf(generated)));
    }

    @Test
    void monthlyProfileCountIs10() throws Exception {
        BenchmarkHarness harness = new BenchmarkHarness();
        long monthly = harness.getProfileIds().stream()
                .filter(id -> id.startsWith("monthly-"))
                .count();
        assertEquals(10, monthly);
    }

    @Test
    void twoRunsProduceIdenticalCanonicalJsonExceptTimestamp(@TempDir Path dir) throws Exception {
        BenchmarkHarness harness = new BenchmarkHarness();
        Path out1 = dir.resolve("run1.json");
        Path out2 = dir.resolve("run2.json");
        harness.writeResultsJson(out1);
        harness.writeResultsJson(out2);
        String content1 = normalizeForComparison(Files.readString(out1));
        String content2 = normalizeForComparison(Files.readString(out2));
        assertEquals(content1, content2, "Two bench runs in process must produce identical canonical JSON (except timestamp and timing)");
    }

    /** Redact non-deterministic fields so two runs can be compared. */
    private static String normalizeForComparison(String json) {
        return json
                .replaceAll("\"generatedAtIso\": \"[^\"]*\"", "\"generatedAtIso\": \"REDACTED\"")
                .replaceAll("\"elapsedNs\": [0-9]+", "\"elapsedNs\": 0")
                .replaceAll("\"verificationTimeMs\": [0-9]+", "\"verificationTimeMs\": 0");
    }

    private static String symmetricDiff(java.util.Set<String> a, java.util.Set<String> b) {
        java.util.Set<String> missing = new java.util.TreeSet<>(a);
        missing.removeAll(b);
        java.util.Set<String> extra = new java.util.TreeSet<>(b);
        extra.removeAll(a);
        return "missing=" + missing + " extra=" + extra;
    }
}
