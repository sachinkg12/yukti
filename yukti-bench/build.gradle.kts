plugins {
    java
    application
}

application {
    mainClass.set("io.yukti.bench.BenchmarkHarness")
}

dependencies {
    implementation(project(":yukti-explain-core"))
    implementation(project(":yukti-core"))
    implementation(project(":yukti-catalog"))
    implementation(project(":yukti-engine"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
}

// Pass -Dio.yukti.bench.* to the JVM for ablation runs (e.g. -Dio.yukti.bench.penalty=soft -Dio.yukti.bench.credits=off)
// Use repo root as workingDir so --args="artifacts/bench/v1/ablation/foo.json" writes to repo root, not yukti-bench/
tasks.withType<JavaExec>().configureEach {
    workingDir = rootProject.layout.projectDirectory.asFile
    val props = System.getProperties().filterKeys { it is String && (it as String).startsWith("io.yukti.") }
    systemProperties(props.mapKeys { it.key.toString() })
}

// Do NOT force io.yukti.bench.version in tests — let individual tests set their own version
tasks.withType<Test>().configureEach {
    workingDir = rootProject.layout.projectDirectory.asFile
}

// Reproducibility: run bench and write JSON to artifacts/bench/v1/bench_results.json (paper-bound profile set)
tasks.register<JavaExec>("runBench") {
    group = "bench"
    description = "Run RewardsBench v1 and write results to artifacts/bench/v1/bench_results.json"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.yukti.bench.BenchmarkHarness")
    val outFile = rootProject.file("artifacts/bench/v1/bench_results.json")
    args(outFile.absolutePath)
    doFirst {
        outFile.parentFile.mkdirs()
    }
}

// Sanity baseline: unified valuation (same cpp/α for all goals) → goal-sensitivity 0%. Paper §6.2.
tasks.register<JavaExec>("runBenchUnified") {
    group = "bench"
    description = "Run RewardsBench v1 with unified valuation (sanity baseline); writes bench-results-unified.json. Goal-sensitivity should be 0%."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.yukti.bench.BenchmarkHarness")
    systemProperty("io.yukti.bench.unifiedValuation", "true")
    val outFile = rootProject.file("artifacts/bench/v1/bench_results_unified.json")
    args(outFile.absolutePath)
    doFirst {
        outFile.parentFile.mkdirs()
    }
}

// Export profiles to JSON for MILP baseline
tasks.register<JavaExec>("exportProfiles") {
    group = "bench"
    description = "Export RewardsBench v1 profiles to docs/bench/profiles_v1.json"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.yukti.bench.ProfileExporter")
    args(rootProject.file("docs/bench/profiles_v1.json").absolutePath)
    doFirst {
        rootProject.file("docs/bench").mkdirs()
    }
}

// Sensitivity sweep: CPP perturbation, card count, fee budget variations
tasks.register<JavaExec>("runSensitivitySweep") {
    group = "bench"
    description = "Run sensitivity sweeps (CPP, card count, fee budget) for robustness analysis"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.yukti.bench.SensitivitySweepRunner")
    val outDir = rootProject.file("artifacts/bench/v1/sensitivity")
    args(outDir.absolutePath)
    doFirst {
        outDir.mkdirs()
    }
}

// AHP weight sensitivity sweep for paper Table VII-C
tasks.register<JavaExec>("runAhpWeightSweep") {
    group = "bench"
    description = "Run AHP weight sensitivity sweep (default, fee-heavy, coverage-heavy)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.yukti.bench.AhpWeightSweepRunner")
    val outDir = rootProject.file("artifacts/bench/v2/sensitivity")
    args(outDir.absolutePath)
    doFirst {
        outDir.mkdirs()
    }
}

// Run all solvers (cap-aware-greedy, greedy, single-best-per-category) for solver comparison
tasks.register<JavaExec>("runBenchAllSolvers") {
    group = "bench"
    description = "Run RewardsBench v1 with all optimizers; writes bench-results-{optimizerId}.json"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.yukti.bench.MultiSolverBenchmarkRunner")
    val outDir = rootProject.file("artifacts/bench/v1")
    args(outDir.absolutePath)
    doFirst {
        outDir.mkdirs()
    }
}

// === RewardsBench v2 tasks (200 profiles × 11 optimizers) ===

// Full v2 benchmark: all 200 profiles × 11 optimizers
tasks.register<JavaExec>("runBenchV2") {
    group = "bench"
    description = "Run RewardsBench v2 (200 profiles × 11 optimizers) — full evaluation"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.yukti.bench.MultiSolverBenchmarkRunner")
    systemProperty("io.yukti.bench.version", "v2")
    val outDir = rootProject.file("artifacts/bench/v2")
    args(outDir.absolutePath)
    doFirst {
        outDir.mkdirs()
    }
}

// Main evaluation only: 150 profiles (curated + generated, no holdout) × 11 optimizers
tasks.register<JavaExec>("runBenchV2Main") {
    group = "bench"
    description = "Run RewardsBench v2 main evaluation (150 profiles × 11 optimizers)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.yukti.bench.MultiSolverBenchmarkRunner")
    systemProperty("io.yukti.bench.version", "v2")
    systemProperty("io.yukti.bench.subset", "main")
    val outDir = rootProject.file("artifacts/bench/v2")
    args(outDir.absolutePath)
    doFirst {
        outDir.mkdirs()
    }
}

// Holdout only: 50 holdout profiles × 11 optimizers
tasks.register<JavaExec>("runBenchV2Holdout") {
    group = "bench"
    description = "Run RewardsBench v2 holdout evaluation (50 profiles × 11 optimizers)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.yukti.bench.MultiSolverBenchmarkRunner")
    systemProperty("io.yukti.bench.version", "v2")
    systemProperty("io.yukti.bench.subset", "holdout")
    val outDir = rootProject.file("artifacts/bench/v2")
    args(outDir.absolutePath)
    doFirst {
        outDir.mkdirs()
    }
}

// === Ablation runs (MILP only, v2 main profiles, 4 penalty×credits configs) ===

tasks.register<JavaExec>("runAblationStrictCreditsOn") {
    group = "bench"
    description = "Ablation: strict penalty + credits on (default config)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.yukti.bench.BenchmarkHarness")
    systemProperty("io.yukti.bench.version", "v2")
    systemProperty("io.yukti.bench.penalty", "strict")
    systemProperty("io.yukti.bench.credits", "on")
    systemProperty("io.yukti.bench.optimizer", "milp-v1")
    val outFile = rootProject.file("artifacts/bench/v2/ablation/penalty_strict_credits_on.json")
    args(outFile.absolutePath)
    doFirst { outFile.parentFile.mkdirs() }
}

tasks.register<JavaExec>("runAblationStrictCreditsOff") {
    group = "bench"
    description = "Ablation: strict penalty + credits off"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.yukti.bench.BenchmarkHarness")
    systemProperty("io.yukti.bench.version", "v2")
    systemProperty("io.yukti.bench.penalty", "strict")
    systemProperty("io.yukti.bench.credits", "off")
    systemProperty("io.yukti.bench.optimizer", "milp-v1")
    val outFile = rootProject.file("artifacts/bench/v2/ablation/penalty_strict_credits_off.json")
    args(outFile.absolutePath)
    doFirst { outFile.parentFile.mkdirs() }
}

tasks.register<JavaExec>("runAblationSoftCreditsOn") {
    group = "bench"
    description = "Ablation: soft penalty + credits on"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.yukti.bench.BenchmarkHarness")
    systemProperty("io.yukti.bench.version", "v2")
    systemProperty("io.yukti.bench.penalty", "soft")
    systemProperty("io.yukti.bench.credits", "on")
    systemProperty("io.yukti.bench.optimizer", "milp-v1")
    val outFile = rootProject.file("artifacts/bench/v2/ablation/penalty_soft_credits_on.json")
    args(outFile.absolutePath)
    doFirst { outFile.parentFile.mkdirs() }
}

tasks.register<JavaExec>("runAblationSoftCreditsOff") {
    group = "bench"
    description = "Ablation: soft penalty + credits off"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.yukti.bench.BenchmarkHarness")
    systemProperty("io.yukti.bench.version", "v2")
    systemProperty("io.yukti.bench.penalty", "soft")
    systemProperty("io.yukti.bench.credits", "off")
    systemProperty("io.yukti.bench.optimizer", "milp-v1")
    val outFile = rootProject.file("artifacts/bench/v2/ablation/penalty_soft_credits_off.json")
    args(outFile.absolutePath)
    doFirst { outFile.parentFile.mkdirs() }
}

// BLS external validation: 50 BLS-derived profiles × 11 optimizers
tasks.register<JavaExec>("runBenchV2BLS") {
    group = "bench"
    description = "Run RewardsBench v2 BLS external validation (50 BLS profiles × 11 optimizers)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.yukti.bench.BLSBenchmarkRunner")
    val outDir = rootProject.file("artifacts/bench/v2/bls")
    args(outDir.absolutePath)
    doFirst {
        outDir.mkdirs()
    }
}

// Scaling study: solve time vs catalog size × card limit
tasks.register<JavaExec>("runScalingStudy") {
    group = "bench"
    description = "Run scaling study (5 catalog sizes × 4 card limits × 3 optimizers)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.yukti.bench.ScalingStudyRunner")
    val outDir = rootProject.file("artifacts/bench/v2/scaling")
    args(outDir.absolutePath)
    doFirst {
        outDir.mkdirs()
    }
}

// v2 sensitivity sweep: all 11 optimizers × 9 configs
tasks.register<JavaExec>("runSensitivitySweepV2") {
    group = "bench"
    description = "Run v2 sensitivity sweeps (11 optimizers × 9 configs)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.yukti.bench.SensitivitySweepRunner")
    systemProperty("io.yukti.bench.version", "v2")
    val outDir = rootProject.file("artifacts/bench/v2/sensitivity")
    args(outDir.absolutePath)
    doFirst {
        outDir.mkdirs()
    }
}
