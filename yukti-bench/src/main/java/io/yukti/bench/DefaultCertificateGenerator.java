package io.yukti.bench;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yukti.core.artifacts.CanonicalJsonWriter;
import io.yukti.core.artifacts.CertificateGenerator;
import io.yukti.core.artifacts.ConfigHash;
import io.yukti.core.artifacts.ReproducibilityCertificate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

/**
 * Computes configHash (from runStamp with normalized timestamp) and resultHash
 * (from full bench results with normalized timestamp), writes certificate JSON.
 */
public final class DefaultCertificateGenerator implements CertificateGenerator {

    private static final String VERSION = "1";
    private static final String NORMALIZED_TIMESTAMP = "1970-01-01T00:00:00Z";
    private static final ObjectMapper OM = new ObjectMapper();

    @Override
    public ReproducibilityCertificate generate(Path benchResultsPath) throws Exception {
        if (!Files.isRegularFile(benchResultsPath)) {
            throw new IllegalArgumentException("Bench results file not found: " + benchResultsPath);
        }
        byte[] raw = Files.readAllBytes(benchResultsPath);
        Map<String, Object> root = OM.readValue(raw, new TypeReference<>() {});

        @SuppressWarnings("unchecked")
        Map<String, Object> runStamp = (Map<String, Object>) root.get("runStamp");
        if (runStamp == null) {
            runStamp = new TreeMap<>();
        } else {
            runStamp = new TreeMap<>(runStamp);
        }
        runStamp.put("generatedAtIso", NORMALIZED_TIMESTAMP);

        byte[] configBytes = CanonicalJsonWriter.writeToBytes(runStamp);
        String configHash = ConfigHash.sha256Hex(configBytes);

        root.put("runStamp", runStamp);
        byte[] resultBytes = CanonicalJsonWriter.writeToBytes(root);
        String resultHash = ConfigHash.sha256Hex(resultBytes);

        String timestamp = Instant.now().toString();
        return new ReproducibilityCertificate(VERSION, configHash, resultHash, timestamp, runStamp);
    }

    /** Writes certificate JSON to the same directory as the bench results file. */
    public static Path writeCertificate(Path benchResultsPath, Path certificatePath) throws Exception {
        CertificateGenerator generator = new DefaultCertificateGenerator();
        ReproducibilityCertificate cert = generator.generate(benchResultsPath);
        Map<String, Object> out = new TreeMap<>();
        out.put("version", cert.version());
        out.put("configHash", cert.configHash());
        out.put("resultHash", cert.resultHash());
        out.put("timestamp", cert.timestamp());
        out.put("runStamp", cert.runStamp());
        Files.createDirectories(certificatePath.getParent());
        CanonicalJsonWriter.writePretty(certificatePath, out);
        return certificatePath;
    }
}
