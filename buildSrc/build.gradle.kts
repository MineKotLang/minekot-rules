plugins {
    `kotlin-dsl`
    // Must match Gradle 9.7.1's embedded Kotlin used by `kotlin-dsl`.
    kotlin("plugin.serialization") version "2.4.0"
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation("junit:junit:4.13.2")
}
