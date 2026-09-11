package org.minekot.rules.codestyle

import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.minekot.inspections.core.*
import org.minekot.rules.internal.*

/** Reports raw rich text only after resolving an Adventure or MineKot text flow. */
public class MiniMessageTextInspection : PsiInspection() {
    override fun createVisitor(context: InspectionContext, reporter: InspectionReporter): KtVisitorVoid =
        object : KtTreeVisitorVoid() {
            override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) {
                super.visitStringTemplateExpression(expression)
                if (expression.isPartOfStringConcatenation()) return
                val path = expression.resolvedCallPath(context)
                val miniMessageInput = path.firstOrNull() in miniMessageInputCalls
                val rawAdventureFlow = path.firstOrNull() in rawComponentCalls &&
                        path.any(adventureTextSinkCalls::contains)
                val message = when {
                    legacyColorPattern.containsMatchIn(expression.text) -> DESCRIPTOR.message("legacy-color-code")
                    rawAdventureFlow -> DESCRIPTOR.message("raw-rich-text")
                    else -> null
                }
                if (message != null && (miniMessageInput || rawAdventureFlow)) {
                    reporter.report(context, DESCRIPTOR, expression, "raw-rich-text", message)
                }
            }

            override fun visitBinaryExpression(expression: KtBinaryExpression) {
                super.visitBinaryExpression(expression)
                if (
                    expression.operationToken != KtTokens.PLUS || expression.isNestedStringConcatenation() ||
                    !expression.hasStringTemplateOperand()
                ) return
                val path = expression.resolvedCallPath(context)
                if (path.firstOrNull() !in rawComponentCalls || path.none(adventureTextSinkCalls::contains)) return
                reporter.report(
                    context,
                    DESCRIPTOR,
                    expression,
                    "rich-text-concatenation",
                    DESCRIPTOR.message("rich-text-concatenation"),
                )
            }
        }

    /** Stable descriptor for Adventure rich-text policy. */
    public companion object {
        /** Rule metadata exposed by the catalog. */
        public val DESCRIPTOR: InspectionDescriptor = ruleDescriptor(
            id = RuleContracts.MINIMESSAGE_TEXT,
            aliases = setOf(RuleContracts.MINIMESSAGE_TEXT_ALIAS),
            category = InspectionCategory.CODE_STYLE,
            severity = RuleSeverity.WARNING,
        )

        private fun KtElement.resolvedCallPath(context: InspectionContext): List<String> =
            parents.filterIsInstance<KtCallExpression>().mapNotNull(context::resolveCall).toList()

        private fun KtStringTemplateExpression.isPartOfStringConcatenation(): Boolean =
            parents.takeWhile { it !is KtCallExpression }.any {
                it is KtBinaryExpression && it.operationToken == KtTokens.PLUS
            }

        private fun KtBinaryExpression.isNestedStringConcatenation(): Boolean {
            var container = parent
            while (container is KtParenthesizedExpression) container = container.parent
            return container is KtBinaryExpression && container.operationToken == KtTokens.PLUS
        }

        private fun KtBinaryExpression.hasStringTemplateOperand(): Boolean =
            left.containsStringTemplate() || right.containsStringTemplate()

        private fun KtExpression?.containsStringTemplate(): Boolean = when (this) {
            is KtStringTemplateExpression -> true
            is KtBinaryExpression -> hasStringTemplateOperand()
            else -> false
        }

        private val legacyColorPattern: Regex = Regex("(?i)(§[0-9a-fk-or]|(?<![\\p{Alnum}?=&])&[0-9a-fk-or])")
        private val miniMessageInputCalls: Set<String> = setOf(
            "net.kyori.adventure.text.minimessage.MiniMessage.deserialize",
            "org.minekot.adventure.minimessage.mineKotMiniMessage",
            "org.minekot.adventure.minimessage.mineKotMiniMessageResult",
            "org.minekot.adventure.minimessage.toMineKotMiniMessageComponent",
        )
        private val rawComponentCalls: Set<String> = setOf("net.kyori.adventure.text.Component.text")
        private val adventureTextSinkCalls: Set<String> = setOf(
            "net.kyori.adventure.audience.Audience.sendActionBar",
            "net.kyori.adventure.audience.Audience.sendMessage",
            "net.kyori.adventure.audience.Audience.sendPlayerListFooter",
            "net.kyori.adventure.audience.Audience.sendPlayerListHeader",
            "net.kyori.adventure.audience.Audience.sendPlayerListHeaderAndFooter",
            "net.kyori.adventure.audience.Audience.showTitle",
        )
    }
}
