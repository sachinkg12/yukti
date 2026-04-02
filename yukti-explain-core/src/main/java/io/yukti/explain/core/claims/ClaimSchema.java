package io.yukti.explain.core.claims;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Claim schema v1: parse JSON array of claims. Used by LLM narrator and verifier flow.
 */
public final class ClaimSchema {

    private static final ObjectMapper OM = new ObjectMapper();

    /**
     * Parse JSON string to list of claims. Expects array of objects with:
     * claimId, claimType (string), text, citedEvidenceIds (array), citedEntities (array), citedNumbers (array).
     * claimType is case-insensitive (e.g. "COMPARISON" or "comparison").
     *
     * @throws IllegalArgumentException if JSON is invalid or schema mismatch
     */
    public static List<Claim> parseClaimsJson(String json) {
        Objects.requireNonNull(json);
        if (json.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> list = OM.readValue(json, new TypeReference<>() {});
            List<Claim> claims = new ArrayList<>();
            for (Map<String, Object> map : list) {
                String claimId = requireString(map, "claimId");
                ClaimType claimType = parseClaimType(getString(map, "claimType"));
                String text = requireString(map, "text");
                List<String> citedEvidenceIds = getStringList(map, "citedEvidenceIds");
                List<String> citedEntities = getStringList(map, "citedEntities");
                List<String> citedNumbers = getStringList(map, "citedNumbers");
                claims.add(new Claim(claimId, claimType, text, citedEvidenceIds, citedEntities, citedNumbers));
            }
            return claims;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid claim JSON: " + e.getMessage(), e);
        }
    }

    private static String requireString(Map<String, Object> map, String key) {
        String s = getString(map, key);
        if (s == null) {
            throw new IllegalArgumentException("Missing or null field: " + key);
        }
        return s;
    }

    @SuppressWarnings("unchecked")
    private static String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        return v.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<String> getStringList(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return List.of();
        if (v instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                out.add(o != null ? o.toString() : "");
            }
            return out;
        }
        return List.of();
    }

    private static ClaimType parseClaimType(String s) {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException("claimType is required");
        }
        String upper = s.trim().toUpperCase();
        for (ClaimType t : ClaimType.values()) {
            if (t.name().equals(upper)) return t;
        }
        throw new IllegalArgumentException("Unknown claimType: " + s);
    }
}
