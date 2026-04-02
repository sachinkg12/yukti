package io.yukti.explain.core.evidence.graph;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Deterministic evidenceId from block structured fields only (paper-aligned).
 * EvidenceId is SHA-256 over canonical structured fields: type, cardId, category. The content string
 * is a display artifact and is not included in the digest, so verification is independent of rendered text drift.
 */
public final class EvidenceIdHelper {

    private static final String CANONICAL_SEP = "|";
    private static final HexFormat HEX = HexFormat.of();

    private EvidenceIdHelper() {}

    /**
     * Compute a stable evidenceId from evidence structured fields only. Deterministic: same type, cardId, category → same id.
     * Content is not used in the digest (display artifact only).
     *
     * @param type    block type (e.g. WINNER_BY_CATEGORY, CAP_HIT)
     * @param cardId  card identifier (may be empty)
     * @param category category (may be empty)
     * @param content human-readable content (display only; not included in digest; may be null)
     * @return SHA-256 hex string (64 chars) as the evidence id
     */
    public static String compute(String type, String cardId, String category, String content) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(cardId);
        Objects.requireNonNull(category);
        String canonical = type + CANONICAL_SEP + cardId + CANONICAL_SEP + category;
        return sha256Hex(canonical);
    }

    public static String sha256Hex(String input) {
        return sha256Hex(input.getBytes(StandardCharsets.UTF_8));
    }

    /** SHA-256 hash of bytes (e.g. canonical JSON bytes). */
    public static String sha256Hex(byte[] input) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
        byte[] digest = md.digest(input);
        return HEX.formatHex(digest);
    }
}
