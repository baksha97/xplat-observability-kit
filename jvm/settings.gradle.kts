pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
        maven("https://plugins.gradle.org/m2/")
    }
}

rootProject.name = "JVMObservability"

listOf(
    "core",
    "app"
).forEach(::include)