package io.yukti.core.artifacts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression: RunStamp serialization is stable; canonical JSON writer output is stable for a fixture.
 */
class RunStampAndCanonicalJsonTest {

    @TempDir
    Path dir;

    @Test
    void runStampSerializationIsStable() throws Exception {
        RunStamp stamp = new RunStamp(
                "v1",
                "1.0",
                "a1b2c3",
                "cap-aware-greedy-v1",
                "d4e5f6",
                "strict",
                "on",
                "profileSetSha",
                50,
                List.of("a", "b", "c"),
                "abc123",
                "2025-01-01T00:00:00Z",
                "21",
                "Mac OS X",
                null,
                "configSha"
        );
        Map<String, Object> root1 = new TreeMap<>();
        root1.put("runStamp", stamp.toMap());
        Map<String, Object> root2 = new TreeMap<>(root1);

        Path p1 = dir.resolve("run1.json");
        Path p2 = dir.resolve("run2.json");
        CanonicalJsonWriter.writePretty(p1, root1);
        CanonicalJsonWriter.writePretty(p2, root2);

        byte[] bytes1 = Files.readAllBytes(p1);
        byte[] bytes2 = Files.readAllBytes(p2);
        assertArrayEquals(bytes1, bytes2, "RunStamp serialization must be byte-identical across writes");
    }

    @Test
    void canonicalJsonWriterOutputIsStableForFixture() throws Exception {
        Map<String, Object> fixture = fixtureObject();
        Path p1 = dir.resolve("canon1.json");
        Path p2 = dir.resolve("canon2.json");
        CanonicalJsonWriter.writePretty(p1, fixture);
        CanonicalJsonWriter.writePretty(p2, fixture);

        byte[] bytes1 = Files.readAllBytes(p1);
        byte[] bytes2 = Files.readAllBytes(p2);
        assertArrayEquals(bytes1, bytes2, "Canonical JSON writer must produce byte-identical output for same input");

        String content = Files.readString(p1);
        assertTrue(content.endsWith("\n"), "Output must end with newline");
        assertTrue(content.contains("\"netValueUsd\": 123.45"), "USD key should be 2 decimals");
        assertTrue(content.contains("someRate") && content.contains("0.013"), "Non-USD number should be stable");
    }

    @Test
    void canonicalJsonWriterKeysAreSorted() throws Exception {
        Map<String, Object> unsorted = new TreeMap<>();
        unsorted.put("z", 1);
        unsorted.put("a", 2);
        unsorted.put("m", 3);
        Path p = dir.resolve("sorted.json");
        CanonicalJsonWriter.write(p, unsorted);
        String content = Files.readString(p);
        int posA = content.indexOf("\"a\"");
        int posM = content.indexOf("\"m\"");
        int posZ = content.indexOf("\"z\"");
        assertTrue(posA < posM && posM < posZ, "Keys must appear in alphabetical order: " + content);
    }

    private static Map<String, Object> fixtureObject() {
        Map<String, Object> m = new TreeMap<>();
        m.put("netValueUsd", 123.45);
        m.put("someRate", 0.013);
        m.put("list", List.of(1, 2, 3));
        Map<String, Object> nested = new TreeMap<>();
        nested.put("amountUsd", 100.0);
        m.put("nested", nested);
        return m;
    }
}
