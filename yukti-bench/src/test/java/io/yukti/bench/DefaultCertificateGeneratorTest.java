package io.yukti.bench;

import io.yukti.core.artifacts.ReproducibilityCertificate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class DefaultCertificateGeneratorTest {

    @Test
    void generate_producesCertificateWithHashes(@TempDir Path dir) throws Exception {
        Path benchPath = dir.resolve("bench_results.json");
        Map<String, Object> runStamp = new TreeMap<>(Map.of(
            "benchVersion", "v1",
            "catalogVersion", "1.0",
            "catalogBundleSha256", "abc",
            "solverId", "cap-aware-greedy-v1",
            "valuationConfigSha256", "def",
            "generatedAtIso", "2025-02-23T12:00:00Z",
            "profileSetId", "xyz",
            "profileCount", 50
        ));
        Map<String, Object> root = new TreeMap<>(Map.of(
            "runStamp", runStamp,
            "benchVersion", "v1",
            "results", List.of()
        ));
        Files.writeString(benchPath, "{\"runStamp\":{\"benchVersion\":\"v1\",\"catalogVersion\":\"1.0\",\"generatedAtIso\":\"2025-02-23T12:00:00Z\"},\"results\":[]}");

        DefaultCertificateGenerator generator = new DefaultCertificateGenerator();
        ReproducibilityCertificate cert = generator.generate(benchPath);

        assertEquals("1", cert.version());
        assertNotNull(cert.configHash());
        assertFalse(cert.configHash().isEmpty());
        assertNotNull(cert.resultHash());
        assertFalse(cert.resultHash().isEmpty());
        assertNotNull(cert.timestamp());
        assertNotNull(cert.runStamp());
        assertEquals("1970-01-01T00:00:00Z", cert.runStamp().get("generatedAtIso"));
    }

    @Test
    void writeCertificate_createsFile(@TempDir Path dir) throws Exception {
        Path benchPath = dir.resolve("bench_results.json");
        Files.writeString(benchPath, "{\"runStamp\":{\"benchVersion\":\"v1\",\"generatedAtIso\":\"2025-01-01T00:00:00Z\"},\"results\":[]}");
        Path certPath = dir.resolve("reproducibility_certificate.json");

        DefaultCertificateGenerator.writeCertificate(benchPath, certPath);

        assertTrue(Files.isRegularFile(certPath));
        String content = Files.readString(certPath);
        assertTrue(content.contains("\"configHash\""));
        assertTrue(content.contains("\"resultHash\""));
        assertTrue(content.contains("\"version\""));
    }
}
