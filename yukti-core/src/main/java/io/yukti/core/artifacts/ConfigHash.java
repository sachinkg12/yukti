package io.yukti.core.artifacts;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

/**
 * SHA-256 of config file bytes for RunStamp and audit.
 */
public final class ConfigHash {

    private ConfigHash() {}

    /**
     * Returns lowercase hex SHA-256 of the file's raw bytes, or empty string if file missing or error.
     */
    public static String sha256OfFile(Path path) {
        try {
            if (path == null || !Files.isRegularFile(path)) return "";
            byte[] bytes = Files.readAllBytes(path);
            return sha256Hex(bytes);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Returns lowercase hex SHA-256 of the UTF-8 bytes of the given string.
     */
    public static String sha256OfUtf8(String s) {
        if (s == null) return "";
        return sha256Hex(s.getBytes(StandardCharsets.UTF_8));
    }

    /** Lowercase hex SHA-256 of raw bytes. */
    public static String sha256Hex(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
