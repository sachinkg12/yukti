plugins {
    base
}

// Vite + React build: npm install && npm run build → dist/
tasks.register<Copy>("copyWeb") {
    dependsOn("npmRunBuild")
    from("$projectDir/dist")
    into("$buildDir/web")
}

tasks.register<Exec>("npmInstall") {
    group = "build"
    workingDir = projectDir
    commandLine("npm", "install", "--prefer-offline", "--no-audit")
    inputs.file("$projectDir/package.json")
    outputs.dir("$projectDir/node_modules")
}

tasks.register<Exec>("npmRunBuild") {
    group = "build"
    dependsOn("npmInstall")
    workingDir = projectDir
    commandLine("npm", "run", "build")
    inputs.dir("$projectDir/src")
    inputs.file("$projectDir/package.json")
    inputs.file("$projectDir/vite.config.ts")
    outputs.dir("$projectDir/dist")
}
