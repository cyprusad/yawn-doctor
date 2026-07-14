plugins {
    kotlin("jvm") version "2.0.21" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
}

/** Run the full Yawn Doctor report pipeline: analyze → convert → verify */
tasks.register("yawnDoctorReport") {
    dependsOn(":demo-codebase:detektMain")
    description = "Generate SARIF, convert to findings.json, verify, and build dashboard"

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

/** Start the dashboard dev server */
tasks.register("yawnDoctorDashboard") {
    description = "Start the dashboard dev server at http://localhost:3000"

    doLast {
        val dashboardDir = rootProject.file("dashboard")
        if (!dashboardDir.resolve("node_modules").exists()) {
            logger.warn("dashboard/node_modules not found — running pnpm install")
            exec {
                workingDir = dashboardDir
                commandLine("pnpm", "install")
            }
        }

        exec {
            workingDir = dashboardDir
            commandLine("pnpm", "dev")
        }
    }
}

/** Convert SARIF to findings.json (requires detektMain first) */
tasks.register("yawnDoctorConvert") {
    description = "Convert the latest SARIF report to findings.json"

    doLast {
        val dashboardDir = rootProject.file("dashboard")
        if (!dashboardDir.resolve("node_modules").exists()) {
            exec {
                workingDir = dashboardDir
                commandLine("pnpm", "install")
            }
        }
        exec { workingDir = dashboardDir; commandLine("pnpm", "report") }
    }
}
