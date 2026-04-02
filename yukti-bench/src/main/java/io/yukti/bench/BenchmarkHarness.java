package io.yukti.bench;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yukti.catalog.ClasspathCatalogSource;
import io.yukti.catalog.JsonCatalogParser;
import io.yukti.core.api.Catalog;
import io.yukti.core.api.Optimizer;
import io.yukti.core.artifacts.CanonicalJsonWriter;
import io.yukti.core.explainability.NarrativeExplanation;
import io.yukti.core.explainability.StructuredExplanation;
import io.yukti.engine.explainability.DeterministicExplanationGeneratorV1;
import io.yukti.engine.explainability.EvidenceGraphBuilder;
import io.yukti.engine.explainability.StructuredExplanationBuilder;
import io.yukti.explain.core.claims.ClaimsDigest;
import io.yukti.explain.core.claims.ClaimVerifier;
import io.yukti.explain.core.evidence.graph.EvidenceGraph;
import io.yukti.core.artifacts.ConfigHash;
import io.yukti.core.artifacts.RunStamp;
import io.yukti.core.artifacts.RunStampBuilder;
import io.yukti.core.domain.*;
import io.yukti.engine.config.BenchRunConfig;
import io.yukti.engine.optimizer.LpRelaxationOptimizer;
import io.yukti.engine.optimizer.OptimizerRegistry;
import io.yukti.engine.valuation.PenaltyPolicyV1;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Benchmark harness: 50 profiles × 3 goals = 150 runs (RewardsBench v1).
 * Profiles are deterministic, stable-order; see createProfiles50() and docs/bench/profiles.md.
 * Ablation config: io.yukti.bench.penalty (strict/soft), io.yukti.bench.credits (on/off). See docs/reproducibility.md.
 */
public class BenchmarkHarness {
    private static final String BENCH_VERSION = "v2";
    private static final int EXPECTED_PROFILE_COUNT_V1 = 50;
    private static final int EXPECTED_PROFILE_COUNT_V2 = 200;
    private static final int EXPECTED_MONTHLY_COUNT = 10;

    private final Catalog catalog;
    private final Optimizer optimizer;
    private final List<BenchProfile> profiles;
    private final boolean penaltyStrict;
    private final boolean creditsInObjective;
    private final boolean unifiedValuation;
    private final UserConstraints constraintsOverride;

    public BenchmarkHarness() throws Exception {
        this(readPenaltyStrict(), readCreditsInObjective());
    }

    /** @param penaltyStrict true = strict (non-primary 0.6), false = soft (0.85). */
    public BenchmarkHarness(boolean penaltyStrict, boolean creditsInObjective) throws Exception {
        this(penaltyStrict, creditsInObjective, readOptimizerId());
    }

    /** @param optimizerId e.g. "cap-aware-greedy-v1" or "greedy-v1". */
    public BenchmarkHarness(boolean penaltyStrict, boolean creditsInObjective, String optimizerId) throws Exception {
        this(penaltyStrict, creditsInObjective, optimizerId, readUnifiedValuation());
    }

    /** @param unifiedValuation when true, use same valuation (CASHBACK) for all three goals so portfolios are identical → goal-sensitivity 0% (sanity baseline). */
    public BenchmarkHarness(boolean penaltyStrict, boolean creditsInObjective, String optimizerId, boolean unifiedValuation) throws Exception {
        this(penaltyStrict, creditsInObjective, optimizerId, unifiedValuation, null);
    }

    /** Full constructor with optional constraints override for sensitivity sweeps. */
    public BenchmarkHarness(boolean penaltyStrict, boolean creditsInObjective, String optimizerId,
                            boolean unifiedValuation, UserConstraints constraintsOverride) throws Exception {
        this.penaltyStrict = penaltyStrict;
        this.creditsInObjective = creditsInObjective;
        this.unifiedValuation = unifiedValuation;
        this.constraintsOverride = constraintsOverride;
        PenaltyPolicyV1.setStrictPenalty(penaltyStrict);
        BenchRunConfig.setCreditsInObjective(creditsInObjective);
        catalog = loadCatalog();
        optimizer = new OptimizerRegistry().get(optimizerId);
        String version = System.getProperty("io.yukti.bench.version", "v2");
        profiles = "v1".equals(version) ? createProfiles50() : createProfilesV2();
        String defaultIdsFile = "v1".equals(version) ? "profile_ids_v1.json" : "profile_ids_v2.json";
        Path profileIdsPath = System.getProperty("io.yukti.bench.profileIdsPath") != null
                ? Path.of(System.getProperty("io.yukti.bench.profileIdsPath"))
                : Path.of("docs", "bench", defaultIdsFile);
        int expectedCount = "v1".equals(version) ? EXPECTED_PROFILE_COUNT_V1 : EXPECTED_PROFILE_COUNT_V2;
        ProfileIdsLoader.failFastIfMismatch(profileIdsPath, getProfileIds(), expectedCount);
    }

    /** Constructor with custom profile list (e.g. BLS-derived profiles). Skips profile ID validation. */
    public BenchmarkHarness(boolean penaltyStrict, boolean creditsInObjective, String optimizerId,
                            boolean unifiedValuation, UserConstraints constraintsOverride,
                            List<BenchProfile> customProfiles) throws Exception {
        this.penaltyStrict = penaltyStrict;
        this.creditsInObjective = creditsInObjective;
        this.unifiedValuation = unifiedValuation;
        this.constraintsOverride = constraintsOverride;
        PenaltyPolicyV1.setStrictPenalty(penaltyStrict);
        BenchRunConfig.setCreditsInObjective(creditsInObjective);
        catalog = loadCatalog();
        optimizer = new OptimizerRegistry().get(optimizerId);
        profiles = List.copyOf(customProfiles);
    }

    /** Load catalog: from file if io.yukti.bench.catalogPath is set (e.g. derived catalog §6.5), else classpath catalog-v1.json. */
    private static Catalog loadCatalog() throws Exception {
        String pathProp = System.getProperty("io.yukti.bench.catalogPath");
        if (pathProp != null && !pathProp.isBlank()) {
            Path p = Path.of(pathProp);
            if (Files.isRegularFile(p)) {
                try (InputStream in = Files.newInputStream(p)) {
                    return new JsonCatalogParser().parse(in);
                }
            }
            throw new IllegalStateException("Catalog path not found: " + pathProp);
        }
        return new ClasspathCatalogSource("catalog/catalog-v1.json").load("1.0");
    }

    /** RewardsBench v1 goal per run: CASHBACK/FLEX_POINTS no primary; PROGRAM_POINTS primary = AVIOS (paper §2.3.1, §6). */
    private static UserGoal goalForBench(GoalType goal) {
        if (goal == GoalType.PROGRAM_POINTS) {
            return new UserGoal(GoalType.PROGRAM_POINTS, Optional.of(RewardCurrencyType.AVIOS), List.of(), Map.of());
        }
        return UserGoal.of(goal);
    }

    private static boolean readUnifiedValuation() {
        String v = System.getProperty("io.yukti.bench.unifiedValuation", "false");
        return "true".equalsIgnoreCase(v);
    }

    private static String readOptimizerId() {
        String v = System.getProperty("io.yukti.bench.optimizer", "milp-v1");
        return v != null && !v.isBlank() ? v : "milp-v1";
    }

    private static boolean readPenaltyStrict() {
        String v = System.getProperty("io.yukti.bench.penalty", "strict");
        return !"soft".equalsIgnoreCase(v);
    }

    private static boolean readCreditsInObjective() {
        String v = System.getProperty("io.yukti.bench.credits", "on");
        return !"off".equalsIgnoreCase(v);
    }

    /**
     * Exactly 50 profiles: 6 baselines, 10 category-dominant, 20 cap-boundary, 4 goal-discriminator, 10 monthly.
     * Stable insertion order; IDs are unique, kebab-case. See docs/bench/profiles.md.
     */
    private List<BenchProfile> createProfiles50() {
        List<BenchProfile> list = new ArrayList<>(50);

        // A) Baselines (6 annual)
        list.add(new BenchProfile("light", false, Map.of(Category.GROCERIES, 3000., Category.DINING, 1500., Category.GAS, 1200., Category.OTHER, 2500.)));
        list.add(new BenchProfile("moderate", false, Map.of(Category.GROCERIES, 6000., Category.DINING, 3000., Category.GAS, 2400., Category.TRAVEL, 2000., Category.ONLINE, 1200., Category.OTHER, 5000.)));
        list.add(new BenchProfile("heavy", false, Map.of(Category.GROCERIES, 15000., Category.DINING, 6000., Category.GAS, 3600., Category.TRAVEL, 8000., Category.ONLINE, 3000., Category.OTHER, 10000.)));
        list.add(new BenchProfile("balanced", false, Map.of(Category.GROCERIES, 5000., Category.DINING, 2500., Category.GAS, 2000., Category.TRAVEL, 2500., Category.ONLINE, 1500., Category.OTHER, 4000.)));
        list.add(new BenchProfile("minimal", false, Map.of(Category.OTHER, 5000.)));
        list.add(new BenchProfile("high-net-worth", false, Map.of(Category.GROCERIES, 24000., Category.DINING, 12000., Category.GAS, 6000., Category.TRAVEL, 20000., Category.ONLINE, 6000., Category.OTHER, 20000.)));

        // B) Category-dominant (10 annual)
        list.add(new BenchProfile("grocery-heavy", false, Map.of(Category.GROCERIES, 12000., Category.DINING, 2000., Category.OTHER, 3000.)));
        list.add(new BenchProfile("dining-heavy", false, Map.of(Category.DINING, 10000., Category.GROCERIES, 4000., Category.OTHER, 2000.)));
        list.add(new BenchProfile("travel-heavy", false, Map.of(Category.TRAVEL, 15000., Category.DINING, 4000., Category.OTHER, 5000.)));
        list.add(new BenchProfile("gas-heavy", false, Map.of(Category.GAS, 6000., Category.GROCERIES, 3000., Category.OTHER, 3000.)));
        list.add(new BenchProfile("online-heavy", false, Map.of(Category.ONLINE, 8000., Category.GROCERIES, 3000., Category.OTHER, 4000.)));
        list.add(new BenchProfile("grocery-heavy-other-low", false, Map.of(Category.GROCERIES, 12000., Category.DINING, 2000., Category.OTHER, 1000.)));
        list.add(new BenchProfile("grocery-heavy-other-med", false, Map.of(Category.GROCERIES, 12000., Category.DINING, 2000., Category.OTHER, 5000.)));
        list.add(new BenchProfile("grocery-heavy-other-high", false, Map.of(Category.GROCERIES, 12000., Category.DINING, 2000., Category.OTHER, 12000.)));
        list.add(new BenchProfile("dining-heavy-other-low", false, Map.of(Category.DINING, 10000., Category.GROCERIES, 4000., Category.OTHER, 1000.)));
        list.add(new BenchProfile("travel-heavy-other-low", false, Map.of(Category.TRAVEL, 15000., Category.DINING, 4000., Category.OTHER, 1000.)));

        // C) Cap-boundary (20 annual): 5 caps × 4 (below, at, above, far). OTHER 1000 for non-degenerate selection.
        // Cap 6000 (GROCERIES)
        list.add(new BenchProfile("groceries-cap6000-below", false, Map.of(Category.GROCERIES, 5900., Category.OTHER, 1000.)));
        list.add(new BenchProfile("groceries-cap6000-at", false, Map.of(Category.GROCERIES, 6000., Category.OTHER, 1000.)));
        list.add(new BenchProfile("groceries-cap6000-above", false, Map.of(Category.GROCERIES, 6100., Category.OTHER, 1000.)));
        list.add(new BenchProfile("groceries-cap6000-far", false, Map.of(Category.GROCERIES, 12000., Category.OTHER, 1000.)));
        // Cap 12000 (GROCERIES)
        list.add(new BenchProfile("groceries-cap12000-below", false, Map.of(Category.GROCERIES, 11900., Category.OTHER, 1000.)));
        list.add(new BenchProfile("groceries-cap12000-at", false, Map.of(Category.GROCERIES, 12000., Category.OTHER, 1000.)));
        list.add(new BenchProfile("groceries-cap12000-above", false, Map.of(Category.GROCERIES, 12100., Category.OTHER, 1000.)));
        list.add(new BenchProfile("groceries-cap12000-far", false, Map.of(Category.GROCERIES, 24000., Category.OTHER, 1000.)));
        // Cap 25000 (GROCERIES)
        list.add(new BenchProfile("groceries-cap25000-below", false, Map.of(Category.GROCERIES, 24900., Category.OTHER, 1000.)));
        list.add(new BenchProfile("groceries-cap25000-at", false, Map.of(Category.GROCERIES, 25000., Category.OTHER, 1000.)));
        list.add(new BenchProfile("groceries-cap25000-above", false, Map.of(Category.GROCERIES, 25100., Category.OTHER, 1000.)));
        list.add(new BenchProfile("groceries-cap25000-far", false, Map.of(Category.GROCERIES, 50000., Category.OTHER, 1000.)));
        // Cap 1500 (GROCERIES)
        list.add(new BenchProfile("groceries-cap1500-below", false, Map.of(Category.GROCERIES, 1400., Category.OTHER, 1000.)));
        list.add(new BenchProfile("groceries-cap1500-at", false, Map.of(Category.GROCERIES, 1500., Category.OTHER, 1000.)));
        list.add(new BenchProfile("groceries-cap1500-above", false, Map.of(Category.GROCERIES, 1600., Category.OTHER, 1000.)));
        list.add(new BenchProfile("groceries-cap1500-far", false, Map.of(Category.GROCERIES, 3000., Category.OTHER, 1000.)));
        // Cap 2000 (ONLINE)
        list.add(new BenchProfile("online-cap2000-below", false, Map.of(Category.ONLINE, 1900., Category.OTHER, 1000.)));
        list.add(new BenchProfile("online-cap2000-at", false, Map.of(Category.ONLINE, 2000., Category.OTHER, 1000.)));
        list.add(new BenchProfile("online-cap2000-above", false, Map.of(Category.ONLINE, 2100., Category.OTHER, 1000.)));
        list.add(new BenchProfile("online-cap2000-far", false, Map.of(Category.ONLINE, 4000., Category.OTHER, 1000.)));

        // D) Goal-discriminator (4 annual)
        list.add(new BenchProfile("aa-sweetspot", false, Map.of(Category.TRAVEL, 12000., Category.DINING, 6000., Category.GROCERIES, 3000., Category.OTHER, 2000.)));
        list.add(new BenchProfile("flex-sweetspot", false, Map.of(Category.ONLINE, 8000., Category.TRAVEL, 6000., Category.DINING, 4000., Category.OTHER, 3000.)));
        list.add(new BenchProfile("cashback-sweetspot", false, Map.of(Category.GROCERIES, 5000., Category.DINING, 3000., Category.GAS, 2000., Category.TRAVEL, 2000., Category.ONLINE, 1500., Category.OTHER, 4000.)));
        list.add(new BenchProfile("fee-sensitive", false, Map.of(Category.GROCERIES, 6000., Category.DINING, 3000., Category.OTHER, 5000.)));

        // E) Monthly (10)
        list.add(new BenchProfile("monthly-light", true, Map.of(Category.GROCERIES, 400., Category.DINING, 200., Category.OTHER, 300.)));
        list.add(new BenchProfile("monthly-moderate", true, Map.of(Category.GROCERIES, 600., Category.DINING, 350., Category.GAS, 250., Category.TRAVEL, 200., Category.ONLINE, 100., Category.OTHER, 500.)));
        list.add(new BenchProfile("monthly-grocery-cap-below", true, Map.of(Category.GROCERIES, 408., Category.OTHER, 100.)));
        list.add(new BenchProfile("monthly-grocery-cap-at", true, Map.of(Category.GROCERIES, 500., Category.OTHER, 100.)));
        list.add(new BenchProfile("monthly-grocery-cap-above", true, Map.of(Category.GROCERIES, 508., Category.OTHER, 100.)));
        list.add(new BenchProfile("monthly-grocery-cap-far", true, Map.of(Category.GROCERIES, 1000., Category.OTHER, 100.)));
        list.add(new BenchProfile("monthly-travel", true, Map.of(Category.TRAVEL, 1000., Category.OTHER, 200.)));
        list.add(new BenchProfile("monthly-dining", true, Map.of(Category.DINING, 500., Category.OTHER, 200.)));
        list.add(new BenchProfile("monthly-online", true, Map.of(Category.ONLINE, 400., Category.OTHER, 150.)));
        list.add(new BenchProfile("monthly-commuter", true, Map.of(Category.GAS, 350., Category.DINING, 150., Category.OTHER, 200.)));

        assert list.size() == EXPECTED_PROFILE_COUNT_V1 : "createProfiles50 must return exactly " + EXPECTED_PROFILE_COUNT_V1;
        return list;
    }

    /**
     * V2 profiles: 200 total = 50 curated (Set A) + 100 generated (Set B, seed=42) + 50 holdout (Set C, seed=99).
     * Reproducible via seeded RNG in ProfileGenerator.
     */
    private List<BenchProfile> createProfilesV2() {
        List<BenchProfile> list = new ArrayList<>(EXPECTED_PROFILE_COUNT_V2);
        list.addAll(createProfiles50());                                    // Set A: 50 curated
        list.addAll(ProfileGenerator.generate(100, 42, "gen-"));           // Set B: 100 generated
        list.addAll(ProfileGenerator.generate(50, 99, "holdout-"));        // Set C: 50 holdout
        assert list.size() == EXPECTED_PROFILE_COUNT_V2 : "createProfilesV2 must return exactly " + EXPECTED_PROFILE_COUNT_V2;
        return list;
    }

    /** Returns true if the profile is in the holdout set (prefix = "holdout-"). */
    public static boolean isHoldout(BenchProfile p) {
        return p.id().startsWith("holdout-");
    }

    /** Returns the list of profile IDs in stable order (for paper binding). */
    public List<String> getProfileIds() {
        return profiles.stream().map(BenchProfile::id).collect(Collectors.toList());
    }

    private SpendProfile toSpendProfile(BenchProfile p) {
        Map<Category, Money> amounts = new EnumMap<>(Category.class);
        p.spend().forEach((cat, v) -> amounts.put(cat, Money.usd(v)));
        return new SpendProfile(p.monthly() ? Period.MONTHLY : Period.ANNUAL, amounts);
    }

    public List<BenchResult> run() {
        List<BenchResult> results = new ArrayList<>();
        for (BenchProfile p : profiles) {
            SpendProfile profile = toSpendProfile(p);
            for (GoalType goal : List.of(GoalType.CASHBACK, GoalType.FLEX_POINTS, GoalType.PROGRAM_POINTS)) {
                // When unifiedValuation: use same goal (CASHBACK) for every request so all three "goals" get identical portfolios → goal-sensitivity 0%.
                // RewardsBench v1: PROGRAM_POINTS uses AVIOS as primary (paper §2.3.1, §6; catalog v1 has five Avios cards).
                UserGoal requestGoal = unifiedValuation ? UserGoal.of(GoalType.CASHBACK) : goalForBench(goal);
                UserConstraints constraints = constraintsOverride != null ? constraintsOverride : UserConstraints.defaults();
                OptimizationRequest req = new OptimizationRequest(
                    profile,
                    requestGoal,
                    constraints,
                    Map.of()
                );
                long start = System.nanoTime();
                OptimizationResult r = optimizer.optimize(req, catalog);
                long elapsed = System.nanoTime() - start;
                // Capture LP bound immediately after solve (before next call overwrites it)
                double lpBound = Double.NaN;
                if (optimizer instanceof LpRelaxationOptimizer lpOpt) {
                    lpBound = lpOpt.getLastLpBound();
                }
                results.add(new BenchResult(p.id(), goal, r, elapsed, lpBound));
            }
        }
        return results;
    }

    public void printReport(java.io.PrintStream out) {
        for (BenchResult r : run()) {
            out.printf("%s | %s | Net=%s | %d μs%n",
                r.profileId(), r.goal(), r.result().getBreakdown().getNet(), r.elapsedNs() / 1000);
        }
    }

    /**
     * Writes bench results as canonical JSON for paper figures and reproducibility.
     * Uses RunStamp from yukti-core and CanonicalJsonWriter (stable key order, stable number formatting).
     * Experiment config path: docs/paper/experiment_config_v1.json (relative to working dir). Also writes catalog-cap-summary.json.
     */
    public void writeResultsJson(Path outputPath) throws Exception {
        List<BenchResult> results = run();
        List<String> profileIds = getProfileIds();
        List<String> sortedIds = new ArrayList<>(profileIds);
        Collections.sort(sortedIds);

        String catalogBundleSha256 = sha256Resource("catalog/catalog-v1.json");
        String valuationConfigSha256 = sha256Resource("valuation/default-cpp.v1.json");
        String profileSetId = ConfigHash.sha256OfUtf8(new ObjectMapper().writeValueAsString(sortedIds));
        Path experimentConfigPath = Path.of("docs", "paper", "experiment_config_v1.json");

        RunStamp runStamp = RunStampBuilder.build(
                BENCH_VERSION,
                catalog.version(),
                catalogBundleSha256,
                optimizer.id(),
                valuationConfigSha256,
                penaltyStrict ? "strict" : "soft",
                creditsInObjective ? "on" : "off",
                profileSetId,
                profiles.size(),
                sortedIds,
                experimentConfigPath.toFile().exists() ? experimentConfigPath : null
        );

        // Results sorted by profileId then goalType (already in order from run(); sort explicitly for stability)
        results.sort(Comparator.comparing(BenchResult::profileId).thenComparing(r -> r.goal().name()));

        List<Map<String, Object>> resultsList = new ArrayList<>();
        for (BenchResult r : results) {
            ObjectiveBreakdown b = r.result().getBreakdown();
            Map<String, Object> entry = new TreeMap<>();
            entry.put("profileId", r.profileId());
            entry.put("goalType", r.goal().name());
            List<String> portfolio = new ArrayList<>(r.result().getPortfolioIds());
            Collections.sort(portfolio);
            entry.put("portfolio", portfolio);
            List<Map<String, String>> allocationSegments = new ArrayList<>();
            r.result().getAllocation().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing(Enum::name)))
                    .forEach(e -> allocationSegments.add(Map.of("category", e.getKey().name(), "cardId", e.getValue())));
            entry.put("allocationSegments", allocationSegments);
            Map<String, Object> breakdown = new TreeMap<>();
            breakdown.put("earnUsd", b.getEarnValue().getAmount().doubleValue());
            breakdown.put("creditsUsd", b.getCreditsValue().getAmount().doubleValue());
            breakdown.put("feesUsd", b.getFees().getAmount().doubleValue());
            breakdown.put("netUsd", b.getNet().getAmount().doubleValue());
            entry.put("breakdown", breakdown);
            entry.put("netValueUsd", b.getNet().getAmount().doubleValue());
            if (!Double.isNaN(r.lpBoundUsd())) {
                entry.put("lpBoundUsd", r.lpBoundUsd());
            }
            List<Map<String, String>> evidenceSummary = new ArrayList<>();
            for (EvidenceBlock eb : r.result().getEvidenceBlocks()) {
                evidenceSummary.add(new TreeMap<>(Map.of("type", eb.getType(), "cardId", eb.getCardId(), "category", eb.getCategory())));
            }
            evidenceSummary.sort(Comparator
                    .comparing((Map<String, String> m) -> m.get("type"))
                    .thenComparing(m -> m.get("cardId"))
                    .thenComparing(m -> m.get("category")));
            entry.put("evidenceSummary", evidenceSummary);
            EvidenceGraph graph = new EvidenceGraphBuilder().build(r.result());
            entry.put("evidenceGraphDigest", graph.getDigest());
            entry.put("evidenceIds", graph.getEvidenceIds());
            // Explanation + verification for reproducibility metrics
            StructuredExplanationBuilder structuredBuilder = new StructuredExplanationBuilder();
            StructuredExplanation structured = structuredBuilder.build(
                r.result(), catalog.version(), r.goal(), null);
            DeterministicExplanationGeneratorV1 detGen = new DeterministicExplanationGeneratorV1();
            NarrativeExplanation explanation = detGen.generate(structured);
            entry.put("claimsDigest", ClaimsDigest.compute(explanation.claims()));
            entry.put("claimCount", explanation.claims().size());
            ClaimVerifier verifier = new ClaimVerifier();
            long verifyStartNs = System.nanoTime();
            boolean verificationPass = verifier.verify(graph, explanation.claims()).passed();
            long verificationTimeMs = (System.nanoTime() - verifyStartNs) / 1_000_000;
            entry.put("verificationPass", verificationPass);
            entry.put("verificationTimeMs", verificationTimeMs);
            entry.put("elapsedNs", r.elapsedNs());
            resultsList.add(entry);
        }

        Map<String, Object> root = new TreeMap<>();
        root.put("runStamp", runStamp.toMap());
        root.put("benchVersion", BENCH_VERSION);
        root.put("catalogVersion", catalog.version());
        root.put("optimizerId", optimizer.id());
        root.put("profileCount", profiles.size());
        root.put("profileIds", sortedIds);
        root.put("goalTypes", List.of("CASHBACK", "FLEX_POINTS", "PROGRAM_POINTS"));
        root.put("penaltyStrict", penaltyStrict);
        root.put("creditsInObjective", creditsInObjective);
        root.put("unifiedValuation", unifiedValuation);
        root.put("results", resultsList);

        Files.createDirectories(outputPath.getParent());
        CanonicalJsonWriter.writePretty(outputPath, root);

        Path certPath = outputPath.getParent().resolve("reproducibility_certificate.json");
        DefaultCertificateGenerator.writeCertificate(outputPath, certPath);

        CatalogCapSummary.write(catalog, outputPath.getParent());
    }

    private static String sha256Resource(String resourcePath) {
        try (InputStream in = BenchmarkHarness.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) return "";
            return ConfigHash.sha256Hex(in.readAllBytes());
        } catch (Exception e) {
            return "";
        }
    }

    public record BenchProfile(String id, boolean monthly, Map<Category, Double> spend) {}
    public record BenchResult(String profileId, GoalType goal, OptimizationResult result, long elapsedNs, double lpBoundUsd) {
        /** Backward-compatible constructor for non-LP optimizers. */
        public BenchResult(String profileId, GoalType goal, OptimizationResult result, long elapsedNs) {
            this(profileId, goal, result, elapsedNs, Double.NaN);
        }
    }

    public static void main(String[] args) throws Exception {
        BenchmarkHarness harness = new BenchmarkHarness();
        if (args.length > 0) {
            Path out = Path.of(args[0]);
            harness.writeResultsJson(out);
            System.out.println("Wrote bench results to " + out + " (" + harness.profiles.size() + " profiles × 3 goals; penaltyStrict=" + harness.penaltyStrict + ", creditsInObjective=" + harness.creditsInObjective + ", unifiedValuation=" + harness.unifiedValuation + ")");
        } else {
            harness.printReport(System.out);
        }
    }
}
