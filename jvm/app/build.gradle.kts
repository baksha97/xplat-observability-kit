plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(project(":core"))
    ksp(project(":core"))
}