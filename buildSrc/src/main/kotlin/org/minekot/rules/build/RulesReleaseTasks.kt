package org.minekot.rules.build

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Verifies that a rules artifact contains only rule implementation classes and one catalog service entry. */
public abstract class ValidateThinRulesJarTask : DefaultTask() {
    /** Rules JAR to validate. */
    @get:InputFile
    public abstract val rulesJar: RegularFileProperty

    /** Validates the archive without loading any classes from it. */
    @TaskAction
    public fun validateJar() {
        val forbidden = listOf(
            "kotlin/",
            "org/jetbrains/kotlin/",
            "org/minekot/inspections/",
            "dev/detekt/",
            "com/intellij/",
        )
        ZipFile(rulesJar.get().asFile).use { archive ->
            val names = archive.entries().asSequence().map { entry -> entry.name }.toList()
            val service = "META-INF/services/org.minekot.inspections.core.MineKotInspectionCatalog"
            require(names.count { name -> name == service } == 1) {
                "Expected exactly one catalog service entry."
            }
            require(names.none { name -> name.endsWith(".class") && forbidden.any(name::startsWith) }) {
                "Rules JAR bundles a forbidden shared API package."
            }
            require(names.none { name -> name.endsWith(".jar", ignoreCase = true) }) {
                "Rules JAR contains a nested JAR."
            }
        }
    }
}

/** Creates the signed-release inputs from one reproducible thin rules JAR. */
public abstract class AssembleRulesReleaseTask : DefaultTask() {
    /** Reproducible rules JAR. */
    @get:InputFile
    public abstract val rulesJar: RegularFileProperty

    /** SemVer release version without the tag prefix. */
    @get:Input
    public abstract val releaseVersion: Property<String>

    /** Full commit SHA represented by the release. */
    @get:Input
    public abstract val releaseCommit: Property<String>

    /** UTC ISO-8601 publication timestamp supplied by CI. */
    @get:Input
    public abstract val publishedAt: Property<String>

    /** Oldest core release accepted by this rules generation. */
    @get:Input
    public abstract val minimumCoreVersion: Property<String>

    /** First core release rejected by this rules generation. */
    @get:Input
    public abstract val maximumCoreVersionExclusive: Property<String>

    /** JVM bytecode/runtime floor for loading this rules generation. */
    @get:Input
    public abstract val minimumJavaVersion: Property<Int>

    /** Kotlin compiler/PSI version used to compile and test the rules. */
    @get:Input
    public abstract val kotlinVersion: Property<String>

    /** Detekt adapter version exercised by the release test suite. */
    @get:Input
    public abstract val detektVersion: Property<String>

    /** IntelliJ Platform version exercised by the release test suite. */
    @get:Input
    public abstract val ideaVersion: Property<String>

    /** Strict rules manifest emitted for signing. */
    @get:OutputFile
    public abstract val manifestFile: RegularFileProperty

    /** Digest list emitted beside the release assets. */
    @get:OutputFile
    public abstract val checksumsFile: RegularFileProperty

    /** Generates byte-stable manifest and checksum files. */
    @TaskAction
    public fun assembleRelease() {
        val jar = rulesJar.get().asFile
        val version = releaseVersion.get()
        require(version.matches(Regex("1\\.0\\.[1-9][0-9]*"))) { "Invalid release version: $version" }
        require(releaseCommit.get().matches(Regex("[0-9a-f]{40}"))) { "releaseCommit must be a full SHA-1." }

        val digest = jar.readBytes().sha256()
        val expectedJarName = "minekot-rules-$version.jar"
        require(jar.name == expectedJarName) { "Expected $expectedJarName, found ${jar.name}." }
        val manifest = RulesReleaseManifestCodec.encode(
            RulesReleaseManifest(
                rulesVersion = version,
                tag = "v$version",
                commitSha = releaseCommit.get(),
                publishedAt = publishedAt.get(),
                jarName = jar.name,
                jarSize = jar.length(),
                jarSha256 = digest,
                minimumCoreVersion = minimumCoreVersion.get(),
                maximumCoreVersionExclusive = maximumCoreVersionExclusive.get(),
                minimumJavaVersion = minimumJavaVersion.get(),
                kotlinPsiBaseline = kotlinVersion.get(),
                testedHosts = listOf(
                    RulesTestedHost(RulesHostType.DETEKT, detektVersion.get(), kotlinVersion.get()),
                    RulesTestedHost(RulesHostType.IDEA, ideaVersion.get(), kotlinVersion.get()),
                ),
                catalogProvider = jar.catalogProvider(),
            ),
        )
        val manifestOutput = manifestFile.get().asFile
        manifestOutput.parentFile.mkdirs()
        manifestOutput.writeText(manifest)
        checksumsFile.get().asFile.writeText(
            "$digest  ${jar.name}\n${manifest.encodeToByteArray().sha256()}  ${manifestOutput.name}\n",
        )
    }

}

private const val CATALOG_SERVICE_ENTRY =
    "META-INF/services/org.minekot.inspections.core.MineKotInspectionCatalog"

private fun java.io.File.catalogProvider(): String =
    ZipFile(this).use { archive ->
        val entry = requireNotNull(archive.getEntry(CATALOG_SERVICE_ENTRY)) {
            "Rules JAR is missing its catalog service entry."
        }
        val providers = archive.getInputStream(entry)
            .bufferedReader()
            .readLines()
            .map(String::trim)
            .filter { line -> line.isNotEmpty() && !line.startsWith('#') }
        require(providers.size == 1) { "Rules JAR must declare exactly one catalog provider." }
        providers.single()
    }

private fun ByteArray.sha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { byte -> "%02x".format(byte) }
