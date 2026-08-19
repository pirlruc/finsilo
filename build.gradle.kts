plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.cyclonedx)
}

allprojects {
    tasks.named<org.cyclonedx.gradle.CyclonedxDirectTask>("cyclonedxDirectBom") {
        includeConfigs = listOf("runtimeClasspath", "debugRuntimeClasspath", "releaseRuntimeClasspath")
        skipConfigs = listOf("(?i).*test.*")
    }
}
