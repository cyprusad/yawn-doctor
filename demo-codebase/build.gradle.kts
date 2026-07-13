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
    reports {
        sarif.required.set(true)
    }
}
