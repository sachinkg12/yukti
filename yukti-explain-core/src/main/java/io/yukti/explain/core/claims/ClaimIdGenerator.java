package io.yukti.explain.core.claims;

import io.yukti.explain.core.canonical.YuktiCanonicalizer;
import io.yukti.explain.core.evidence.graph.EvidenceIdHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Computes claimId per schema v1: sha256(canonical JSON of claimType, normalizedFields, citedEvidenceIds).
 * Deterministic: same inputs → same claimId.
 */
public final class ClaimIdGenerator {

    private static final ObjectMapper OM = new ObjectMapper();

    private ClaimIdGenerator() {}

    /**
     * Compute claimId from the id material (claimType, normalizedFields, citedEvidenceIds).
     * citedEvidenceIds are sorted before hashing for determinism.
     */
    public static String compute(
        ClaimType claimType,
        Map<String, ?> normalizedFields,
        List<String> citedEvidenceIds
    ) {
        Objects.requireNonNull(claimType);
        Objects.requireNonNull(normalizedFields);
        Objects.requireNonNull(citedEvidenceIds);
        ObjectNode root = OM.createObjectNode();
        root.put("claimType", claimType.name());
        root.set("normalizedFields", OM.valueToTree(normalizeKeys(normalizedFields)));
        ArrayNode ids = root.putArray("citedEvidenceIds");
        citedEvidenceIds.stream().sorted().forEach(ids::add);
        JsonNode node = root;
        byte[] canonical = YuktiCanonicalizer.canonicalize(node);
        return EvidenceIdHelper.sha256Hex(canonical);
    }

    private static Map<String, ?> normalizeKeys(Map<String, ?> map) {
        if (map instanceof TreeMap) return map;
        return new TreeMap<>(map);
    }
}
