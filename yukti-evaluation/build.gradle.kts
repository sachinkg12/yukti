plugins {
    java
}

dependencies {
    implementation(project(":yukti-explain-core"))
    implementation(project(":yukti-core"))
    implementation(project(":yukti-engine"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}
