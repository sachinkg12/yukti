package io.yukti.core.artifacts;

import java.nio.file.Path;

/**
 * Generates a reproducibility certificate from a bench results file.
 * OCP: new implementations (e.g. signed certificate) can be added without changing the harness.
 */
public interface CertificateGenerator {

    /**
     * Reads the bench results file, computes configHash (from runStamp, timestamp normalized)
     * and resultHash (from full file content, timestamp normalized), returns certificate.
     */
    ReproducibilityCertificate generate(Path benchResultsPath) throws Exception;
}
