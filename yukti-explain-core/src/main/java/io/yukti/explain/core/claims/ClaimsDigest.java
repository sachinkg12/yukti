package io.yukti.explain.core.claims;

import com.fasterxml.jackson.databind.JsonNode;
import io.yukti.explain.core.canonical.DigestUtil;
import io.yukti.explain.core.canonical.YuktiCanonicalizer;
import io.yukti.explain.core.evidence.graph.EvidenceGraphDigest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Deterministic digest of a list of claims for auditable evaluation and reproducibility.
 * claimsDigest = sha256(canonical JSON of claims sorted by claimId).
 */
public final class ClaimsDigest {

    private ClaimsDigest() {}

    /**
     * Compute digest over claims. Canonical form: array of {claimId, claimType, citedEvidenceIds, citedEntities, citedNumbers} sorted by claimId.
     */
    public static String compute(List<Claim> claims) {
        if (claims == null || claims.isEmpty()) {
            return "";
        }
        Map<String, Object> sorted = new TreeMap<>();
        for (Claim c : claims) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("claimId", c.claimId());
            m.put("claimType", c.claimType().name());
            m.put("citedEvidenceIds", new ArrayList<>(c.citedEvidenceIds()));
            m.put("citedEntities", new ArrayList<>(c.citedEntities()));
            m.put("citedNumbers", new ArrayList<>(c.citedNumbers()));
            sorted.put(c.claimId(), m);
        }
        List<Object> array = new ArrayList<>(sorted.values());
        JsonNode node = EvidenceGraphDigest.toJsonNode(array);
        byte[] canonical = YuktiCanonicalizer.canonicalize(node);
        return DigestUtil.sha256Hex(canonical);
    }
}
