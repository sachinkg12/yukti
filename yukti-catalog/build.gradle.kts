plugins {
    java
}

dependencies {
    implementation(project(":yukti-core"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
}

val generateCatalogBundle by tasks.registering(JavaExec::class) {
    group = "catalog"
    description = "Generate catalog-v1.json from v1 index + per-card JSON (single source of truth)"
    dependsOn(tasks.compileJava)
    classpath = sourceSets.main.get().compileClasspath + sourceSets.main.get().output.classesDirs
    mainClass.set("io.yukti.catalog.CatalogBundleGenerator")
    val v1Dir = layout.projectDirectory.dir("src/main/resources/catalog/v1").asFile.absolutePath
    val outFile = layout.projectDirectory.dir("src/main/resources/catalog").file("catalog-v1.json").asFile.absolutePath
    args(v1Dir, outFile)
}

tasks.named("processResources") {
    dependsOn(generateCatalogBundle)
}
