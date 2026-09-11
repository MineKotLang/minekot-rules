package org.minekot.rules.codestyle

import com.intellij.psi.PsiComment
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.minekot.inspections.core.*
import org.minekot.rules.internal.*

/** Requires trailing commas in supported multiline comma-separated constructs. */
public class TrailingCommaInspection : PsiInspection() {
    override fun createVisitor(context: InspectionContext, reporter: InspectionReporter): KtVisitorVoid =
        object : KtTreeVisitorVoid() {
            override fun visitParameterList(list: KtParameterList) {
                super.visitParameterList(list)
                if (list.ownerFunction !is KtFunctionLiteral) list.inspectLast(list.parameters.lastOrNull())
            }

            override fun visitValueArgumentList(list: KtValueArgumentList) {
                super.visitValueArgumentList(list)
                list.inspectLast(list.arguments.lastOrNull())
            }

            override fun visitTypeArgumentList(list: KtTypeArgumentList) {
                super.visitTypeArgumentList(list)
                list.inspectLast(list.arguments.lastOrNull())
            }

            override fun visitTypeParameterList(list: KtTypeParameterList) {
                super.visitTypeParameterList(list)
                list.inspectLast(list.parameters.lastOrNull())
            }

            override fun visitCollectionLiteralExpression(expression: KtCollectionLiteralExpression) {
                super.visitCollectionLiteralExpression(expression)
                expression.inspectLast(expression.innerExpressions.lastOrNull())
            }

            override fun visitDestructuringDeclaration(multiDeclaration: KtDestructuringDeclaration) {
                super.visitDestructuringDeclaration(multiDeclaration)
                val left = multiDeclaration.lPar ?: return
                val right = multiDeclaration.rPar ?: return
                val last = multiDeclaration.entries.lastOrNull() ?: return
                multiDeclaration.inspectBefore(last, left.textRange.startOffset, right.textRange.startOffset)
            }

            override fun visitArrayAccessExpression(expression: KtArrayAccessExpression) {
                super.visitArrayAccessExpression(expression)
                val left = expression.leftBracket ?: return
                val right = expression.rightBracket ?: return
                if ('\n' in context.sourceText.substring(left.textRange.startOffset, right.textRange.endOffset)) {
                    expression.inspectLast(expression.indexExpressions.lastOrNull())
                }
            }

            override fun visitContextReceiverList(contextReceiverList: KtContextReceiverList) {
                super.visitContextReceiverList(contextReceiverList)
                val last = contextReceiverList.contextParameters.lastOrNull()
                    ?: contextReceiverList.contextReceivers().lastOrNull()
                contextReceiverList.inspectLast(last)
            }

            override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) {
                super.visitLambdaExpression(lambdaExpression)
                val literal = lambdaExpression.functionLiteral
                val last = literal.valueParameters.lastOrNull() ?: return
                val arrow = literal.arrow ?: return
                lambdaExpression.inspectBefore(
                    last,
                    lambdaExpression.textRange.startOffset,
                    arrow.textRange.startOffset,
                )
            }

            override fun visitWhenEntry(jetWhenEntry: KtWhenEntry) {
                super.visitWhenEntry(jetWhenEntry)
                val whenExpression = jetWhenEntry.parent as? KtWhenExpression ?: return
                if (whenExpression.subjectExpression == null || jetWhenEntry.conditions.size < 2) return
                val last = jetWhenEntry.conditions.lastOrNull() ?: return
                val arrow = jetWhenEntry.arrow ?: return
                jetWhenEntry.inspectBefore(
                    last,
                    jetWhenEntry.textRange.startOffset,
                    arrow.textRange.startOffset,
                )
            }

            private fun KtElement.inspectLast(lastElement: KtElement?) {
                if (lastElement == null || '\n' !in text || hasTrailingCommaIgnoringComments()) return
                val firstComment = lastElement.collectDescendantsOfType<PsiComment>()
                    .minOfOrNull { it.textRange.startOffset }
                val boundary = firstComment ?: lastElement.textRange.endOffset
                val insertionOffset = context.sourceText.substring(0, boundary).trimEnd().length
                reportCorrection(
                    this,
                    insertionOffset,
                    "multiline-list",
                    DESCRIPTOR.message("multiline-list"),
                )
            }

            private fun KtElement.inspectBefore(last: KtElement, headerStart: Int, boundary: Int) {
                if ('\n' !in context.sourceText.substring(headerStart, boundary)) return
                val between = context.sourceText.substring(last.textRange.endOffset, boundary)
                val withoutComments = between.replace(lineCommentPattern, "").replace(blockCommentPattern, "")
                if (withoutComments.trim().startsWith(',')) return
                reportCorrection(
                    this,
                    last.textRange.endOffset,
                    "multiline-delimiter",
                    DESCRIPTOR.message("multiline-delimiter"),
                )
            }

            private fun reportCorrection(element: KtElement, offset: Int, messageId: String, message: String) {
                reporter.report(
                    context,
                    DESCRIPTOR,
                    element,
                    messageId,
                    message,
                    listOf(
                        CorrectionPlan(
                            id = "${DESCRIPTOR.id}.insert-comma",
                            label = DESCRIPTOR.correctionLabel("insert-comma"),
                            fileId = context.fileId,
                            sourceSha256 = context.sourceSha256,
                            edits = listOf(TextEdit(offset, offset, "", ",")),
                        ),
                    ),
                )
            }
        }

    /** Stable descriptor for trailing-comma policy. */
    public companion object {
        /** Rule metadata exposed by the catalog. */
        public val DESCRIPTOR: InspectionDescriptor = ruleDescriptor(
            id = RuleContracts.TRAILING_COMMA,
            aliases = setOf(RuleContracts.TRAILING_COMMA_ALIAS),
            category = InspectionCategory.CODE_STYLE,
            severity = RuleSeverity.WARNING,
        )

        private fun KtElement.hasTrailingCommaIgnoringComments(): Boolean {
            val sourceWithoutComments = text.toCharArray()
            collectDescendantsOfType<PsiComment>().forEach { comment ->
                val start = comment.textRange.startOffset - textRange.startOffset
                val end = comment.textRange.endOffset - textRange.startOffset
                (start until end).forEach { sourceWithoutComments[it] = ' ' }
            }
            return sourceWithoutComments.concatToString().dropLast(1).trimEnd().endsWith(',')
        }

        private val lineCommentPattern: Regex = Regex("//[^\n]*")
        private val blockCommentPattern: Regex = Regex("/\\*[\\s\\S]*?\\*/")
    }
}
