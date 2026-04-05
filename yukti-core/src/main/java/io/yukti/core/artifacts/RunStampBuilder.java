package io.yukti.core.artifacts;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Builds RunStamp with environment fields (javaVersion, osName, cpuInfo, gitCommit).
 */
public final class RunStampBuilder {

    public static RunStamp build(
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
            Path experimentConfigPath
    ) {
        String gitCommit = gitCommit();
        String generatedAtIso = Instant.now().toString();
        String javaVersion = System.getProperty("java.version", "");
        String osName = System.getProperty("os.name", "");
        String cpuInfo = cpuInfo();
        String experimentConfigSha256 = experimentConfigPath != null
                ? ConfigHash.sha256OfFile(experimentConfigPath)
                : "";
        return new RunStamp(
                benchVersion,
                catalogVersion,
                catalogBundleSha256,
                solverId,
                valuationConfigSha256,
                penaltyPolicyId,
                creditsModeId,
                profileSetId,
                profileCount,
                profileIdsSorted,
                gitCommit,
                generatedAtIso,
                javaVersion,
                osName,
                cpuInfo,
                experimentConfigSha256
        );
    }

    private static String gitCommit() {
        try {
            Process p = new ProcessBuilder("git", "rev-parse", "HEAD")
                    .directory(Path.of(".").toFile())
                    .start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (p.waitFor() == 0 && !out.isEmpty()) return out;
        } catch (Exception ignored) { }
        return "";
    }

    private static String cpuInfo() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac") || os.contains("darwin")) {
            try {
                Process p = new ProcessBuilder("sysctl", "-n", "machdep.cpu.brand_string").start();
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                if (p.waitFor() == 0 && !out.isEmpty()) return out;
            } catch (Exception ignored) { }
        }
        if (os.contains("linux")) {
            try {
                Path p = Path.of("/proc/cpuinfo");
                if (p.toFile().exists()) {
                    String content = new String(java.nio.file.Files.readAllBytes(p), StandardCharsets.UTF_8);
                    for (String line : content.split("\n")) {
                        if (line.startsWith("model name")) {
                            int colon = line.indexOf(':');
                            if (colon >= 0) return line.substring(colon + 1).trim();
                        }
                    }
                }
            } catch (Exception ignored) { }
        }
        return null;
    }
}
