package org.minekot.rules

import java.nio.file.Path
import java.util.*
import kotlin.io.path.extension
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Protects the language-resource boundary for rule-facing text and stable IDs. */
class RuleLocalizationTest {
    private val bundle = ResourceBundle.getBundle("messages.MineKotRulesBundle", Locale.ENGLISH)

    /** Catalog metadata must resolve from the English language bundle. */
    @Test
    fun `every descriptor resolves localized metadata`() {
        MineKotRulesCatalog().inspections.forEach { descriptor ->
            assertEquals(bundle.getString("${descriptor.id}.name"), descriptor.displayName)
            assertEquals(bundle.getString("${descriptor.id}.description"), descriptor.description)
            assertTrue(descriptor.displayName.isNotBlank(), descriptor.id)
            assertTrue(descriptor.description.isNotBlank(), descriptor.id)
        }
    }

    /** Production rules may not embed user-facing text or descriptor identifiers at use sites. */
    @Test
    fun `production rules contain no inline presentation strings`() {
        val sourceRoot = Path.of(requireNotNull(System.getProperty("minekot.rootDir")))
            .resolve("src/main/kotlin/org/minekot/rules")
        val violations = sourceRoot.walk()
            .filter { path -> path.extension == "kt" && path.fileName.toString() != "MineKotRulesBundle.kt" }
            .flatMap { path ->
                path.readText().lineSequence().mapIndexedNotNull { index, line ->
                    forbiddenPatterns.firstOrNull(line::contains)?.let {
                        "${sourceRoot.relativize(path)}:${index + 1}: ${line.trim()}"
                    }
                }
            }
            .toList()
        assertFalse(violations.isNotEmpty(), violations.joinToString(separator = "\n"))
    }

    private companion object {
        private val forbiddenPatterns: List<String> = listOf(
            "displayName = \"",
            "description = \"",
            "label = \"",
            "message = \"",
            "id = \"minekot.",
        )
    }
}
