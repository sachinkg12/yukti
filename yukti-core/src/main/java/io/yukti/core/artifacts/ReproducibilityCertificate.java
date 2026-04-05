package io.yukti.core.artifacts;

import java.util.Map;

/**
 * DTO for a reproducibility certificate (config hash + result hash + run stamp).
 * Written next to bench_results.json for verification by scripts/verify_reproducibility_certificate.py.
 */
public record ReproducibilityCertificate(
    String version,
    String configHash,
    String resultHash,
    String timestamp,
    Map<String, Object> runStamp
) {
    public ReproducibilityCertificate {
        runStamp = runStamp != null ? Map.copyOf(runStamp) : Map.of();
    }
}
