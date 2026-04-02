package io.yukti.explain.core.canonical;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Snapshot tests: canonical bytes and digest for each fixture must match saved snapshots.
 * Run with fixture content loaded from resources; snapshots in fixtures/canonical/snapshots/.
 * To regenerate snapshots: -DgenerateCanonicalSnapshots=true then copy from build/canonical_snapshots/.
 */
class CanonicalSnapshotTest {

    private static final List<String> FIXTURES = List.of(
        "fixture1_simple", "fixture2_nested", "fixture3_arrays", "fixture4_mixed", "fixture5_domain_numbers"
    );

    private static String load(String path) throws IOException {
        try (var in = CanonicalSnapshotTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Canonical output always ends with newline. */
    private static String expectedCanonicalWithNewline(String fromFile) {
        return fromFile != null && !fromFile.endsWith("\n") ? fromFile + "\n" : fromFile;
    }

    @Test
    @EnabledIfSystemProperty(named = "generateCanonicalSnapshots", matches = "true")
    void generateSnapshotFiles() throws IOException {
        Path buildDir = Path.of("build");
        if (!Files.isDirectory(buildDir)) buildDir = Path.of("../build");
        Path out = buildDir.resolve("canonical_snapshots");
        Files.createDirectories(out);
        for (String base : FIXTURES) {
            String json = load("/fixtures/canonical/" + base + ".json");
            byte[] canonical = YuktiCanonicalizer.canonicalize(json);
            String digest = DigestUtil.sha256Hex(canonical);
            Files.writeString(out.resolve(base + "_canonical.txt"), new String(canonical, StandardCharsets.UTF_8));
            Files.writeString(out.resolve(base + "_digest.txt"), digest + "\n");
        }
    }

    @Test
    void fixture1_snapshotMatch() throws IOException {
        String json = load("/fixtures/canonical/fixture1_simple.json");
        byte[] canonical = YuktiCanonicalizer.canonicalize(json);
        String digest = DigestUtil.sha256Hex(canonical);
        String expectedCanonical = load("/fixtures/canonical/snapshots/fixture1_simple_canonical.txt");
        String expectedDigest = load("/fixtures/canonical/snapshots/fixture1_simple_digest.txt").trim();
        assertEquals(expectedCanonicalWithNewline(expectedCanonical), new String(canonical, StandardCharsets.UTF_8));
        assertEquals(expectedDigest, digest);
    }

    @Test
    void fixture2_snapshotMatch() throws IOException {
        String json = load("/fixtures/canonical/fixture2_nested.json");
        byte[] canonical = YuktiCanonicalizer.canonicalize(json);
        String digest = DigestUtil.sha256Hex(canonical);
        String expectedCanonical = load("/fixtures/canonical/snapshots/fixture2_nested_canonical.txt");
        String expectedDigest = load("/fixtures/canonical/snapshots/fixture2_nested_digest.txt").trim();
        assertEquals(expectedCanonicalWithNewline(expectedCanonical), new String(canonical, StandardCharsets.UTF_8));
        assertEquals(expectedDigest, digest);
    }

    @Test
    void fixture3_snapshotMatch() throws IOException {
        String json = load("/fixtures/canonical/fixture3_arrays.json");
        byte[] canonical = YuktiCanonicalizer.canonicalize(json);
        String digest = DigestUtil.sha256Hex(canonical);
        String expectedCanonical = load("/fixtures/canonical/snapshots/fixture3_arrays_canonical.txt");
        String expectedDigest = load("/fixtures/canonical/snapshots/fixture3_arrays_digest.txt").trim();
        assertEquals(expectedCanonicalWithNewline(expectedCanonical), new String(canonical, StandardCharsets.UTF_8));
        assertEquals(expectedDigest, digest);
    }

    @Test
    void fixture4_snapshotMatch() throws IOException {
        String json = load("/fixtures/canonical/fixture4_mixed.json");
        byte[] canonical = YuktiCanonicalizer.canonicalize(json);
        String digest = DigestUtil.sha256Hex(canonical);
        String expectedCanonical = load("/fixtures/canonical/snapshots/fixture4_mixed_canonical.txt");
        String expectedDigest = load("/fixtures/canonical/snapshots/fixture4_mixed_digest.txt").trim();
        assertEquals(expectedCanonicalWithNewline(expectedCanonical), new String(canonical, StandardCharsets.UTF_8));
        assertEquals(expectedDigest, digest);
    }

    @Test
    void fixture5_snapshotMatch() throws IOException {
        String json = load("/fixtures/canonical/fixture5_domain_numbers.json");
        byte[] canonical = YuktiCanonicalizer.canonicalize(json);
        String digest = DigestUtil.sha256Hex(canonical);
        String expectedCanonical = load("/fixtures/canonical/snapshots/fixture5_domain_numbers_canonical.txt");
        String expectedDigest = load("/fixtures/canonical/snapshots/fixture5_domain_numbers_digest.txt").trim();
        assertEquals(expectedCanonicalWithNewline(expectedCanonical), new String(canonical, StandardCharsets.UTF_8));
        assertEquals(expectedDigest, digest);
    }
}
