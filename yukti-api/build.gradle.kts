plugins {
    java
    application
}

application {
    mainClass.set("io.yukti.api.server.LocalServer")
}

tasks.register<JavaExec>("runServer") {
    group = "application"
    description = "Run local HTTP server on port 18000"
    mainClass.set("io.yukti.api.server.LocalServer")
    classpath = sourceSets.main.get().runtimeClasspath
}

dependencies {
    implementation(project(":yukti-core"))
    implementation(project(":yukti-engine"))
    implementation(project(":yukti-catalog"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.amazonaws:aws-lambda-java-core:1.2.3")
    implementation("com.amazonaws:aws-lambda-java-events:3.11.3")
}
