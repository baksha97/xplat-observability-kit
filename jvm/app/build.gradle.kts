plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.coroutines)
    implementation(libs.opentelemetry.sdk)
    implementation(libs.opentelemetry.exporter.otlp)
    implementation(libs.opentelemetry.exporter.jaeger)
    implementation(libs.opentelemetry.semconv)
    ksp(project(":core"))
}

kotlin {
    sourceSets.all {
        languageSettings {
            optIn("kotlin.ExperimentalStdlibApi")
        }
    }
}