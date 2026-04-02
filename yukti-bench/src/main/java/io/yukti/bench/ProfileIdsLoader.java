package io.yukti.bench;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Loads the canonical profile ID list from docs/bench/profile_ids_{version}.json (single source of truth).
 * Fail-fast if generated profile IDs differ (missing/extra) or count doesn't match expected.
 */
public final class ProfileIdsLoader {

    private static final ObjectMapper OM = new ObjectMapper();

    private ProfileIdsLoader() {}

    /**
     * Loads profile IDs from the given path. File must contain a JSON array of string IDs.
     */
    public static List<String> load(Path path, int expectedCount) throws Exception {
        if (path == null || !Files.isRegularFile(path)) {
            throw new IllegalStateException("Profile IDs file not found: " + path);
        }
        List<String> ids = OM.readValue(path.toFile(), new TypeReference<>() {});
        if (ids.size() != expectedCount) {
            throw new IllegalStateException(
                "Profile count in " + path + " is " + ids.size() + ", expected " + expectedCount + ".");
        }
        return List.copyOf(ids);
    }

    /** Backward-compatible overload for v1 (50 profiles). */
    public static List<String> load(Path path) throws Exception {
        return load(path, 50);
    }

    /**
     * If generated profile IDs do not exactly match the canonical list, prints diff to stderr and exits with code 1.
     */
    public static void failFastIfMismatch(Path profileIdsPath, List<String> generatedIds, int expectedCount) {
        List<String> canonical;
        try {
            canonical = load(profileIdsPath, expectedCount);
        } catch (Exception e) {
            System.err.println("Profile IDs check failed: " + e.getMessage());
            System.exit(1);
            return;
        }
        if (generatedIds.size() != expectedCount) {
            System.err.println("Profile count mismatch: generated " + generatedIds.size() + ", expected " + expectedCount + ".");
            System.exit(1);
            return;
        }
        Set<String> canSet = new TreeSet<>(canonical);
        Set<String> genSet = new TreeSet<>(generatedIds);
        Set<String> missing = new TreeSet<>(canSet);
        missing.removeAll(genSet);
        Set<String> extra = new TreeSet<>(genSet);
        extra.removeAll(canSet);
        if (!missing.isEmpty() || !extra.isEmpty()) {
            System.err.println("Profile IDs do not match " + profileIdsPath + ".");
            if (!missing.isEmpty()) System.err.println("  Missing (in canonical, not generated): " + missing);
            if (!extra.isEmpty()) System.err.println("  Extra (in generated, not in canonical): " + extra);
            System.exit(1);
        }
    }

    /** Backward-compatible overload for v1 (50 profiles). */
    public static void failFastIfMismatch(Path profileIdsPath, List<String> generatedIds) {
        failFastIfMismatch(profileIdsPath, generatedIds, 50);
    }
}
