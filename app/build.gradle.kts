plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

room {
    schemaDirectory("$projectDir/schemas")
}

android {
    namespace = "com.pirlruc.finsilo"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.pirlruc.finsilo"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val releaseStore = System.getenv("FINSILO_KEYSTORE_FILE")
    signingConfigs {
        create("release") {
            if (!releaseStore.isNullOrBlank()) {
                storeFile = file(releaseStore)
                storePassword = System.getenv("FINSILO_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("FINSILO_KEY_ALIAS")
                keyPassword = System.getenv("FINSILO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Fail closed: a release APK is signed only when the keystore env is set.
            signingConfig = if (releaseStore.isNullOrBlank()) null else signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    sourceSets.named("test") {
        assets.directories.add("$projectDir/schemas")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.google.material)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
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
    val file = rootProject.file("config/kotlin.profile.thresholds.yml")
    require(file.isFile) { "Missing ${file.path}" }
    val line = file.readLines().first { it.trimStart().startsWith("$key:") }
    return line.substringAfter(":").substringBefore("#").trim().toInt()
}

ktlint {
    android.set(true)
    filter {
        exclude("**/generated/**")
        exclude("**/build/**")
    }
}

detekt {
    buildUponDefaultConfig.set(true)
    allRules.set(false)
    // Preserve 1.x maxIssues: 0 (fail on Warning+, not only Error).
    failOnSeverity.set(dev.detekt.gradle.extensions.FailOnSeverity.Warning)
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    source.setFrom("src/main/java")
    reportsDir.set(
        extensions.getByType<org.gradle.api.reporting.ReportingExtension>().baseDirectory.dir("detekt"),
    )
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    jvmTarget.set("17")
}
