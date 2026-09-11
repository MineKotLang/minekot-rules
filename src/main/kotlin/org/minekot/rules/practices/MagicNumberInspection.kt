package org.minekot.rules.practices

import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.minekot.inspections.core.*
import org.minekot.rules.internal.*

/** Flags non-trivial numeric literals that should be named. */
public class MagicNumberInspection : PsiInspection() {
    override fun createVisitor(context: InspectionContext, reporter: InspectionReporter): KtVisitorVoid =
        object : KtTreeVisitorVoid() {
            override fun visitConstantExpression(expression: KtConstantExpression) {
                super.visitConstantExpression(expression)
                if (expression.isMagicNumber()) {
                    reporter.report(
                        context,
                        DESCRIPTOR,
                        expression,
                        "unnamed-number",
                        DESCRIPTOR.message("unnamed-number"),
                    )
                }
            }
        }

    /** Stable descriptor for intent-based value extraction. */
    public companion object {
        /** Rule metadata exposed by the catalog. */
        public val DESCRIPTOR: InspectionDescriptor = ruleDescriptor(
            id = RuleContracts.MAGIC_NUMBER,
            aliases = setOf(RuleContracts.MAGIC_NUMBER_ALIAS),
            category = InspectionCategory.CODE_STYLE,
            severity = RuleSeverity.WARNING,
        )

        private fun KtConstantExpression.isMagicNumber(): Boolean =
            text.toMineKotNumberOrNull() != null &&
                    text.toMineKotNumberOrNull() !in allowedNumbers &&
                    parents.filterIsInstance<KtAnnotationEntry>().firstOrNull() == null &&
                    parents.filterIsInstance<KtPackageDirective>().firstOrNull() == null &&
                    !isDirectNamedArgument() &&
                    !isNamedPropertyInitializer() &&
                    parents.filterIsInstance<KtProperty>().firstOrNull()?.isConstant() != true

        private fun KtConstantExpression.isDirectNamedArgument(): Boolean {
            val argument = when (val directParent = parent) {
                is KtValueArgument -> directParent
                is KtPrefixExpression -> directParent.parent as? KtValueArgument
                else -> null
            } ?: return false
            val argumentExpression = argument.getArgumentExpression()
            return argument.getArgumentName() != null &&
                    (argumentExpression == this || (argumentExpression as? KtPrefixExpression)?.baseExpression == this)
        }

        private fun KtConstantExpression.isNamedPropertyInitializer(): Boolean =
            (parent as? KtProperty)?.initializer == this

        private fun KtProperty.isConstant(): Boolean =
            hasModifier(KtTokens.CONST_KEYWORD) || name?.all { it.isUpperCase() || it == '_' || it.isDigit() } == true

        private fun String.toMineKotNumberOrNull(): Double? {
            val cleaned = trim().replace("_", "").lowercase()
                .removeSuffix("ul").removeSuffix("u").removeSuffix("l").removeSuffix("f")
            return when {
                cleaned.startsWith("0x") -> cleaned.removePrefix("0x").toLongOrNull(radix = 16)?.toDouble()
                cleaned.startsWith("0b") -> cleaned.removePrefix("0b").toLongOrNull(radix = 2)?.toDouble()
                else -> cleaned.toDoubleOrNull()
            }
        }

        private val allowedNumbers: Set<Double> = setOf(0.0, 1.0, 2.0)
    }
}
