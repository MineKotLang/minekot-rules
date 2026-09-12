package org.minekot.rules.codestyle

import org.minekot.inspections.core.*
import org.jetbrains.kotlin.CoreEnvironmentDeprecation
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.cli.create
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.security.MessageDigest
import kotlin.test.*

/** Behavioral matrix for the string-template-braces inspection. */
class StringTemplateBracesInspectionTest {
    /** Simple entries flag once and the correction reaches a fixed point. */
    @Test
    fun `should flag simple template and produce idempotent correction`() {
        val source = "fun greet(name: String) = \"Hello, ${'$'}name!\""
        val findings = inspect(source)

        assertEquals(1, findings.size)
        assertEquals("minekot.codestyle.string-template-braces", findings.single().ruleId)
        val corrected = findings.single().corrections.single().applyTo(source, source.sha256())
        assertEquals("fun greet(name: String) = \"Hello, ${'$'}{name}!\"", corrected)
        assertTrue(inspect(corrected).isEmpty())
    }

    /** Already-correct and non-template inputs stay clean. */
    @Test
    fun `should not flag braced escaped or plain strings`() {
        val sources = listOf(
            "fun greet(name: String) = \"Hello, ${'$'}{name}!\"",
            "val price = \"${'$'}5\"",
            "val message = \"plain text\"",
        )

        sources.forEach { source -> assertTrue(inspect(source).isEmpty(), source) }
    }

    /** Every simple entry in a single template is reported. */
    @Test
    fun `should flag every simple entry in one template`() {
        val source = "fun pair(a: String, b: String) = \"${'$'}a:${'$'}b\""
        assertEquals(2, inspect(source).size)
    }

    @OptIn(CompilerConfiguration.Internals::class, CoreEnvironmentDeprecation::class, K1Deprecation::class)
    private fun inspect(source: String): List<InspectionFinding> {
        val disposable = Disposer.newDisposable()
        val configuration = CompilerConfiguration.create().apply {
            put(CommonConfigurationKeys.MODULE_NAME, "minekot-rules-test")
        }
        val environment = KotlinCoreEnvironment.createForProduction(
            disposable,
            configuration,
            EnvironmentConfigFiles.JVM_CONFIG_FILES,
        )
        val file = KtPsiFactory(environment.project, false).createFile("Test.kt", source)
        val findings = mutableListOf<InspectionFinding>()
        val context = InspectionContext(
            file = file,
            fileId = "Test.kt",
            sourceText = source,
            sourceSha256 = source.sha256(),
            options = emptyMap(),
            suppressionResolver = InspectionSuppressionResolver { _, _ -> false },
            capabilities = object : InspectionCapabilities {
                override fun supports(capabilityId: String): Boolean = false
            },
        )
        try {
            StringTemplateBracesInspection().createSession(context).use { session ->
                file.accept(session.createVisitor(findings::add))
            }
        } finally {
            Disposer.dispose(disposable)
        }
        return findings
    }
}

private fun String.sha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(this.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
