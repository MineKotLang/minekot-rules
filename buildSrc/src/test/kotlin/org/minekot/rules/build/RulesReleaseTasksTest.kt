package org.minekot.rules.build

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.*
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Release artifact generation and thin-JAR validation tests. */
class RulesReleaseTasksTest {
    private val temporary = TemporaryFolder().apply { create() }

    /** Same inputs produce byte-identical manifest and checksum evidence. */
    @Test
    fun `release assembly is deterministic`() {
        val project = ProjectBuilder.builder().withProjectDir(temporary.newFolder("project")).build()
        val jar = temporary.newFile("minekot-rules-1.0.42.jar").apply { writeText("rules") }
        val firstManifest = temporary.newFile("first-manifest.json")
        val firstChecksums = temporary.newFile("first-SHA256SUMS")
        val task = project.tasks.register("firstRelease", AssembleRulesReleaseTask::class.java).get().apply {
            rulesJar.set(jar)
            releaseVersion.set("1.0.42")
            releaseCommit.set("a".repeat(COMMIT_LENGTH))
            publishedAt.set("2026-08-13T00:00:00Z")
            manifestFile.set(firstManifest)
            checksumsFile.set(firstChecksums)
        }
        task.assembleRelease()
        val expectedManifest = firstManifest.readBytes()
        val expectedChecksums = firstChecksums.readBytes()

        task.assembleRelease()

        assertArrayEquals(expectedManifest, firstManifest.readBytes())
        assertArrayEquals(expectedChecksums, firstChecksums.readBytes())
        assertTrue(firstManifest.readText().contains("\"rulesVersion\": \"1.0.42\""))
        assertEquals(2, firstChecksums.readLines().size)
    }

    /** Thin validator accepts one service and rejects shared implementation classes. */
    @Test
    fun `thin jar validation rejects shared classes`() {
        val project = ProjectBuilder.builder().withProjectDir(temporary.newFolder("thin-project")).build()
        val valid = zip("valid.jar", "org/minekot/rules/Rule.class")
        val invalid = zip("invalid.jar", "org/minekot/inspections/core/Spi.class")
        val task = project.tasks.register("validateRules", ValidateThinRulesJarTask::class.java).get()
        task.rulesJar.set(valid)
        task.validateJar()

        task.rulesJar.set(invalid)
        val failure = assertThrows(IllegalArgumentException::class.java) { task.validateJar() }
        assertTrue(failure.message!!.contains("forbidden shared API package"))
    }

    private fun zip(name: String, classEntry: String) = temporary.newFile(name).apply {
        ZipOutputStream(outputStream()).use { output ->
            listOf(SERVICE_ENTRY, classEntry).forEach { entryName ->
                output.putNextEntry(ZipEntry(entryName))
                output.write(if (entryName == SERVICE_ENTRY) "org.minekot.rules.Catalog".encodeToByteArray() else byteArrayOf(0))
                output.closeEntry()
            }
        }
    }

    private companion object {
        const val COMMIT_LENGTH = 40
        const val SERVICE_ENTRY = "META-INF/services/org.minekot.inspections.core.MineKotInspectionCatalog"
    }
}
