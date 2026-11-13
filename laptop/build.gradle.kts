import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.foundation)
    implementation(compose.ui)
    implementation(libs.coroutines.swing)

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

// Renders the window offscreen with two real nodes talking to each other: gradle :laptop:preview
tasks.register<JavaExec>("preview") {
    group = "verification"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("app.proxsh.laptop.dev.PreviewKt")
    args(layout.buildDirectory.dir("previews").get().asFile.absolutePath)
}

compose.desktop {
    application {
        mainClass = "app.proxsh.laptop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "ProxSh"
            packageVersion = "0.1.0"
            description = "Send files and text between your phone and laptop. No account, no cable."
            vendor = "ProxSh"
            windows {
                menuGroup = "ProxSh"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "6f0b3a1e-5d2c-4c7e-9a41-2b8e7f3d1c55"
            }
        }
    }
}
