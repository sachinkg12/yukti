package io.yukti.bench.verifier;

import io.yukti.explain.core.claims.Claim;
import io.yukti.explain.core.claims.ClaimType;
import io.yukti.explain.core.claims.ClaimVerifier;
import io.yukti.explain.core.evidence.graph.EvidenceEdge;
import io.yukti.explain.core.evidence.graph.EvidenceGraph;
import io.yukti.explain.core.evidence.graph.EvidenceNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Microbench for ClaimVerifier — measures per-instance verification latency,
 * throughput, and JVM heap footprint on a representative evidence graph + claim list.
 *
 * <p>Representative input dimensions match the paper-grade eval:
 *   - 20 evidence nodes across 6 types (CAP_HIT, WINNER_BY_CATEGORY, ALLOCATION_SEGMENT,
 *     FEE_BREAK_EVEN, EARN_RATE, ASSUMPTION)
 *   - 30 allowed entities (card identifiers + category names + currency codes)
 *   - 100 allowed numbers (dollar amounts + percentages + counts)
 *   - 14 claims per instance (mean from paper-grade Run B with repair=1 on Sonnet)
 *
 * <p>Output: prints a result block + a markdown table ready to paste into §4.
 */
public final class VerifierMicroBench {

    private static final int WARMUP_ITERATIONS = 10_000;
    private static final int MEASURE_ITERATIONS = 100_000;

    public static void main(String[] args) {
        EvidenceGraph graph = buildRepresentativeGraph();
        List<Claim> claims = buildRepresentativeClaims();
        ClaimVerifier verifier = new ClaimVerifier();

        System.out.println("=== VerifierMicroBench ===");
        System.out.println("Evidence nodes:    " + graph.getNodes().size());
        System.out.println("Allowed entities:  " + graph.getAllowedEntities().size());
        System.out.println("Allowed numbers:   " + graph.getAllowedNumbers().size());
        System.out.println("Claims / instance: " + claims.size());
        System.out.println("Warmup iter:       " + WARMUP_ITERATIONS);
        System.out.println("Measure iter:      " + MEASURE_ITERATIONS);
        System.out.println();

        // Warmup
        long warmupAcc = 0;
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            warmupAcc += verifier.verify(graph, claims).hashCode();
        }
        // Print accumulator to prevent JIT from optimizing away the call.
        System.out.println("(warmup acc:       " + warmupAcc + " — ignore)");

        // Force a GC before measurement to stabilize heap
        System.gc();
        long heapBefore = usedHeap();

        // Measure
        long[] timings = new long[MEASURE_ITERATIONS];
        long sideEffect = 0;
        for (int i = 0; i < MEASURE_ITERATIONS; i++) {
            long t0 = System.nanoTime();
            sideEffect += verifier.verify(graph, claims).hashCode();
            timings[i] = System.nanoTime() - t0;
        }
        long heapAfter = usedHeap();

        // Stats
        Arrays.sort(timings);
        long min = timings[0];
        long p50 = timings[MEASURE_ITERATIONS / 2];
        long p95 = timings[(int) (MEASURE_ITERATIONS * 0.95)];
        long p99 = timings[(int) (MEASURE_ITERATIONS * 0.99)];
        long max = timings[MEASURE_ITERATIONS - 1];
        double mean = Arrays.stream(timings).average().orElse(0);
        double throughput = 1.0e9 / mean;   // verifications / sec (single thread)

        System.out.println();
        System.out.println("(side effect acc:  " + sideEffect + " — ignore)");
        System.out.println();
        System.out.println("=== Results ===");
        System.out.printf("Per-instance latency (ns): min=%d  p50=%d  p95=%d  p99=%d  max=%d  mean=%.0f%n",
            min, p50, p95, p99, max, mean);
        System.out.printf("Per-instance latency (us): min=%.2f  p50=%.2f  p95=%.2f  p99=%.2f  max=%.2f  mean=%.2f%n",
            min / 1e3, p50 / 1e3, p95 / 1e3, p99 / 1e3, max / 1e3, mean / 1e3);
        System.out.printf("Sustained throughput:      %.0f verifications/sec/core%n", throughput);
        System.out.printf("Heap used (MB):            before=%.1f  after=%.1f  delta=%.1f%n",
            heapBefore / 1.0e6, heapAfter / 1.0e6, (heapAfter - heapBefore) / 1.0e6);
        long maxHeap = Runtime.getRuntime().maxMemory();
        System.out.printf("Max heap (configured MB):  %.0f%n", maxHeap / 1.0e6);

        System.out.println();
        System.out.println("=== Markdown table (paste into §4.4) ===");
        System.out.println();
        System.out.println("| Metric | Value |");
        System.out.println("|---|---:|");
        System.out.printf("| p50 latency / instance | %.1f μs |%n", p50 / 1e3);
        System.out.printf("| p95 latency / instance | %.1f μs |%n", p95 / 1e3);
        System.out.printf("| p99 latency / instance | %.1f μs |%n", p99 / 1e3);
        System.out.printf("| Sustained throughput   | %.0f verif/sec/core |%n", throughput);
        System.out.printf("| Heap (per process)     | < %.0f MB |%n",
            Math.max(64.0, Math.ceil(heapAfter / 1.0e6 / 64.0) * 64.0));
    }

    private static long usedHeap() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    private static EvidenceGraph buildRepresentativeGraph() {
        // 20 nodes across 6 types — matches paper-grade evidence graph dimensions
        String[] types = {
            "WINNER_BY_CATEGORY",
            "CAP_HIT",
            "ALLOCATION_SEGMENT",
            "FEE_BREAK_EVEN",
            "EARN_RATE",
            "ASSUMPTION"
        };
        String[] cards = {"amex-bcp", "chase-sapphire", "citi-double-cash"};
        String[] categories = {"GROCERIES", "DINING", "GAS", "TRAVEL", "ONLINE"};

        List<EvidenceNode> nodes = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String evidenceId = "ev_" + i;
            String type = types[i % types.length];
            String cardId = cards[i % cards.length];
            String category = categories[i % categories.length];
            String content = type + " content body for " + cardId + " in " + category;
            nodes.add(new EvidenceNode(evidenceId, type, cardId, category, content));
        }

        // Allowed entities: 30 names (cards + categories + currencies)
        Set<String> allowedEntities = new HashSet<>();
        allowedEntities.add("amex-bcp");
        allowedEntities.add("chase-sapphire");
        allowedEntities.add("citi-double-cash");
        allowedEntities.add("capital-one-venture");
        allowedEntities.add("discover-it");
        for (String cat : categories) allowedEntities.add(cat);
        for (int i = 0; i < 20; i++) allowedEntities.add("entity_" + i);

        // Allowed numbers: 100 distinct values
        Set<String> allowedNumbers = new HashSet<>();
        for (int i = 0; i < 100; i++) allowedNumbers.add(String.valueOf(50 + i * 7));

        return new EvidenceGraph(
            nodes,
            List.<EvidenceEdge>of(),
            allowedEntities,
            allowedNumbers,
            "sha256:representative"
        );
    }

    private static List<Claim> buildRepresentativeClaims() {
        // 14 claims with mix of types and cited counts — matches paper-grade mean
        ClaimType[] types = {
            ClaimType.COMPARISON,
            ClaimType.THRESHOLD,
            ClaimType.ALLOCATION,
            ClaimType.ASSUMPTION,
            ClaimType.FEE_JUSTIFICATION,
            ClaimType.CAP_SWITCH
        };
        // Each claim cites 2-3 evidence ids, 1-2 entities, 1-2 numbers
        List<Claim> claims = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            ClaimType type = types[i % types.length];
            int evCount = 2 + (i % 2);
            List<String> evidenceIds = new ArrayList<>();
            for (int j = 0; j < evCount; j++) evidenceIds.add("ev_" + ((i + j) % 20));
            List<String> entities = List.of("amex-bcp", "GROCERIES");
            List<String> numbers = List.of(String.valueOf(50 + (i % 100) * 7),
                                            String.valueOf(50 + ((i + 1) % 100) * 7));
            claims.add(new Claim(
                "c_" + i,
                type,
                "Representative claim text for instance " + i,
                evidenceIds,
                entities,
                numbers
            ));
        }
        return claims;
    }
}
