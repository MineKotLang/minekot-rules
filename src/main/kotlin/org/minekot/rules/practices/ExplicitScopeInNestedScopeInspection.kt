package org.minekot.rules.practices

import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.minekot.inspections.core.*
import org.minekot.rules.internal.*

/** Reports implicit outer class or object members inside plain nested lambdas. */
public class ExplicitScopeInNestedScopeInspection : PsiInspection() {
    override fun createVisitor(context: InspectionContext, reporter: InspectionReporter): KtVisitorVoid =
        object : KtTreeVisitorVoid() {
            override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                super.visitSimpleNameExpression(expression)
                val reference = expression as? KtNameReferenceExpression ?: return
                if (
                    reference.isInsideFormatterControl() || !reference.isUnqualified() ||
                    reference.parents.none { it is KtLambdaExpression }
                ) return
                val capability = context.capabilities as? ExplicitOuterReceiverCapability ?: return
                if (!capability.isImplicitOuterReceiver(reference)) return
                reporter.report(
                    context,
                    DESCRIPTOR,
                    reference,
                    "implicit-outer-receiver",
                    DESCRIPTOR.message("implicit-outer-receiver", reference.getReferencedName()),
                )
            }
        }

    /** Stable descriptor for explicit nested-scope receiver policy. */
    public companion object {
        /** Rule metadata exposed by the catalog. */
        public val DESCRIPTOR: InspectionDescriptor = ruleDescriptor(
            id = RuleContracts.EXPLICIT_SCOPE,
            aliases = setOf(RuleContracts.EXPLICIT_SCOPE_ALIAS),
            category = InspectionCategory.CODE_STYLE,
            severity = RuleSeverity.INFO,
        )

        private fun KtNameReferenceExpression.isUnqualified(): Boolean {
            val selector = (parent as? KtCallExpression) ?: this
            val qualified = selector.parent as? KtQualifiedExpression ?: return true
            return qualified.selectorExpression != selector
        }
    }
}
