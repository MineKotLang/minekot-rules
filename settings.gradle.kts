// -------===={ Project Configuration }====-------

rootProject.name = "minekot-rules"

enableFeaturePreview("STABLE_CONFIGURATION_CACHE")
enableFeaturePreview("GROOVY_COMPILATION_AVOIDANCE")

// -------===={ Plugin Management }====-------

pluginManagement {
    repositories {
        maven("https://maven2.minekot.org/releases/")
        maven("https://maven2.minekot.org/snapshots/")
        gradlePluginPortal()
        mavenLocal()
        mavenCentral()
    }
}

// -------===={ Plugins }====-------

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("com.gradle.develocity") version "4.5.0"
}

// -------===={ Plugin Configuration }====-------

develocity {
    buildScan {
        termsOfUseUrl = "https://gradle.com/help/legal-terms-of-use"
        termsOfUseAgree = "yes"
        publishing.onlyIf { true }
    }
}
