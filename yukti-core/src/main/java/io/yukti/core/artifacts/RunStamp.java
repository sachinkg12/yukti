package io.yukti.core.artifacts;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Immutable run stamp for bench and paper reproducibility.
 * Emitted with every bench run; used for audit and determinism checks.
 */
public final class RunStamp {

    private final String benchVersion;
    private final String catalogVersion;
    private final String catalogBundleSha256;
    private final String solverId;
    private final String valuationConfigSha256;
    private final String penaltyPolicyId;
    private final String creditsModeId;
    private final String profileSetId;
    private final int profileCount;
    private final List<String> profileIdsSorted;
    private final String gitCommit;
    private final String generatedAtIso;
    private final String javaVersion;
    private final String osName;
    private final String cpuInfo;
    private final String experimentConfigSha256;

    public RunStamp(
            String benchVersion,
            String catalogVersion,
            String catalogBundleSha256,
            String solverId,
            String valuationConfigSha256,
            String penaltyPolicyId,
            String creditsModeId,
            String profileSetId,
            int profileCount,
            List<String> profileIdsSorted,
            String gitCommit,
            String generatedAtIso,
            String javaVersion,
            String osName,
            String cpuInfo,
            String experimentConfigSha256
    ) {
        this.benchVersion = benchVersion != null ? benchVersion : "";
        this.catalogVersion = catalogVersion != null ? catalogVersion : "";
        this.catalogBundleSha256 = catalogBundleSha256 != null ? catalogBundleSha256 : "";
        this.solverId = solverId != null ? solverId : "";
        this.valuationConfigSha256 = valuationConfigSha256 != null ? valuationConfigSha256 : "";
        this.penaltyPolicyId = penaltyPolicyId != null ? penaltyPolicyId : "";
        this.creditsModeId = creditsModeId != null ? creditsModeId : "";
        this.profileSetId = profileSetId != null ? profileSetId : "";
        this.profileCount = profileCount;
        this.profileIdsSorted = profileIdsSorted != null ? List.copyOf(profileIdsSorted) : List.of();
        this.gitCommit = gitCommit != null ? gitCommit : "";
        this.generatedAtIso = generatedAtIso != null ? generatedAtIso : "";
        this.javaVersion = javaVersion != null ? javaVersion : "";
        this.osName = osName != null ? osName : "";
        this.cpuInfo = cpuInfo;
        this.experimentConfigSha256 = experimentConfigSha256 != null ? experimentConfigSha256 : "";
    }

    /** Converts this stamp to a map with stable key order (alphabetical) for canonical JSON. */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new TreeMap<>();
        m.put("benchVersion", benchVersion);
        m.put("catalogBundleSha256", catalogBundleSha256);
        m.put("catalogVersion", catalogVersion);
        m.put("cpuInfo", cpuInfo);
        m.put("creditsModeId", creditsModeId);
        m.put("experimentConfigSha256", experimentConfigSha256);
        m.put("generatedAtIso", generatedAtIso);
        m.put("gitCommit", gitCommit);
        m.put("javaVersion", javaVersion);
        m.put("osName", osName);
        m.put("penaltyPolicyId", penaltyPolicyId);
        m.put("profileCount", profileCount);
        m.put("profileIdsSorted", profileIdsSorted);
        m.put("profileSetId", profileSetId);
        m.put("solverId", solverId);
        m.put("valuationConfigSha256", valuationConfigSha256);
        return m;
    }

    public String benchVersion() { return benchVersion; }
    public String catalogVersion() { return catalogVersion; }
    public String catalogBundleSha256() { return catalogBundleSha256; }
    public String solverId() { return solverId; }
    public String valuationConfigSha256() { return valuationConfigSha256; }
    public String penaltyPolicyId() { return penaltyPolicyId; }
    public String creditsModeId() { return creditsModeId; }
    public String profileSetId() { return profileSetId; }
    public int profileCount() { return profileCount; }
    public List<String> profileIdsSorted() { return profileIdsSorted; }
    public String gitCommit() { return gitCommit; }
    public String generatedAtIso() { return generatedAtIso; }
    public String javaVersion() { return javaVersion; }
    public String osName() { return osName; }
    public String cpuInfo() { return cpuInfo; }
    public String experimentConfigSha256() { return experimentConfigSha256; }
}
