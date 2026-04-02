import org.gradle.api.plugins.JavaPluginExtension

// Root: aggregator only. No source.
group = "io.yukti"
version = "0.1.0-SNAPSHOT"

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    tasks.named<Test>("test") {
        useJUnitPlatform()
        maxParallelForks = (Runtime.getRuntime().availableProcessors().takeIf { it > 0 } ?: 2).coerceIn(1, 4)
    }

    dependencies {
        add("testImplementation", platform("org.junit:junit-bom:5.11.4"))
        add("testImplementation", "org.junit.jupiter:junit-jupiter")
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
    }
}
