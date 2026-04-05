package io.yukti.core.artifacts;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Single experiment configuration used by bench and paper scripts.
 * Stored at docs/paper/experiment_config_v1.json; its SHA-256 is embedded in RunStamp.
 */
public final class ExperimentConfig {

    private final String catalogVersion;
    private final String solverId;
    private final String valuationConfigId;
    private final String penaltyPolicyMode; // "strict" or "soft"
    private final String creditsMode;       // "on" or "off"
    private final String profileSetId;
    private final int expectedProfileCount;
    private final List<String> profileIds;  // optional; if present must match expected count

    @JsonCreator
    public ExperimentConfig(
            @JsonProperty("catalogVersion") String catalogVersion,
            @JsonProperty("solverId") String solverId,
            @JsonProperty("valuationConfigId") String valuationConfigId,
            @JsonProperty("penaltyPolicyMode") String penaltyPolicyMode,
            @JsonProperty("creditsMode") String creditsMode,
            @JsonProperty("profileSetId") String profileSetId,
            @JsonProperty("expectedProfileCount") int expectedProfileCount,
            @JsonProperty("profileIds") List<String> profileIds
    ) {
        this.catalogVersion = catalogVersion != null ? catalogVersion : "1.0";
        this.solverId = solverId != null ? solverId : "cap-aware-greedy-v1";
        this.valuationConfigId = valuationConfigId != null ? valuationConfigId : "default-cpp.v1";
        this.penaltyPolicyMode = penaltyPolicyMode != null ? penaltyPolicyMode : "strict";
        this.creditsMode = creditsMode != null ? creditsMode : "on";
        this.profileSetId = profileSetId != null ? profileSetId : "";
        this.expectedProfileCount = expectedProfileCount;
        this.profileIds = profileIds != null ? List.copyOf(profileIds) : List.of();
    }

    public String catalogVersion() { return catalogVersion; }
    public String solverId() { return solverId; }
    public String valuationConfigId() { return valuationConfigId; }
    public String penaltyPolicyMode() { return penaltyPolicyMode; }
    public String creditsMode() { return creditsMode; }
    public String profileSetId() { return profileSetId; }
    public int expectedProfileCount() { return expectedProfileCount; }
    public List<String> profileIds() { return profileIds; }
}
