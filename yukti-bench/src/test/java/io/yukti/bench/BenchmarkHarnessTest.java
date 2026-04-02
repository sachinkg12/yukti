package io.yukti.bench;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for RewardsBench v1 (50 profiles). Disabled because v2 (200 profiles) is now default.
 * Run manually with: ./gradlew :yukti-bench:test -Dio.yukti.bench.version=v1
 *
 * Original: RewardsBench v1 must have exactly 50 profiles (40 annual, 10 monthly),
 * stable unique IDs. Paper metrics and docs/bench/profile_ids_v1.json are bound to this set.
 */
@org.junit.jupiter.api.Disabled("v1 binding test — skipped because v2 (200 profiles) is now default")
class BenchmarkHarnessTest {

    private static final int EXPECTED_PROFILE_COUNT = 50;
    private static final int EXPECTED_MONTHLY_COUNT = 10;

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
        List<String> ids = harness.getProfileIds();
        assertEquals(EXPECTED_PROFILE_COUNT, ids.size(),
            "RewardsBench v1 must have exactly 50 profiles; paper metrics and profile_ids_v1.json depend on it.");
    }

    @Test
    void profileIdsAreUnique() throws Exception {
        BenchmarkHarness harness = new BenchmarkHarness();
        List<String> ids = harness.getProfileIds();
        assertEquals(ids.size(), new HashSet<>(ids).size(),
            "Profile IDs must be unique.");
    }

    @Test
    void monthlyProfileCountIs10() throws Exception {
        BenchmarkHarness harness = new BenchmarkHarness();
        List<String> ids = harness.getProfileIds();
        long monthly = ids.stream().filter(id -> id.startsWith("monthly-")).count();
        assertEquals(EXPECTED_MONTHLY_COUNT, monthly,
            "Exactly 10 monthly profiles required (ids starting with 'monthly-').");
    }

    @Test
    void profileIdsAreKebabCaseAndStable() throws Exception {
        BenchmarkHarness harness = new BenchmarkHarness();
        List<String> ids = harness.getProfileIds();
        for (String id : ids) {
            assertTrue(id.matches("[a-z0-9]+(-[a-z0-9]+)*"),
                "Profile ID must be kebab-case: " + id);
        }
        // First and last in stable order (baseline first, monthly last)
        assertEquals("light", ids.get(0));
        assertEquals("monthly-commuter", ids.get(ids.size() - 1));
    }

    @Test
    void profileIdsMatchCanonicalSetInDocs() throws Exception {
        Path profileIdsFile = findProfileIdsV1();
        List<String> expected = new ObjectMapper().readValue(
            profileIdsFile.toFile(), new TypeReference<>() {});
        BenchmarkHarness harness = new BenchmarkHarness();
        Set<String> actual = new HashSet<>(harness.getProfileIds());
        Set<String> expectedSet = new HashSet<>(expected);
        assertEquals(expectedSet, actual,
            "Harness profile IDs must equal docs/bench/profile_ids_v1.json; "
                + "update that file and paper metrics binding when changing createProfiles50().");
    }

    private static Path findProfileIdsV1() {
        for (String rel : new String[]{"docs/bench/profile_ids_v1.json", "../docs/bench/profile_ids_v1.json"}) {
            Path p = Paths.get(rel).toAbsolutePath().normalize();
            if (Files.isRegularFile(p)) return p;
        }
        throw new IllegalStateException("docs/bench/profile_ids_v1.json not found from " + Paths.get("").toAbsolutePath());
    }
}
