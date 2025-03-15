plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(project(":core"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
//    implementation("io.opentelemetry.android:android-agent:0.10.0-alpha")
    implementation("io.opentelemetry:opentelemetry-sdk:1.48.0")
    implementation("io.opentelemetry:opentelemetry-extension-kotlin:1.48.0")
    implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure:1.48.0")
    implementation(libs.opentelemetry.api.incubator)
    implementation(libs.opentelemetry.exporter.otlp)
    implementation("io.opentelemetry:opentelemetry-exporter-jaeger:1.25.0")
    implementation("io.opentelemetry:opentelemetry-semconv:1.25.0-alpha")
    ksp(project(":core"))
}

kotlin {
    sourceSets.all {
        languageSettings {
            optIn("kotlin.ExperimentalStdlibApi")
        }
    }
}