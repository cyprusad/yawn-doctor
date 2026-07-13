plugins {
    kotlin("jvm")
    id("io.gitlab.arturbosch.detekt")
}

group = "demo"

dependencies {
    detektPlugins(project(":doctor-rules"))
}

kotlin {
    jvmToolchain(17)
}

detekt {
    toolVersion = "1.23.8"
    config.setFrom(file("${rootDir}/detekt.yml"))
    buildUponDefaultConfig = true
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    ignoreFailures = true
    reports {
        txt.required.set(true)
        sarif.required.set(true)
    }
}

/** ANSI colour helpers */
val c = mapOf(
    "r" to "\u001B[0m",    // reset
    "b" to "\u001B[1m",    // bold
    "d" to "\u001B[2m",    // dim
    "cy" to "\u001B[36m",  // cyan
    "mg" to "\u001B[35m",  // magenta
    "rd" to "\u001B[31m",  // red
    "yl" to "\u001B[33m",  // yellow
    "gn" to "\u001B[32m",  // green
)

/** YAWN rule IDs and severities keyed by rule class name */
val yawnIds = mapOf(
    "QueryInsideLoop" to "YAWN001",
    "MaterializedCount" to "YAWN002",
    "ExternalCallInsideTransaction" to "YAWN003",
)
val yawnSeverities = mapOf(
    "QueryInsideLoop" to "HIGH",
    "MaterializedCount" to "HIGH",
    "ExternalCallInsideTransaction" to "HIGH",
)

/** Colourise findings in a rustc-like style */
tasks.register("yawnDoctorDemo") {
    dependsOn(tasks.named("detektMain"))
    description = "Run detekt with Yawn Doctor and display findings with ANSI colouring"

    doLast {
        val report = layout.buildDirectory.file("reports/detekt/main.txt").get().asFile
        if (!report.exists()) {
            println("${c["gn"]}${c["b"]}OK\u001B[0m — no findings")
            return@doLast
        }

        var count = 0
        // Format: RuleName - [entity] at /path/File.kt:line:col - Signature=Class.kt$Class$method(args)
        val findingPat = Regex("""^(\w+) - \[([^\]]+)\] at (.+\.kt):(\d+):(\d+) - Signature=(?:\w+\.kt\$)?(\w+(?:\.\w+)*)\$?(.*)""")

        for (line in report.readLines()) {
            val m = findingPat.find(line) ?: continue
            val (ruleName, entity, file, ln, col, cls, call) = m.destructured
            val id = yawnIds[ruleName] ?: continue
            count++
            val sc = c["rd"]
            // Derive a display name: use actual entity name when available,
            // fall back to the signature so lambdas show "Class.method()" instead
            // of "<anonymous>".
            val display = if (entity == "<anonymous>") "$cls.$call" else entity
            println("${c["cy"]}${c["b"]}$file${c["yl"]}:$ln:$col${c["r"]}")
            println("  ${c["d"]}|${c["r"]}")
            println("  ${c["d"]}|${c["r"]} $sc${c["b"]}[$id]${c["r"]} $sc${ruleName} in `$display`${c["r"]}")
            println("  ${c["d"]}|${c["r"]}")
        }

        if (count == 0) {
            println("${c["gn"]}${c["b"]}OK${c["r"]} — no Yawn Doctor findings")
        } else {
            throw GradleException("Found $count Yawn Doctor violation(s). See above.")
        }
    }
}
