plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
    alias(libs.plugins.dokka)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

ktlint {
    filter {
        exclude("**/generated/**")
        exclude("**/build/**")
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    source.setFrom("src/main/kotlin")
}

val statementCoverage = readKotlinThreshold("statement_coverage")
val branchCoverage = readKotlinThreshold("branch_coverage")

kover {
    reports {
        verify {
            rule("statement coverage") {
                bound {
                    coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE
                    minValue = statementCoverage
                }
            }
            rule("branch coverage") {
                bound {
                    coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.BRANCH
                    minValue = branchCoverage
                }
            }
        }
    }
}

fun readKotlinThreshold(key: String): Int {
    // Committed consumer copy so CI can fail-closed without cloning private analog (LIM-SUB).
    val file = rootProject.file("config/kotlin.profile.thresholds.yml")
    require(file.isFile) { "Missing ${file.path} (CI-022 fail closed)" }
    val line =
        file.readLines()
            .map { it.substringBefore('#').trim() }
            .firstOrNull { it.startsWith("$key:") }
            ?: error("Missing key '$key' in ${file.path} (CI-022 fail closed)")
    val raw = line.substringAfter(':').trim()
    require(raw.isNotEmpty()) { "Empty key '$key' in ${file.path} (CI-022 fail closed)" }
    return raw.toInt()
}
