plugins {
    java
}

dependencies {
    implementation(project(":yukti-explain-core"))
    implementation(project(":yukti-core"))
    implementation(project(":yukti-catalog"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.google.ortools:ortools-java:9.10.4067")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.2.1")
}

tasks.test {
    systemProperty("writeSnapshots", System.getProperty("writeSnapshots", "false"))
}
