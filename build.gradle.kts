plugins {
    kotlin("jvm") version "2.0.21" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
}

fun runPnpm(dashboardDir: java.io.File, vararg args: String) {
    val proc = ProcessBuilder("pnpm", *args)
        .directory(dashboardDir)
        .inheritIO()
        .start()
    val exit = proc.waitFor()
    if (exit != 0) throw GradleException("pnpm ${args.joinToString(" ")} failed with exit code $exit")
}

/** Run the full Yawn Doctor report pipeline: analyze → convert → verify */
tasks.register("yawnDoctorReport") {
    dependsOn(":demo-codebase:detektMain")
    description = "Generate SARIF, convert to findings.json, verify, and build dashboard"

    val dashboardDir = rootProject.file("dashboard")
    doLast {
        if (!dashboardDir.resolve("node_modules").exists()) {
            logger.warn("dashboard/node_modules not found — running pnpm install")
            runPnpm(dashboardDir, "install")
        }
        runPnpm(dashboardDir, "report")
        runPnpm(dashboardDir, "verify-report")
        runPnpm(dashboardDir, "build")
    }
}

/** Start the dashboard dev server */
tasks.register("yawnDoctorDashboard") {
    description = "Start the dashboard dev server at http://localhost:3000"

    val dashboardDir = rootProject.file("dashboard")
    doLast {
        if (!dashboardDir.resolve("node_modules").exists()) {
            logger.warn("dashboard/node_modules not found — running pnpm install")
            runPnpm(dashboardDir, "install")
        }
        println("\n  Yawn Doctor dashboard at http://localhost:3000\n")
        runPnpm(dashboardDir, "dev")
    }
}

/** Convert SARIF to findings.json (requires detektMain first) */
tasks.register("yawnDoctorConvert") {
    description = "Convert the latest SARIF report to findings.json"

    val dashboardDir = rootProject.file("dashboard")
    doLast {
        if (!dashboardDir.resolve("node_modules").exists()) {
            runPnpm(dashboardDir, "install")
        }
        runPnpm(dashboardDir, "report")
    }
}
