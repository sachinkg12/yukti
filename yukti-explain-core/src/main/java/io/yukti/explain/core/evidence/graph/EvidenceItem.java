package io.yukti.explain.core.evidence.graph;

import io.yukti.explain.core.canonical.DigestUtil;
import io.yukti.explain.core.canonical.YuktiCanonicalizer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Single evidence node payload for EvidenceGraph v1.
 * Immutable. evidenceId and payloadDigest are derived from content for determinism.
 * Numbers should be normalized numeric strings (e.g. {@link java.math.BigDecimal#toPlainString()}) for stable digests.
 */
public final class EvidenceItem {

    private final String evidenceId;
    private final String evidenceType;
    private final String evidenceVersion;
    private final Map<String, Object> stablePayload;
    private final SortedSet<String> entities;
    private final SortedSet<String> numbers;
    private final String payloadDigest;

    private EvidenceItem(
            String evidenceId,
            String evidenceType,
            String evidenceVersion,
            Map<String, Object> stablePayload,
            SortedSet<String> entities,
            SortedSet<String> numbers,
            String payloadDigest
    ) {
        this.evidenceId = Objects.requireNonNull(evidenceId);
        this.evidenceType = Objects.requireNonNull(evidenceType);
        this.evidenceVersion = Objects.requireNonNull(evidenceVersion);
        this.stablePayload = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(stablePayload)));
        this.entities = Collections.unmodifiableSortedSet(new TreeSet<>(Objects.requireNonNull(entities)));
        this.numbers = Collections.unmodifiableSortedSet(new TreeSet<>(Objects.requireNonNull(numbers)));
        this.payloadDigest = Objects.requireNonNull(payloadDigest);
    }

    /**
     * Build an EvidenceItem. evidenceId and payloadDigest are computed from content.
     * evidenceId = sha256(canonical JSON of evidenceType, evidenceVersion, stablePayload, entities, numbers).
     * payloadDigest = sha256(canonical JSON of stablePayload).
     */
    public static EvidenceItem of(
            String evidenceType,
            String evidenceVersion,
            Map<String, Object> stablePayload,
            SortedSet<String> entities,
            SortedSet<String> numbers
    ) {
        Map<String, Object> payload = stablePayload != null ? new LinkedHashMap<>(stablePayload) : new LinkedHashMap<>();
        SortedSet<String> ent = entities != null ? new TreeSet<>(entities) : new TreeSet<>();
        SortedSet<String> num = numbers != null ? new TreeSet<>(numbers) : new TreeSet<>();

        String payloadDigest = computePayloadDigest(payload);
        String evidenceId = computeEvidenceId(evidenceType, evidenceVersion, payload, ent, num);

        return new EvidenceItem(evidenceId, evidenceType, evidenceVersion, payload, ent, num, payloadDigest);
    }

    /**
     * evidenceId = sha256(canonical JSON of evidenceType, evidenceVersion, stablePayload, entities, numbers).
     */
    public static String computeEvidenceId(
            String evidenceType,
            String evidenceVersion,
            Map<String, Object> stablePayload,
            SortedSet<String> entities,
            SortedSet<String> numbers
    ) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("evidenceType", evidenceType != null ? evidenceType : "");
        m.put("evidenceVersion", evidenceVersion != null ? evidenceVersion : "");
        m.put("stablePayload", stablePayload != null ? stablePayload : Map.of());
        m.put("entities", entities != null ? entities : new TreeSet<String>());
        m.put("numbers", numbers != null ? numbers : new TreeSet<String>());
        byte[] canonical = YuktiCanonicalizer.canonicalize(EvidenceGraphDigest.toJsonNode(m));
        return DigestUtil.sha256Hex(canonical);
    }

    /**
     * payloadDigest = sha256(canonical JSON of stablePayload).
     */
    public static String computePayloadDigest(Map<String, Object> stablePayload) {
        Map<String, Object> p = stablePayload != null ? stablePayload : Map.of();
        byte[] canonical = YuktiCanonicalizer.canonicalize(EvidenceGraphDigest.toJsonNode(p));
        return DigestUtil.sha256Hex(canonical);
    }

    public String getEvidenceId() { return evidenceId; }
    public String getEvidenceType() { return evidenceType; }
    public String getEvidenceVersion() { return evidenceVersion; }
    public Map<String, Object> getStablePayload() { return stablePayload; }
    public SortedSet<String> getEntities() { return entities; }
    public SortedSet<String> getNumbers() { return numbers; }
    public String getPayloadDigest() { return payloadDigest; }
}
