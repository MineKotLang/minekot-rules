package org.minekot.rules.codestyle

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.minekot.inspections.core.*
import org.minekot.rules.internal.*

/** Enforces deterministic whitespace rules shared by CLI and IDE hosts. */
public class WhitespaceFormattingInspection : PsiInspection() {
    override fun createVisitor(context: InspectionContext, reporter: InspectionReporter): KtVisitorVoid =
        object : KtTreeVisitorVoid() {
            override fun visitKtFile(file: KtFile) {
                super.visitKtFile(file)
                reportLineWhitespace(file)
            }

            override fun visitClassBody(classBody: KtClassBody) {
                super.visitClassBody(classBody)
                reportEmptyFirstLine(classBody)
            }

            override fun visitBlockExpression(expression: KtBlockExpression) {
                super.visitBlockExpression(expression)
                if (expression.parent is KtNamedFunction) reportEmptyFirstLine(expression)
            }

            override fun visitBinaryExpression(expression: KtBinaryExpression) {
                super.visitBinaryExpression(expression)
                if (expression.isInsideFormatterControl()) return
                val left = expression.left ?: return
                val right = expression.right ?: return
                val operation = expression.operationReference
                val expected = if (operation.text in noSpaceBinaryOperators) "" else " "
                reportSpacing(
                    element = expression,
                    messageId = "binary-operator-spacing",
                    message = DESCRIPTOR.message("binary-operator-spacing"),
                    highlightedElement = operation,
                    gaps = listOf(
                        left.textRange.endOffset to operation.textRange.startOffset,
                        operation.textRange.endOffset to right.textRange.startOffset,
                    ),
                    expected = expected,
                )
            }

            override fun visitUnaryExpression(expression: KtUnaryExpression) {
                super.visitUnaryExpression(expression)
                if (expression.isInsideFormatterControl()) return
                val base = expression.baseExpression ?: return
                val operation = expression.operationReference
                val gap = if (operation.textRange.startOffset < base.textRange.startOffset) {
                    operation.textRange.endOffset to base.textRange.startOffset
                } else {
                    base.textRange.endOffset to operation.textRange.startOffset
                }
                reportSpacing(
                    element = expression,
                    messageId = "unary-operator-spacing",
                    message = DESCRIPTOR.message("unary-operator-spacing"),
                    highlightedElement = operation,
                    gaps = listOf(gap),
                    expected = "",
                )
            }

            override fun visitIsExpression(expression: KtIsExpression) {
                super.visitIsExpression(expression)
                val type = expression.typeReference ?: return
                reportTypeOperatorSpacing(expression, expression.leftHandSide, expression.operationReference, type)
            }

            override fun visitBinaryWithTypeRHSExpression(expression: KtBinaryExpressionWithTypeRHS) {
                super.visitBinaryWithTypeRHSExpression(expression)
                val left = expression.left
                val type = expression.right ?: return
                reportTypeOperatorSpacing(expression, left, expression.operationReference, type)
            }

            override fun visitWhenExpression(expression: KtWhenExpression) {
                super.visitWhenExpression(expression)
                if (expression.isInsideFormatterControl()) return
                expression.entries.zipWithNext().forEach { (first, second) ->
                    val gapStart = first.textRange.endOffset
                    val gap = context.sourceText.substring(gapStart, second.textRange.startOffset)
                    val match = blankLinePattern.find(gap) ?: return@forEach
                    val range = match.groups[1]?.range ?: return@forEach
                    val start = gapStart + range.first
                    val end = gapStart + range.last + 1
                    reportRange(
                        expression,
                        start,
                        end,
                        "blank-line-between-when-branches",
                        DESCRIPTOR.message("blank-line-between-when-branches"),
                        listOf(edit(start, end, "")),
                    )
                }
            }

            override fun visitElement(element: PsiElement) {
                super.visitElement(element)
                when (element.node.elementType) {
                    KtTokens.COMMA -> reportCommaSpacing(element)
                    KtTokens.COLON -> reportColonSpacing(element)
                    KtTokens.ARROW -> reportArrowSpacing(element)
                    KtTokens.LBRACE -> reportLeftBraceSpacing(element)
                    KtTokens.RBRACE -> reportRightBraceSpacing(element)
                }
            }

            private fun reportLineWhitespace(file: KtFile) {
                var offset = 0
                var formatterDepth = 0
                val stringRanges = file.collectDescendantsOfType<KtStringTemplateExpression>()
                    .map { expression -> expression.textRange }
                context.sourceText.split('\n').forEach { line ->
                    val formatterWasEnabled = formatterDepth == 0
                    formatterTagPattern.findAll(line).forEach { match ->
                        if (match.groupValues[1] == "off") {
                            formatterDepth++
                        } else if (formatterDepth > 0) {
                            formatterDepth--
                        }
                    }
                    val lineEnd = offset + line.length
                    val isStringContent = stringRanges.any { range ->
                        offset < range.endOffset && range.startOffset < lineEnd
                    }
                    if (formatterWasEnabled && formatterDepth == 0 && !isStringContent) {
                        val indentation = line.takeWhile { character -> character == ' ' || character == '\t' }
                        if ('\t' in indentation) {
                            reportRange(
                                file.suppressionElementAt(offset),
                                offset,
                                offset + indentation.length,
                                "tab-indentation",
                                DESCRIPTOR.message("tab-indentation"),
                                listOf(edit(offset, offset + indentation.length, indentation.expandTabs())),
                            )
                        } else if (indentation.length % INDENT_SIZE != 0) {
                            reportRange(
                                file.suppressionElementAt(offset),
                                offset,
                                offset + indentation.length,
                                "indentation-width",
                                DESCRIPTOR.message("indentation-width"),
                            )
                        }
                        if (line.isNotBlank()) {
                            val trailing = line.takeLastWhile { character -> character == ' ' || character == '\t' }
                            if (trailing.isNotEmpty()) {
                                val start = offset + line.length - trailing.length
                                reportRange(
                                    file.suppressionElementAt(offset),
                                    start,
                                    offset + line.length,
                                    "trailing-whitespace",
                                    DESCRIPTOR.message("trailing-whitespace"),
                                    listOf(edit(start, offset + line.length, "")),
                                )
                            }
                        }
                    }
                    offset += line.length + 1
                }
            }

            private fun KtFile.suppressionElementAt(offset: Int): KtElement =
                findElementAt(offset)?.let { element ->
                    PsiTreeUtil.getParentOfType(element, KtElement::class.java, false)
                } ?: this

            private fun reportEmptyFirstLine(body: KtElement) {
                if (body.isInsideFormatterControl()) return
                val match = emptyFirstBodyLinePattern.find(body.text) ?: return
                val range = match.groups[1]?.range ?: return
                reportRange(
                    body,
                    body.textRange.startOffset + range.first,
                    body.textRange.startOffset + range.last + 1,
                    "empty-first-body-line",
                    DESCRIPTOR.message("empty-first-body-line"),
                )
            }

            private fun reportCommaSpacing(comma: PsiElement) {
                val container = comma.parent as? KtElement ?: return
                if (container.isInsideFormatterControl()) return
                val source = context.sourceText
                val beforeStart = source.whitespaceStartBefore(comma.textRange.startOffset)
                val afterEnd = source.whitespaceEndAfter(comma.textRange.endOffset)
                val next = source.getOrNull(afterEnd)
                val expectedAfter = when (next) {
                    null, '\n', '\r', in closingDelimiters -> ""
                    else -> " "
                }
                reportSpacing(
                    element = container,
                    messageId = "comma-spacing",
                    message = DESCRIPTOR.message("comma-spacing"),
                    highlightedElement = comma,
                    gaps = listOf(beforeStart to comma.textRange.startOffset),
                    expected = "",
                    additionalGaps = listOf(Triple(comma.textRange.endOffset, afterEnd, expectedAfter)),
                )
            }

            private fun reportColonSpacing(colon: PsiElement) {
                val container = colon.parent as? KtElement ?: return
                if (container.isInsideFormatterControl()) return
                val source = context.sourceText
                val beforeStart = source.whitespaceStartBefore(colon.textRange.startOffset)
                val afterEnd = source.whitespaceEndAfter(colon.textRange.endOffset)
                if ('\n' in source.substring(beforeStart, afterEnd)) return
                val expectedBefore = if (container is KtClass || container is KtTypeParameter) " " else ""
                reportSpacing(
                    element = container,
                    messageId = "colon-spacing",
                    message = DESCRIPTOR.message("colon-spacing"),
                    highlightedElement = colon,
                    gaps = emptyList(),
                    expected = "",
                    additionalGaps = listOf(
                        Triple(beforeStart, colon.textRange.startOffset, expectedBefore),
                        Triple(colon.textRange.endOffset, afterEnd, " "),
                    ),
                )
            }

            private fun reportArrowSpacing(arrow: PsiElement) {
                val container = arrow.parent as? KtElement ?: return
                if (container.isInsideFormatterControl()) return
                val source = context.sourceText
                val gaps = buildList {
                    val beforeStart = source.whitespaceStartBefore(arrow.textRange.startOffset)
                    val before = source.getOrNull(beforeStart - 1)
                    if (before != null && before != '\n' && before != '\r') {
                        add(Triple(beforeStart, arrow.textRange.startOffset, " "))
                    }
                    val afterEnd = source.whitespaceEndAfter(arrow.textRange.endOffset)
                    val after = source.getOrNull(afterEnd)
                    if (after != null && after != '\n' && after != '\r') {
                        add(Triple(arrow.textRange.endOffset, afterEnd, " "))
                    }
                }
                reportSpacing(
                    element = container,
                    messageId = "arrow-spacing",
                    message = DESCRIPTOR.message("arrow-spacing"),
                    highlightedElement = arrow,
                    gaps = emptyList(),
                    expected = "",
                    additionalGaps = gaps,
                )
            }

            private fun reportTypeOperatorSpacing(
                expression: KtElement,
                left: KtElement,
                operation: KtElement,
                right: KtElement,
            ) {
                if (expression.isInsideFormatterControl()) return
                reportSpacing(
                    element = expression,
                    messageId = "type-operator-spacing",
                    message = DESCRIPTOR.message("type-operator-spacing"),
                    highlightedElement = operation,
                    gaps = listOf(
                        left.textRange.endOffset to operation.textRange.startOffset,
                        operation.textRange.endOffset to right.textRange.startOffset,
                    ),
                    expected = " ",
                )
            }

            private fun reportLeftBraceSpacing(brace: PsiElement) {
                val container = brace.parent as? KtElement ?: return
                if (container.isInsideFormatterControl() || container.isInsideStringTemplate()) return
                val source = context.sourceText
                val gaps = buildList {
                    val before = source.whitespaceStartBefore(brace.textRange.startOffset)
                    val previous = source.getOrNull(before - 1)
                    if (previous != null && previous !in leftBracePrefixDelimiters) {
                        add(Triple(before, brace.textRange.startOffset, " "))
                    }
                    val after = source.whitespaceEndAfter(brace.textRange.endOffset)
                    val next = source.getOrNull(after)
                    if (next != null && next != '\n' && next != '\r' && next != '}') {
                        add(Triple(brace.textRange.endOffset, after, " "))
                    }
                }
                reportSpacing(
                    element = container,
                    messageId = "curly-brace-spacing",
                    message = DESCRIPTOR.message("curly-brace-spacing"),
                    highlightedElement = brace,
                    gaps = emptyList(),
                    expected = "",
                    additionalGaps = gaps,
                )
            }

            private fun reportRightBraceSpacing(brace: PsiElement) {
                val container = brace.parent as? KtElement ?: return
                if (container.isInsideFormatterControl() || container.isInsideStringTemplate()) return
                val source = context.sourceText
                val whitespaceStart = source.whitespaceStartBeforeIncludingLines(brace.textRange.startOffset)
                val whitespace = source.substring(whitespaceStart, brace.textRange.startOffset)
                if (whitespace.count { character -> character == '\n' } >= 2) {
                    val closingIndent = whitespace.substringAfterLast('\n')
                    reportRange(
                        container,
                        whitespaceStart,
                        brace.textRange.startOffset,
                        "blank-line-before-closing-brace",
                        DESCRIPTOR.message("blank-line-before-closing-brace"),
                        listOf(edit(whitespaceStart, brace.textRange.startOffset, "\n${closingIndent}")),
                    )
                    return
                }
                val before = source.whitespaceStartBefore(brace.textRange.startOffset)
                val previous = source.getOrNull(before - 1)
                if (previous == null || previous == '\n' || previous == '\r' || previous == '{') return
                reportSpacing(
                    element = container,
                    messageId = "curly-brace-spacing",
                    message = DESCRIPTOR.message("curly-brace-spacing"),
                    highlightedElement = brace,
                    gaps = listOf(before to brace.textRange.startOffset),
                    expected = " ",
                )
            }

            private fun reportSpacing(
                element: KtElement,
                messageId: String,
                message: String,
                highlightedElement: PsiElement,
                gaps: List<Pair<Int, Int>>,
                expected: String,
                additionalGaps: List<Triple<Int, Int, String>> = emptyList(),
            ) {
                val replacements =
                    gaps.map { (start, end) -> Triple(start, end, expected) } + additionalGaps
                val edits = replacements.mapNotNull { (start, end, replacement) ->
                    val actual = context.sourceText.substring(start, end)
                    if (
                        '\n' in actual ||
                        '\r' in actual ||
                        actual.any { !it.isWhitespace() } ||
                        actual == replacement
                    ) {
                        null
                    } else {
                        edit(start, end, replacement)
                    }
                }
                if (edits.isEmpty()) return
                reportRange(
                    element,
                    highlightedElement.textRange.startOffset,
                    highlightedElement.textRange.endOffset,
                    messageId,
                    message,
                    edits,
                )
            }

            private fun edit(start: Int, end: Int, replacement: String): TextEdit =
                TextEdit(start, end, context.sourceText.substring(start, end), replacement)

            private fun reportRange(
                suppressionElement: KtElement,
                start: Int,
                end: Int,
                messageId: String,
                message: String,
                edits: List<TextEdit> = emptyList(),
            ) {
                if (context.suppressionResolver.isSuppressed(DESCRIPTOR.id, suppressionElement)) return
                val corrections = if (edits.isEmpty()) {
                    emptyList()
                } else {
                    listOf(
                        CorrectionPlan(
                            id = "${DESCRIPTOR.id}.${messageId}",
                            label = message,
                            fileId = context.fileId,
                            sourceSha256 = context.sourceSha256,
                            edits = edits,
                        ),
                    )
                }
                reporter.report(
                    InspectionFinding(
                        ruleId = DESCRIPTOR.id,
                        messageId = messageId,
                        message = message,
                        messageArguments = emptyList(),
                        startOffset = start,
                        endOffset = end,
                        severity = DESCRIPTOR.defaultSeverity,
                        corrections = corrections,
                    ),
                )
            }
        }

    /** Stable descriptor for deterministic whitespace formatting. */
    public companion object {
        /** Rule metadata exposed by the catalog. */
        public val DESCRIPTOR: InspectionDescriptor = ruleDescriptor(
            id = RuleContracts.WHITESPACE_FORMATTING,
            aliases = setOf(RuleContracts.WHITESPACE_FORMATTING_ALIAS),
            category = InspectionCategory.CODE_STYLE,
            severity = RuleSeverity.WARNING,
        )

        private fun String.expandTabs(): String = buildString {
            this@expandTabs.forEach { character ->
                if (character == '\t') repeat(TAB_WIDTH - length % TAB_WIDTH) { append(' ') } else append(character)
            }
        }

        private fun String.whitespaceStartBefore(offset: Int): Int {
            var result = offset
            while (result > 0 && get(result - 1) in horizontalWhitespace) result--
            return result
        }

        private fun String.whitespaceEndAfter(offset: Int): Int {
            var result = offset
            while (result < length && get(result) in horizontalWhitespace) result++
            return result
        }

        private fun String.whitespaceStartBeforeIncludingLines(offset: Int): Int {
            var result = offset
            while (result > 0 && get(result - 1).isWhitespace()) result--
            return result
        }

        private fun KtElement.isInsideStringTemplate(): Boolean =
            generateSequence(parent) { element -> element.parent }.any { element ->
                element is KtStringTemplateExpression
            }

        private const val TAB_WIDTH: Int = 4
        private const val INDENT_SIZE: Int = 4
        private val horizontalWhitespace: Set<Char> = setOf(' ', '\t')
        private val closingDelimiters: Set<Char> = setOf(')', ']', '}', '>')
        private val leftBracePrefixDelimiters: Set<Char> = setOf('(', '[', '{', '.', '@')
        private val noSpaceBinaryOperators: Set<String> = setOf("..", "..<")
        private val formatterTagPattern: Regex = Regex("@formatter:(off|on)")
        private val blankLinePattern: Regex = Regex("\\n((?:[ \\t]*\\n)+)")
        private val emptyFirstBodyLinePattern: Regex = Regex("^\\{[ \\t]*\\n((?:[ \\t]*\\n)+)")
    }
}
