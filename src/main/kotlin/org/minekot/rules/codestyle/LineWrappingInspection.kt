package org.minekot.rules.codestyle

import com.intellij.psi.PsiComment
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.minekot.inspections.core.*
import org.minekot.rules.internal.*

/** Wraps long calls and conditions without changing string contents. */
public class LineWrappingInspection : PsiInspection() {
    override fun createVisitor(context: InspectionContext, reporter: InspectionReporter): KtVisitorVoid =
        object : KtTreeVisitorVoid() {
            private val correctedLineStarts: MutableSet<Int> = mutableSetOf()

            override fun visitKtFile(file: KtFile) {
                super.visitKtFile(file)
                reportUnstructuredLongLines(file)
            }

            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                val argumentList = expression.valueArgumentList ?: return
                if (
                    argumentList.textContains('\n') || expression.valueArguments.size < 2 ||
                    expression.containsStringLiteral() ||
                    expression.collectDescendantsOfType<PsiComment>().isNotEmpty() ||
                    expression.isInsideFormatterControl() || expression.lineLength() <= MAX_LINE_LENGTH
                ) return
                val indent = expression.lineIndent()
                val argumentIndent = " ".repeat(indent.length + CONTINUATION_INDENT)
                val replacement = expression.valueArguments.joinToString(
                    prefix = "(\n",
                    separator = "\n",
                    postfix = "\n${indent})",
                ) { "${argumentIndent}${it.text.removeSuffix(",")}," }
                reportEdit(
                    expression,
                    argumentList,
                    replacement,
                    "long-call",
                    DESCRIPTOR.message("long-call"),
                )
            }

            override fun visitIfExpression(expression: KtIfExpression) {
                super.visitIfExpression(expression)
                val condition = expression.condition ?: return
                if (
                    condition.textContains('\n') || condition.isInsideFormatterControl() ||
                    expression.lineLength() <= MAX_LINE_LENGTH || condition.containsStringLiteral()
                ) return
                val parts = condition.text.split(logicalOperatorPattern)
                val operators = logicalOperatorPattern.findAll(condition.text).map { it.value.trim() }.toList()
                if (parts.size < 2 || operators.size != parts.size - 1) return
                val continuation = " ".repeat(expression.lineIndent().length + CONTINUATION_INDENT)
                val replacement = buildString {
                    append(parts.first().trim())
                    operators.forEachIndexed { index, operator ->
                        append(" ${operator}\n${continuation}${parts[index + 1].trim()}")
                    }
                }
                reportEdit(
                    expression,
                    condition,
                    replacement,
                    "long-condition",
                    DESCRIPTOR.message("long-condition"),
                )
            }

            override fun visitPropertyAccessor(accessor: KtPropertyAccessor) {
                super.visitPropertyAccessor(accessor)
                val body = accessor.bodyExpression ?: return
                val equalsToken = accessor.equalsToken ?: return
                val bodyGap = context.sourceText.substring(equalsToken.textRange.endOffset, body.textRange.startOffset)
                if (
                    accessor.isSetter || '\n' in bodyGap || body.textContains('\n') || body.containsStringLiteral() ||
                    accessor.isInsideFormatterControl() || accessor.lineLength() <= MAX_LINE_LENGTH
                ) return
                val continuation = " ".repeat(accessor.lineIndent().length + CONTINUATION_INDENT)
                reportEdit(
                    body,
                    equalsToken.textRange.endOffset,
                    body.textRange.startOffset,
                    "\n${continuation}",
                    "long-getter",
                    DESCRIPTOR.message("long-getter"),
                )
            }

            override fun visitBinaryWithTypeRHSExpression(expression: KtBinaryExpressionWithTypeRHS) {
                super.visitBinaryWithTypeRHSExpression(expression)
                if (
                    expression.textContains('\n') || expression.containsStringLiteral() ||
                    expression.isInsideFormatterControl() || expression.lineLength() <= MAX_LINE_LENGTH
                ) return
                val offset = expression.operationReference.textRange.startOffset
                val continuation = " ".repeat(expression.lineIndent().length + CONTINUATION_INDENT)
                reportEdit(
                    expression,
                    offset,
                    offset,
                    "\n${continuation}",
                    "long-cast",
                    DESCRIPTOR.message("long-cast"),
                )
            }

            override fun visitWhenEntry(jetWhenEntry: KtWhenEntry) {
                super.visitWhenEntry(jetWhenEntry)
                val conditions = jetWhenEntry.conditions
                if (
                    conditions.size < 2 || jetWhenEntry.textContains('\n') ||
                    jetWhenEntry.isInsideFormatterControl() || jetWhenEntry.lineLength() <= MAX_LINE_LENGTH
                ) return
                val continuation = " ".repeat(jetWhenEntry.lineIndent().length + CONTINUATION_INDENT)
                val replacement = conditions.joinToString(",\n${continuation}") { it.text }
                reportEdit(
                    jetWhenEntry.expression ?: conditions.first(),
                    conditions.first().textRange.startOffset,
                    conditions.last().textRange.endOffset,
                    replacement,
                    "long-when-entry",
                    DESCRIPTOR.message("long-when-entry"),
                )
            }

            private fun reportEdit(
                findingElement: KtElement,
                replacedElement: KtElement,
                replacement: String,
                messageId: String,
                message: String,
            ) = reportEdit(
                findingElement,
                replacedElement.textRange.startOffset,
                replacedElement.textRange.endOffset,
                replacement,
                messageId,
                message,
            )

            private fun reportEdit(
                findingElement: KtElement,
                start: Int,
                end: Int,
                replacement: String,
                messageId: String,
                message: String,
            ) {
                correctedLineStarts += context.sourceText.lastIndexOf(
                    '\n',
                    findingElement.textRange.startOffset - 1,
                ) + 1
                reporter.report(
                    context,
                    DESCRIPTOR,
                    findingElement,
                    messageId,
                    message,
                    listOf(
                        CorrectionPlan(
                            id = "${DESCRIPTOR.id}.${messageId}",
                            label = message,
                            fileId = context.fileId,
                            sourceSha256 = context.sourceSha256,
                            edits = listOf(
                                TextEdit(start, end, context.sourceText.substring(start, end), replacement),
                            ),
                        ),
                    ),
                )
            }

            private fun reportUnstructuredLongLines(file: KtFile) {
                var lineStart = 0
                var formatterDisabled = false
                context.sourceText.lineSequence().forEach { line ->
                    val containsOffTag = "@formatter:off" in line
                    val containsOnTag = "@formatter:on" in line
                    if (
                        !formatterDisabled && !containsOffTag && line.length > MAX_LINE_LENGTH &&
                        lineStart !in correctedLineStarts
                    ) {
                        val anchor = file.findElementAt(lineStart)?.let { element ->
                            PsiTreeUtil.getParentOfType(element, KtElement::class.java, false)
                        } ?: file
                        if (context.suppressionResolver.isSuppressed(DESCRIPTOR.id, anchor)) {
                            lineStart += line.length + 1
                            return@forEach
                        }
                        reporter.report(
                            InspectionFinding(
                                ruleId = DESCRIPTOR.id,
                                messageId = "overlong-line",
                                message = DESCRIPTOR.message("overlong-line"),
                                messageArguments = emptyList(),
                                startOffset = lineStart,
                                endOffset = lineStart + line.length,
                                severity = DESCRIPTOR.defaultSeverity,
                            ),
                        )
                    }
                    if (containsOffTag) formatterDisabled = true
                    if (containsOnTag) formatterDisabled = false
                    lineStart += line.length + 1
                }
            }
        }

    /** Stable descriptor for line-wrapping policy. */
    public companion object {
        /** Rule metadata exposed by the catalog. */
        public val DESCRIPTOR: InspectionDescriptor = ruleDescriptor(
            id = RuleContracts.LINE_WRAPPING,
            aliases = setOf(RuleContracts.LINE_WRAPPING_ALIAS),
            category = InspectionCategory.CODE_STYLE,
            severity = RuleSeverity.WARNING,
        )

        private fun KtElement.containsStringLiteral(): Boolean =
            collectDescendantsOfType<KtStringTemplateExpression>().isNotEmpty()

        private fun KtElement.lineLength(): Int {
            val source = containingKtFile.text
            val start = source.lastIndexOf('\n', textRange.startOffset - 1) + 1
            val end = source.indexOf('\n', textRange.endOffset).takeIf { it >= 0 } ?: source.length
            return source.substring(start, end).lineSequence().maxOf(String::length)
        }

        private fun KtElement.lineIndent(): String {
            val source = containingKtFile.text
            val start = source.lastIndexOf('\n', textRange.startOffset - 1) + 1
            return source.substring(start, textRange.startOffset).takeWhile(Char::isWhitespace)
        }

        private const val MAX_LINE_LENGTH: Int = 120
        private const val CONTINUATION_INDENT: Int = 8
        private val logicalOperatorPattern: Regex = Regex("\\s+(&&|\\|\\|)\\s+")
    }
}
