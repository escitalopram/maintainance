// Root aggregator; optional frontend build for prod-like packaging.

tasks.register<Delete>("cleanStaleFrontend") {
    group = "frontend"
    description = "Remove legacy frontend files copied into src/main/resources/static"
    delete("backend/src/main/resources/static")
}

tasks.register<Exec>("frontendBuild") {
    group = "frontend"
    description = "Build the Vite frontend (requires npm install in frontend/)"
    workingDir = file("frontend")
    commandLine("npm", "run", "build")
    inputs.dir("frontend/src")
    inputs.files(
        "frontend/package.json",
        "frontend/package-lock.json",
        "frontend/index.html",
        "frontend/vite.config.ts",
        "frontend/tsconfig.json",
        "frontend/tsconfig.app.json",
        "frontend/tsconfig.node.json",
    )
    outputs.dir("frontend/dist")
}

tasks.register<Copy>("copyFrontend") {
    group = "frontend"
    description = "Copy frontend dist into backend static resources (classpath:/static/)"
    dependsOn("cleanStaleFrontend", "frontendBuild")
    from("frontend/dist")
    into("backend/build/generated/resources/static")
}
