package org.minekot.rules.build

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Machine-readable compatibility and integrity contract shipped with one rules release. */
@Serializable
internal data class RulesReleaseManifest(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val rulesVersion: String,
    val tag: String,
    val commitSha: String,
    val publishedAt: String,
    val jarName: String,
    val jarSize: Long,
    val jarSha256: String,
    val spiMajor: Int = CURRENT_SPI_MAJOR,
    val minimumCoreVersion: String,
    val maximumCoreVersionExclusive: String,
    val minimumJavaVersion: Int,
    val kotlinPsiBaseline: String,
    val testedHosts: List<RulesTestedHost>,
    val catalogProvider: String,
    val configurationSchemaVersion: Int = CURRENT_CONFIGURATION_SCHEMA_VERSION,
) {
    internal companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 1
        const val CURRENT_SPI_MAJOR: Int = 1
        const val CURRENT_CONFIGURATION_SCHEMA_VERSION: Int = 1
    }
}

/** Exact host dependency tuple exercised before a release is published. */
@Serializable
internal data class RulesTestedHost(
    val hostType: RulesHostType,
    val hostVersion: String,
    val kotlinVersion: String,
)

/** Host adapters whose compatibility is represented by the release manifest. */
@Serializable
internal enum class RulesHostType {
    DETEKT,
    IDEA,
}

/** Deterministic JSON codec for the signed release contract. */
internal object RulesReleaseManifestCodec {
    private val format = Json {
        encodeDefaults = true
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    fun encode(manifest: RulesReleaseManifest): String = format.encodeToString(manifest) + "\n"

    fun decode(content: String): RulesReleaseManifest = format.decodeFromString(content)
}
