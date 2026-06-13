// Root aggregator; optional frontend build for prod-like packaging.

tasks.register<Exec>("frontendBuild") {
    group = "frontend"
    description = "Build the Vite frontend (requires npm install in frontend/)"
    workingDir = file("frontend")
    commandLine("npm", "run", "build")
}

tasks.register<Copy>("copyFrontend") {
    group = "frontend"
    description = "Copy frontend dist into backend static resources"
    dependsOn("frontendBuild")
    from("frontend/dist")
    into("backend/src/main/resources/static")
}