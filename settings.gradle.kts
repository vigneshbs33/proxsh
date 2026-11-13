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
include(":core", ":laptop")

// ":android" is added once the Android SDK is installed. See android/README.md.
