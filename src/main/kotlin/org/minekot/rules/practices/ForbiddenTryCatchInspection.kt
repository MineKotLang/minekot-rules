package org.minekot.rules.practices

import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.minekot.inspections.core.*
import org.minekot.rules.internal.*

/** Flags catch clauses that swallow failures or can break coroutine cancellation. */
public class ForbiddenTryCatchInspection : PsiInspection() {
    override fun createVisitor(context: InspectionContext, reporter: InspectionReporter): KtVisitorVoid =
        object : KtTreeVisitorVoid() {
            override fun visitTryExpression(expression: KtTryExpression) {
                super.visitTryExpression(expression)
                expression.catchClauses.filter { it.isUnsafeCatch() }.forEach { clause ->
                    reporter.report(
                        context,
                        DESCRIPTOR,
                        clause,
                        "unsafe-catch",
                        DESCRIPTOR.message("unsafe-catch"),
                    )
                }
            }
        }

    /** Stable descriptor for exception-boundary policy. */
    public companion object {
        /** Rule metadata exposed by the catalog. */
        public val DESCRIPTOR: InspectionDescriptor = ruleDescriptor(
            id = RuleContracts.FORBIDDEN_TRY_CATCH,
            aliases = setOf(RuleContracts.FORBIDDEN_TRY_CATCH_ALIAS),
            category = InspectionCategory.CORRECTNESS,
            severity = RuleSeverity.WARNING,
        )

        private fun KtCatchClause.isUnsafeCatch(): Boolean {
            val body = catchBody as? KtBlockExpression ?: return true
            if (body.statements.isEmpty() || body.statements.all { logOnlyPattern.matches(it.text) }) return true
            val caughtType = catchParameter?.typeReference?.text?.substringAfterLast('.') ?: return false
            if (caughtType == "CancellationException") return !rethrowsCaughtFailure()
            if (caughtType !in cancellationSupertypes || isInsideTypedMineKotBoundary()) return false
            return !rethrowsCaughtFailure() ||
                    body.statements.size > 1 && !body.text.contains("CancellationException")
        }

        private fun KtCatchClause.rethrowsCaughtFailure(): Boolean {
            val parameterName = catchParameter?.name ?: return false
            return catchBody?.collectDescendantsOfType<KtThrowExpression>()
                ?.any { it.thrownExpression?.text == parameterName } == true
        }

        private fun KtCatchClause.isInsideTypedMineKotBoundary(): Boolean =
            parents.filterIsInstance<KtNamedFunction>().any { it.name == "mineKotRunCatching" }

        private val cancellationSupertypes: Set<String> =
            setOf("Exception", "IllegalStateException", "RuntimeException", "Throwable")
        private val logOnlyPattern: Regex = Regex(
            "(?:[A-Za-z_][A-Za-z0-9_.]*\\.)?(?:debug|error|info|print|println|warn)\\s*\\(.*\\)",
            RegexOption.DOT_MATCHES_ALL,
        )
    }
}
