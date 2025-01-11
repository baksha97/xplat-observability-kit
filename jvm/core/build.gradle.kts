plugins {
    alias(libs.plugins.kotlin.jvm)
    // Needed for test - otherwise wouldn't be needed.
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.ksp.symbol.processing.api)
    implementation(libs.kotlin.poet)

    kspTest(project(":core"))
    testImplementation(libs.mockk)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}
