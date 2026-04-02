package io.yukti.explain.core.canonical;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class YuktiCanonicalizerTest {

    private static final List<String> FIXTURES = List.of(
        "fixture1_simple.json",
        "fixture2_nested.json",
        "fixture3_arrays.json",
        "fixture4_mixed.json",
        "fixture5_domain_numbers.json"
    );

    private static String loadFixture(String name) throws IOException {
        try (var in = YuktiCanonicalizerTest.class.getResourceAsStream("/fixtures/canonical/" + name)) {
            assertNotNull(in, "fixture not found: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void canonicalize_twoRuns_producesIdenticalBytes() throws IOException {
        for (String name : FIXTURES) {
            String json = loadFixture(name);
            byte[] first = YuktiCanonicalizer.canonicalize(json);
            byte[] second = YuktiCanonicalizer.canonicalize(json);
            assertArrayEquals(first, second, "Two runs must produce identical bytes for " + name);
        }
    }

    @Test
    void canonicalize_thenDigest_stableAcrossRuns() throws IOException {
        for (String name : FIXTURES) {
            String json = loadFixture(name);
            byte[] canonical = YuktiCanonicalizer.canonicalize(json);
            String digest1 = DigestUtil.sha256Hex(canonical);
            String digest2 = DigestUtil.sha256Hex(canonical);
            assertEquals(digest1, digest2);
            assertEquals(64, digest1.length());
            assertTrue(digest1.matches("[0-9a-f]{64}"), "digest must be lowercase hex");
        }
    }

    @Test
    void canonicalize_endsWithNewline() throws IOException {
        for (String name : FIXTURES) {
            String json = loadFixture(name);
            byte[] bytes = YuktiCanonicalizer.canonicalize(json);
            assertTrue(bytes.length > 0);
            assertEquals('\n', bytes[bytes.length - 1], "canonical output must end with newline: " + name);
        }
    }

    @Test
    void canonicalize_utf8() throws IOException {
        String json = loadFixture("fixture1_simple.json");
        byte[] bytes = YuktiCanonicalizer.canonicalize(json);
        assertNotNull(new String(bytes, StandardCharsets.UTF_8));
    }

    @Test
    void canonicalize_keysSortedLexicographically() throws IOException {
        String json = "{\"z\":1,\"a\":2,\"m\":3}";
        byte[] bytes = YuktiCanonicalizer.canonicalize(json);
        String s = new String(bytes, StandardCharsets.UTF_8).trim();
        // Keys sorted a < m < z; default scale 6 for numbers
        assertEquals("{\"a\":2.000000,\"m\":3.000000,\"z\":1.000000}", s);
    }

    @Test
    void canonicalize_usdScale2_cppScale3_pointsScale0() throws IOException {
        String json = loadFixture("fixture5_domain_numbers.json");
        byte[] bytes = YuktiCanonicalizer.canonicalize(json);
        String s = new String(bytes, StandardCharsets.UTF_8).trim();
        assertTrue(s.contains("405.50"), "netValueUsd scale 2");
        assertTrue(s.contains("1.400"), "cpp scale 3");
        assertTrue(s.contains("1001"), "pointsEarned scale 0");
        assertTrue(s.contains("450.00") && s.contains("95.00"), "nested usd scale 2");
    }

    @Test
    void canonicalize_booleanAndNull() throws IOException {
        String json = "{\"x\":null,\"t\":true,\"f\":false}";
        byte[] bytes = YuktiCanonicalizer.canonicalize(json);
        String s = new String(bytes, StandardCharsets.UTF_8).trim();
        assertEquals("{\"f\":false,\"t\":true,\"x\":null}", s);
    }

}
