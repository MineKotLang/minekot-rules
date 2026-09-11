import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.minekot.rules.build.AssembleRulesReleaseTask
import org.minekot.rules.build.ValidateThinRulesJarTask

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.minekot.toolchain)
    jacoco
}

group = project.findProperty("group") ?: missingProperty("group")
version = project.findProperty("version") ?: missingProperty("version")
val projectJavaVersion = 21
val minekotInspectionsVersion = providers.gradleProperty("minekotInspectionsVersion")
    .orElse(libs.versions.minekot.inspections)

@Suppress("GradleDslConventions")
val cleanFinalArtifacts = tasks.register<Delete>("cleanFinalArtifacts") {
    description = "Cleans the final directory with a backup of the previous state"
    val finalFile = layout.projectDirectory.dir("final").asFile
    val backupFile = layout.projectDirectory.dir("final_bak").asFile

    doFirst {
        if (finalFile.listFiles()?.isEmpty() != false) return@doFirst

        backupFile.deleteRecursively()
        finalFile.copyRecursively(target = backupFile, overwrite = true)
    }
    delete(finalFile)
}

repositories {
    mavenLocal()
    maven("https://maven2.minekot.org/releases")
    maven("https://maven2.minekot.org/snapshots")
    mavenCentral()
}

@Suppress("AvoidDuplicateDependencies", "RedundantSuppression")
dependencies {
    compileOnly("org.minekot.inspections:minekot-inspections-core:${minekotInspectionsVersion.get()}")
    compileOnly(libs.kotlin.compiler)

    testImplementation("org.minekot.inspections:minekot-inspections-core:${minekotInspectionsVersion.get()}")
    testImplementation("org.minekot.inspections:minekot-inspections-detekt:${minekotInspectionsVersion.get()}")
    testImplementation(libs.detekt.api)
    testImplementation(libs.kotlin.compiler)
    testImplementation(libs.kotlin.test)
}

minekotToolchain {
    toolchainVersion = libs.versions.minekot.toolchain
    build {
        javaVersion = projectJavaVersion
        allWarningsAsErrors = true
    }
    publishing {
        enabled = false
    }
    shadow {
        enabled = false
        mergeServiceFiles = true
    }
    reflection { enabled = false }
    serialization { enabled = false }
    io { enabled = false }
    coroutines { enabled = false }
    atomic { enabled = false }
    adventure { enabled = false }
    lint {
        enabled = true
        configFile = rootProject.layout.projectDirectory.file("config/detekt/minekot.yml")
    }
    ciCd {
        enabled = true
    }
}

afterEvaluate {
    configurations.named("implementation") {
        dependencies.removeAll { dependency -> dependency.group == "org.minekot" }
    }
}

tasks {
    withType<ShadowJar>().configureEach {
        enabled = false
    }

    withType<Test>().configureEach {
        jvmArgs("-Xshare:off")
        systemProperty("minekot.rootDir", rootProject.projectDir.absolutePath)
    }

    withType<Jar>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
        if (name == "jar") {
            mustRunAfter(":cleanFinalArtifacts")
            destinationDirectory = rootProject.layout.projectDirectory.dir("final")
            archiveFileName = "${project.name}-${project.version}.jar"
        }
        if (name == "sourcesJar") {
            mustRunAfter(":cleanFinalArtifacts")
            destinationDirectory = rootProject.layout.projectDirectory.dir("final")
            archiveFileName = "${project.name}-${project.version}-sources.jar"
        }
    }

    withType<Task>().configureEach {
        if (name == "build") {
            dependsOn(":cleanFinalArtifacts")
        }
    }
}

jacoco {
    toolVersion = "0.8.13"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        html.required.set(true)
        xml.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            limit {
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}

val rulesJarTask = tasks.named<Jar>("jar")

@Suppress("GradleDslConventions")
val validateThinRulesJar = tasks.register<ValidateThinRulesJarTask>("validateThinRulesJar") {
    rulesJar = rulesJarTask.flatMap { task -> task.archiveFile }
    dependsOn(rulesJarTask)
}

tasks.named("check") {
    dependsOn(validateThinRulesJar, tasks.jacocoTestReport, tasks.jacocoTestCoverageVerification)
}

tasks.register<AssembleRulesReleaseTask>("assembleRulesRelease") {
    val requestedVersion = providers.gradleProperty("releaseVersion")
    rulesJar = rulesJarTask.flatMap { task -> task.archiveFile }
    releaseVersion = requestedVersion
    releaseCommit = providers.gradleProperty("releaseCommit")
    publishedAt = providers.gradleProperty("releasePublishedAt")
    manifestFile = layout.projectDirectory.file(
        requestedVersion.map { selectedVersion -> "final/minekot-rules-${selectedVersion}.manifest.json" },
    )
    checksumsFile = layout.projectDirectory.file("final/SHA256SUMS")
    dependsOn(rulesJarTask)
}

private fun missingProperty(name: String): Nothing =
    throw IllegalStateException("Property '${name}' is missing. Please define it in gradle.properties.")
