package org.minekot.rules.structure

import org.jetbrains.kotlin.psi.*
import org.minekot.inspections.core.*
import org.minekot.rules.internal.*

/** Requires explicit framework and parallelism configuration for configured Gradle test tasks. */
public class GradleTestConfigurationInspection : PsiInspection() {
    override fun createVisitor(context: InspectionContext, reporter: InspectionReporter): KtVisitorVoid =
        object : KtTreeVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                if (
                    !expression.containingKtFile.name.endsWith(".gradle.kts") ||
                    !expression.configuresTestTask()
                ) return
                val body = expression.lambdaArguments.firstOrNull()?.getLambdaExpression()?.bodyExpression ?: return
                if (body.text.contains("useJUnitPlatform()") && body.text.contains("maxParallelForks")) return
                reporter.report(
                    context,
                    DESCRIPTOR,
                    expression,
                    "incomplete-test-configuration",
                    DESCRIPTOR.message("incomplete-test-configuration"),
                )
            }
        }

    /** Stable descriptor for Gradle test configuration. */
    public companion object {
        /** Rule metadata exposed by catalog. */
        public val DESCRIPTOR: InspectionDescriptor = ruleDescriptor(
            id = RuleContracts.GRADLE_TEST_CONFIGURATION,
            aliases = setOf(RuleContracts.GRADLE_TEST_CONFIGURATION_ALIAS),
            category = InspectionCategory.PERFORMANCE,
            severity = RuleSeverity.WARNING,
        )

        private fun KtCallExpression.configuresTestTask(): Boolean {
            if (calleeExpression?.text == "configureEach") {
                val receiver = (parent as? KtDotQualifiedExpression)?.receiverExpression?.text ?: return false
                return receiver.startsWith("tasks.withType") && receiver.contains("<Test>")
            }
            if (calleeExpression?.text !in setOf("named", "withType")) return false
            val qualified = parent as? KtDotQualifiedExpression ?: return false
            if (qualified.receiverExpression.text != "tasks") return false
            return typeArgumentList?.text?.contains("Test") == true ||
                    valueArguments.firstOrNull()?.getArgumentExpression()?.text == "\"test\""
        }
    }
}
