plugins {
    kotlin("jvm") version "2.0.21" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
}

/** Run the full Yawn Doctor report pipeline: analyze → convert → verify */
tasks.register("yawnDoctorReport") {
    dependsOn(":demo-codebase:detektMain")
    description = "Generate SARIF, convert to findings.json, and verify"

    doLast {
        val dashboardDir = rootProject.file("dashboard")
        if (!dashboardDir.resolve("node_modules").exists()) {
            logger.warn("dashboard/node_modules not found — running pnpm install")
            exec {
                workingDir = dashboardDir
                commandLine("pnpm", "install")
            }
        }

        exec { workingDir = dashboardDir; commandLine("pnpm", "report") }
        exec { workingDir = dashboardDir; commandLine("pnpm", "verify-report") }
        exec { workingDir = dashboardDir; commandLine("pnpm", "build") }
    }
}
