import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Plain JVM library with Java 17 bytecode, so the Android app and the laptop app can both use it.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.coroutines.core)
    api(libs.serialization.json)
    // Only used to build the self-signed device certificate.
    implementation(libs.bouncycastle.prov)
    implementation(libs.bouncycastle.pkix)

    testImplementation(kotlin("test"))
    testImplementation(libs.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
    // Keep test files inside the project (on D), not in the system temp folder on C.
    val tmp = layout.buildDirectory.dir("test-tmp").get().asFile
    doFirst { tmp.mkdirs() }
    systemProperty("java.io.tmpdir", tmp.absolutePath)
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
