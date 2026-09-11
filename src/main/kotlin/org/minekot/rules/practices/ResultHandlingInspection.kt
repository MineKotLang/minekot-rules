package org.minekot.rules.practices

import org.jetbrains.kotlin.psi.*
import org.minekot.inspections.core.*
import org.minekot.rules.internal.*

/** Flags obvious unsafe Result handling. */
public class ResultHandlingInspection : PsiInspection() {
    override fun createVisitor(context: InspectionContext, reporter: InspectionReporter): KtVisitorVoid =
        object : KtTreeVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                when (expression.calleeExpression?.text) {
                    "getOrThrow" -> if (expression.isKnownResultAccess()) {
                        reporter.report(
                            context,
                            DESCRIPTOR,
                            expression,
                            "unsafe-get-or-throw",
                            DESCRIPTOR.message("unsafe-get-or-throw"),
                        )
                    }

                    "runCatching" -> if (!expression.isResultConsumed() && !expression.isInsideFormatterControl()) {
                        reporter.report(
                            context,
                            DESCRIPTOR,
                            expression,
                            "discarded-result",
                            DESCRIPTOR.message("discarded-result"),
                        )
                    }
                }
            }
        }

    /** Stable descriptor for Result handling policy. */
    public companion object {
        /** Rule metadata exposed by the catalog. */
        public val DESCRIPTOR: InspectionDescriptor = ruleDescriptor(
            id = RuleContracts.RESULT_HANDLING,
            aliases = setOf(RuleContracts.RESULT_HANDLING_ALIAS),
            category = InspectionCategory.CORRECTNESS,
            severity = RuleSeverity.WARNING,
        )

        private fun KtCallExpression.isKnownResultAccess(): Boolean {
            val qualified = parent as? KtDotQualifiedExpression ?: return false
            if (qualified.selectorExpression != this) return false
            return knownResultProducerPrefixes.any(qualified.receiverExpression.text::startsWith)
        }

        private fun KtExpression.isResultConsumed(): Boolean = when (val container = parent) {
            is KtDotQualifiedExpression,
            is KtProperty,
            is KtReturnExpression,
            is KtNamedFunction,
            is KtValueArgument,
                -> true
            is KtParenthesizedExpression -> container.isResultConsumed()
            is KtContainerNode -> container.isResultContainerConsumed()
            is KtIfExpression -> container.isResultConsumed()
            is KtTryExpression -> container.isResultConsumed()
            is KtWhenEntry -> (container.parent as? KtWhenExpression)?.isResultConsumed() == true
            is KtBlockExpression -> container.usesLastStatementAsValue(this)
            else -> false
        }

        private fun KtContainerNode.isResultContainerConsumed(): Boolean = when (val container = parent) {
            is KtIfExpression -> container.isResultConsumed()
            is KtWhenEntry -> (container.parent as? KtWhenExpression)?.isResultConsumed() == true
            else -> false
        }

        private fun KtBlockExpression.usesLastStatementAsValue(expression: KtExpression): Boolean =
            statements.lastOrNull() == expression && when (val container = parent) {
                is KtFunctionLiteral -> !container.hasUnitReturningOwner()
                is KtIfExpression -> container.isResultConsumed()
                is KtTryExpression -> container.isResultConsumed()
                is KtWhenEntry -> (container.parent as? KtWhenExpression)?.isResultConsumed() == true
                else -> false
            }

        private fun KtFunctionLiteral.hasUnitReturningOwner(): Boolean {
            val lambda = parent as? KtLambdaExpression ?: return false
            val call = generateSequence(lambda.parent) { it.parent }
                .filterIsInstance<KtCallExpression>().firstOrNull() ?: return false
            return call.calleeExpression?.text in unitLambdaCalls
        }

        private val knownResultProducerPrefixes: Set<String> = setOf(
            "runCatching", "kotlin.runCatching", "Result.success", "Result.failure",
            "kotlin.Result.success", "kotlin.Result.failure",
        )
        private val unitLambdaCalls: Set<String> =
            setOf("also", "apply", "forEach", "forEachIndexed", "launch", "onEach")
    }
}
