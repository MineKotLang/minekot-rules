package org.minekot.rules.structure

import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.minekot.inspections.core.*
import org.minekot.rules.internal.*

/** Enforces deterministic Gradle task naming, laziness, and action boundaries. */
public class GradleTaskContractInspection : PsiInspection() {
    override fun createVisitor(context: InspectionContext, reporter: InspectionReporter): KtVisitorVoid =
        object : KtTreeVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                if (!expression.containingKtFile.name.endsWith(".gradle.kts")) return
                val messageId = when {
                    expression.hasInvalidTaskName() -> "invalid-task-name"
                    expression.isEagerTaskConfiguration() -> "eager-task-configuration"
                    expression.isComplexInlineTaskAction() -> "complex-inline-action"
                    else -> null
                } ?: return
                reporter.report(context, DESCRIPTOR, expression, messageId, DESCRIPTOR.message(messageId))
            }
        }

    /** Stable descriptor for Gradle task contracts. */
    public companion object {
        /** Rule metadata exposed by catalog. */
        public val DESCRIPTOR: InspectionDescriptor = ruleDescriptor(
            id = RuleContracts.GRADLE_TASK_CONTRACT,
            aliases = setOf(RuleContracts.GRADLE_TASK_CONTRACT_ALIAS),
            category = InspectionCategory.PERFORMANCE,
            severity = RuleSeverity.WARNING,
        )

        private fun KtCallExpression.hasInvalidTaskName(): Boolean {
            if (calleeExpression?.text !in setOf("create", "register")) return false
            val qualified = parent as? KtDotQualifiedExpression ?: return false
            if (qualified.receiverExpression.text != "tasks") return false
            val name = valueArguments.firstOrNull()?.getArgumentExpression()?.text?.removeSurrounding("\"")
                ?: return false
            return !taskNamePattern.matches(name) || taskVerbs.none(name::startsWith)
        }

        private fun KtCallExpression.isEagerTaskConfiguration(): Boolean {
            val qualified = parent as? KtDotQualifiedExpression ?: return false
            if (qualified.receiverExpression.text != "tasks") return false
            if (calleeExpression?.text == "withType" && parents.filterIsInstance<KtDotQualifiedExpression>().any {
                    (it.selectorExpression as? KtCallExpression)?.calleeExpression?.text == "configureEach"
                }
            ) return false
            return calleeExpression?.text in setOf("create", "getAt", "getByName", "withType")
        }

        private fun KtCallExpression.isComplexInlineTaskAction(): Boolean {
            if (calleeExpression?.text !in setOf("doFirst", "doLast")) return false
            val statements = lambdaArguments.firstOrNull()?.getLambdaExpression()?.bodyExpression?.statements.orEmpty()
            return statements.size > 1
        }

        private val taskNamePattern: Regex = Regex("[a-z][A-Za-z0-9]*")
        private val taskVerbs: Set<String> = setOf(
            "assemble", "build", "check", "clean", "compile", "format", "generate", "lint", "mineKot",
            "prepare", "print", "publish", "run", "test", "verify", "write",
        )
    }
}
