rootProject.name = "kotlin-expression"
include(":expression")
include(":benchmarks")

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories { mavenCentral() }
}
