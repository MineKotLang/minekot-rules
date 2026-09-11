package org.minekot.rules.structure

import org.jetbrains.kotlin.psi.*
import org.minekot.inspections.core.*
import org.minekot.rules.internal.*

/** Enforces deterministic dependency notation and reproducible versions. */
public class GradleDependencyPolicyInspection : PsiInspection() {
    override fun createVisitor(context: InspectionContext, reporter: InspectionReporter): KtVisitorVoid =
        object : KtTreeVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                if (!expression.containingKtFile.name.endsWith(".gradle.kts")) return
                val messageId = when {
                    expression.isDynamicDependency() -> "dynamic-version"
                    expression.isShortKotlinDependencyNotation() -> "short-kotlin-notation"
                    expression.isFileProjectDependency() -> "file-project-dependency"
                    else -> null
                } ?: return
                reporter.report(context, DESCRIPTOR, expression, messageId, DESCRIPTOR.message(messageId))
            }
        }

    /** Stable descriptor for Gradle dependency policy. */
    public companion object {
        /** Rule metadata exposed by catalog. */
        public val DESCRIPTOR: InspectionDescriptor = ruleDescriptor(
            id = RuleContracts.GRADLE_DEPENDENCY_POLICY,
            aliases = setOf(RuleContracts.GRADLE_DEPENDENCY_POLICY_ALIAS),
            category = InspectionCategory.CORRECTNESS,
            severity = RuleSeverity.ERROR,
        )

        private fun KtCallExpression.isDynamicDependency(): Boolean {
            if (!dependencyConfigurationPattern.matches(calleeExpression?.text.orEmpty())) return false
            val dependency = valueArguments.firstOrNull()?.getArgumentExpression() as? KtStringTemplateExpression
                ?: return false
            return dynamicVersionPattern.containsMatchIn(dependency.text.removeSurrounding("\""))
        }

        private fun KtCallExpression.isShortKotlinDependencyNotation(): Boolean {
            if (calleeExpression?.text != "kotlin") return false
            val argument = parent as? KtValueArgument ?: return false
            val dependencyCall = argument.parent?.parent as? KtCallExpression ?: return false
            return dependencyConfigurationPattern.matches(dependencyCall.calleeExpression?.text.orEmpty())
        }

        private fun KtCallExpression.isFileProjectDependency(): Boolean =
            dependencyConfigurationPattern.matches(calleeExpression?.text.orEmpty()) && valueArguments.any { argument ->
                (argument.getArgumentExpression() as? KtCallExpression)?.calleeExpression?.text == "files"
            }

        private val dependencyConfigurationPattern: Regex =
            Regex("(?:api|implementation|compileOnly|runtimeOnly|testImplementation|testRuntimeOnly)")
        private val dynamicVersionPattern: Regex = Regex("(?:^|:)(?:latest\\.[A-Za-z]+|[^:]*\\+)(?:$|@)")
    }
}
