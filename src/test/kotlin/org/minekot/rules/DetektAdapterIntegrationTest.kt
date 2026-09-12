package org.minekot.rules

import com.intellij.openapi.util.Disposer
import dev.detekt.api.Config
import dev.detekt.api.RuleName
import org.jetbrains.kotlin.CoreEnvironmentDeprecation
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.cli.create
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.minekot.inspections.detekt.MineKotDetektRuleSetProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifies actual rules discovery and pure-finding mapping through the Detekt adapter. */
class DetektAdapterIntegrationTest {
    /** ServiceLoader exposes the complete official catalog to Detekt. */
    @Test
    fun `detekt discovers every official rule`() {
        val rules = MineKotDetektRuleSetProvider().instance().rules.keys.map(RuleName::value).toSet()

        assertEquals(MineKotRulesCatalog().inspections.size, rules.size)
        assertTrue("minekot-codestyle-whitespace-formatting" in rules)
        assertTrue("minekot-performance-gradle-configuration" in rules)
    }

    /** New performance findings retain their exact identity, message, and source range. */
    @Test
    fun `gradle performance finding maps through detekt`() {
        val rule = MineKotDetektRuleSetProvider().instance().rules
            .getValue(RuleName("minekot-performance-gradle-configuration"))
            .invoke(Config.empty)
        val source = "afterEvaluate { Unit }"

        val finding = lint(rule, source).single()

        assertEquals("Replace afterEvaluate with lazy providers or configure the target directly.", finding.message)
        assertEquals(0, finding.entity.location.text.start)
        assertEquals(source.length, finding.entity.location.text.end)
    }

    @OptIn(CompilerConfiguration.Internals::class, CoreEnvironmentDeprecation::class, K1Deprecation::class)
    private fun lint(rule: dev.detekt.api.Rule, source: String): List<dev.detekt.api.Finding> {
        val disposable = Disposer.newDisposable()
        return try {
            val environment = KotlinCoreEnvironment.createForProduction(
                disposable,
                CompilerConfiguration.create().apply {
                    put(CommonConfigurationKeys.MODULE_NAME, "rules-detekt-integration")
                },
                EnvironmentConfigFiles.JVM_CONFIG_FILES,
            )
            val file = KtPsiFactory(environment.project, false).createFile("build.gradle.kts", source)
            rule.visitFile(file, LanguageVersionSettingsImpl.DEFAULT)
        } finally {
            Disposer.dispose(disposable)
        }
    }
}
