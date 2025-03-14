import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode

plugins {
    alias(libs.plugins.kotlin.jvm)
    // Needed for test - otherwise wouldn't be needed.
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.ksp.symbol.processing.api)
    implementation(libs.kotlin.poet)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

    kspTest(project(":core"))
    testImplementation(libs.mockk)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

kotlin {
    explicitApi = ExplicitApiMode.Strict
    sourceSets.all {
        languageSettings {
            optIn("kotlin.ExperimentalStdlibApi")
        }
    }
    compilerOptions {
        freeCompilerArgs = listOf("-Xopt-in=kotlin.time.ExperimentalTime", "-Xcontext-receivers")
    }
}
