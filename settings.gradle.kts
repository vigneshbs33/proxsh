pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "proxsh"

// core    shared Kotlin library: identity, trust, discovery, transfer, link choice
// laptop  the Windows app (Compose Desktop)
// android the phone app
include(":core", ":laptop", ":android")

