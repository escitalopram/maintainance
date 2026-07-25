// Root aggregator; optional frontend build for prod-like packaging.

tasks.register<Exec>("frontendBuild") {
    group = "frontend"
    description = "Build the Vite frontend (requires npm install in frontend/)"
    workingDir = file("frontend")
    commandLine("npm", "run", "build")
}

tasks.register<Copy>("copyFrontend") {
    group = "frontend"
    description = "Copy frontend dist into backend generated resources"
    dependsOn("frontendBuild")
    from("frontend/dist")
    into("backend/build/generated/frontend-static")
}