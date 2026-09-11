package org.minekot.rules.structure

import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.minekot.inspections.core.*
import org.minekot.rules.internal.*

/** Enforces Gradle Kotlin DSL conventions that can be determined safely from syntax. */
public class GradleDslConventionsInspection : PsiInspection() {
    override fun createVisitor(context: InspectionContext, reporter: InspectionReporter): KtVisitorVoid =
        object : KtTreeVisitorVoid() {
            override fun visitKtFile(file: KtFile) {
                super.visitKtFile(file)
                if (!file.name.endsWith(".gradle.kts")) return
                val source = file.text
                if (file.name == "build.gradle.kts") {
                    val pluginsOffset = source.indexOf("plugins {")
                    val firstConfigurationOffset = listOf(
                        source.indexOf("repositories {"),
                        source.indexOf("dependencies {"),
                    ).filter { it >= 0 }.minOrNull()
                    if (
                        pluginsOffset >= 0 && firstConfigurationOffset != null &&
                        pluginsOffset > firstConfigurationOffset
                    ) report(file, "plugins-order", DESCRIPTOR.message("plugins-order"))
                }
            }

            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                if (!expression.containingKtFile.name.endsWith(".gradle.kts")) return
                when {
                    expression.isImperativePluginApplication() -> report(
                        expression,
                        "imperative-plugin",
                        DESCRIPTOR.message("imperative-plugin"),
                    )
                    expression.isSingleLineGradleBlock() -> report(
                        expression,
                        "single-line-block",
                        DESCRIPTOR.message("single-line-block"),
                    )
                    expression.isUntypedAccessor() -> report(
                        expression,
                        "untyped-accessor",
                        DESCRIPTOR.message("untyped-accessor"),
                    )
                    expression.hasInvalidNamedContainerElement() -> report(
                        expression,
                        "invalid-container-name",
                        DESCRIPTOR.message("invalid-container-name"),
                    )
                    expression.isBroadSubprojectConfiguration() -> report(
                        expression,
                        "broad-subproject-configuration",
                        DESCRIPTOR.message("broad-subproject-configuration"),
                    )
                }
            }

            private fun report(element: KtElement, messageId: String, message: String) {
                reporter.report(context, DESCRIPTOR, element, messageId, message)
            }
        }

    /** Stable descriptor for Gradle Kotlin DSL conventions. */
    public companion object {
        /** Rule metadata exposed by the catalog. */
        public val DESCRIPTOR: InspectionDescriptor = ruleDescriptor(
            id = RuleContracts.GRADLE_DSL,
            aliases = setOf(RuleContracts.GRADLE_DSL_ALIAS),
            category = InspectionCategory.CODE_STYLE,
            severity = RuleSeverity.WARNING,
        )

        private fun KtCallExpression.isImperativePluginApplication(): Boolean =
            calleeExpression?.text == "apply" && valueArguments.any { it.getArgumentName()?.text == "plugin" } &&
                    parents.filterIsInstance<KtCallExpression>().none {
                        it.calleeExpression?.text in setOf("allprojects", "subprojects")
                    }

        private fun KtCallExpression.isSingleLineGradleBlock(): Boolean =
            calleeExpression?.text in multilineGradleBlocks && lambdaArguments.any { '\n' !in it.text }

        private fun KtCallExpression.isUntypedAccessor(): Boolean =
            calleeExpression?.text == "the" || calleeExpression?.text in setOf("getByType", "withType") &&
                    valueArguments.any { it.text.contains("::class.java") }

        private fun KtCallExpression.hasInvalidNamedContainerElement(): Boolean {
            val qualified = parent as? KtDotQualifiedExpression ?: return false
            if (
                qualified.receiverExpression.text !in namedContainers ||
                calleeExpression?.text !in setOf("create", "register")
            ) return false
            val name = valueArguments.firstOrNull()?.getArgumentExpression()?.text?.removeSurrounding("\"")
                ?: return false
            return !camelCaseTaskNamePattern.matches(name)
        }

        private fun KtCallExpression.isBroadSubprojectConfiguration(): Boolean =
            calleeExpression?.text in setOf("allprojects", "subprojects") && lambdaArguments.isNotEmpty()

        private val camelCaseTaskNamePattern: Regex = Regex("[a-z][A-Za-z0-9]*")
        private val multilineGradleBlocks: Set<String> = setOf("dependencies", "plugins", "repositories")
        private val namedContainers: Set<String> = setOf("configurations", "extensions")
    }
}
