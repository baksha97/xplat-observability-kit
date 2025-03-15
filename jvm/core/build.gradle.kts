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
//    implementation("io.opentelemetry.android:android-agent:0.10.0-alpha")
    implementation("io.opentelemetry:opentelemetry-sdk:1.48.0")
    implementation("io.opentelemetry:opentelemetry-extension-kotlin:1.48.0")

    implementation(libs.opentelemetry.api.incubator)
    implementation(libs.opentelemetry.exporter.otlp)
    implementation("io.opentelemetry:opentelemetry-exporter-jaeger:1.25.0")
    implementation("io.opentelemetry:opentelemetry-semconv:1.25.0-alpha")

    kspTest(project(":core"))
    testImplementation(libs.mockk)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

kotlin {
//    explicitApi = ExplicitApiMode.Strict
    sourceSets.all {
        languageSettings {
            optIn("kotlin.ExperimentalStdlibApi")
        }
    }
    compilerOptions {
        freeCompilerArgs = listOf("-Xopt-in=kotlin.time.ExperimentalTime", "-Xcontext-receivers")
    }
}
