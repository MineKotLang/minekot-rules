package org.minekot.rules.language

import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.minekot.inspections.core.*
import org.minekot.rules.internal.*

/** Prefers forEach for collection iteration when loop control and mutation are absent. */
public class ForEachPreferenceInspection : PsiInspection() {
    override fun createVisitor(context: InspectionContext, reporter: InspectionReporter): KtVisitorVoid =
        object : KtTreeVisitorVoid() {
            override fun visitForExpression(expression: KtForExpression) {
                super.visitForExpression(expression)
                val range = expression.loopRange ?: return
                val body = expression.body ?: return
                if (
                    range.text.isIndexedIteration() ||
                    expression.isInsideFormatterControl() ||
                    body.hasLoopControlFor(expression) ||
                    body.collectDescendantsOfType<KtReturnExpression>().isNotEmpty() ||
                    body.text.hasMutationRisk()
                ) return
                reporter.report(
                    context,
                    DESCRIPTOR,
                    expression,
                    "prefer-for-each",
                    DESCRIPTOR.message("prefer-for-each"),
                )
            }
        }

    /** Stable descriptor for simple collection iteration. */
    public companion object {
        /** Rule metadata exposed by the catalog. */
        public val DESCRIPTOR: InspectionDescriptor = ruleDescriptor(
            id = RuleContracts.FOR_EACH_PREFERENCE,
            aliases = setOf(RuleContracts.FOR_EACH_PREFERENCE_ALIAS),
            category = InspectionCategory.CODE_STYLE,
            severity = RuleSeverity.WARNING,
        )

        private fun String.isIndexedIteration(): Boolean =
            endsWith(".indices") || endsWith(".withIndex()") || contains("..") ||
                    contains(" until ") || contains(" downTo ")

        private fun String.hasMutationRisk(): Boolean = mutationPattern.containsMatchIn(this)

        private fun KtExpression.hasLoopControlFor(loop: KtForExpression): Boolean =
            collectDescendantsOfType<KtBreakExpression>().any { it.targets(loop) } ||
                    collectDescendantsOfType<KtContinueExpression>().any { it.targets(loop) }

        private fun KtExpression.targets(loop: KtLoopExpression): Boolean =
            parents.filterIsInstance<KtLoopExpression>().firstOrNull() == loop

        private val mutationPattern: Regex = Regex(
            "(?:\\[[^]]+]\\s*(?:[+*/%-]=|=(?!=))|" +
                    "\\.(?:add|addAll|clear|put|putAll|remove|removeAll|removeAt|removeIf|replaceAll|retainAll|" +
                    "set|shuffle|sort|sortBy|sortWith)\\s*\\(|" +
                    "\\b[A-Za-z_][A-Za-z0-9_]*\\s*(?:[+*/%-]=|=(?!=)))",
        )
    }
}
