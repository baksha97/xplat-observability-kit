plugins {
    id("com.google.devtools.ksp")
    kotlin("jvm")
}

version = "0.0.1"

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":monitor"))
    ksp(project(":monitor"))
}
