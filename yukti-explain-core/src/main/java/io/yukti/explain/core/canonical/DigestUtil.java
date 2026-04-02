package io.yukti.explain.core.canonical;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 digest over bytes; returns lowercase hex string.
 */
public final class DigestUtil {

    private static final HexFormat HEX = HexFormat.of();

    private DigestUtil() {}

    /**
     * Compute SHA-256 over the given bytes and return 64-character lowercase hex string.
     */
    public static String sha256Hex(byte[] input) {
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
        byte[] digest = md.digest(input);
        return HEX.formatHex(digest);
    }

    /**
     * Compute SHA-256 over the UTF-8 bytes of the given string; return lowercase hex.
     */
    public static String sha256Hex(String input) {
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        return sha256Hex(input.getBytes(StandardCharsets.UTF_8));
    }
}
