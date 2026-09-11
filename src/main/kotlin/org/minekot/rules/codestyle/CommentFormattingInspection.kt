package org.minekot.rules.codestyle

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.minekot.inspections.core.*
import org.minekot.rules.internal.*

/** Enforces comment indentation and marker spacing. */
public class CommentFormattingInspection : PsiInspection() {
    override fun createVisitor(context: InspectionContext, reporter: InspectionReporter): KtVisitorVoid =
        object : KtTreeVisitorVoid() {
            override fun visitComment(comment: PsiComment) {
                super.visitComment(comment)
                val text = comment.text
                if (text.startsWith("/**") || text.contains("@formatter:")) return

                val edits = buildList {
                    comment.indentationEdit(context)?.let(::add)
                    val start = comment.textRange.startOffset
                    if (
                        text.startsWith("//") && !text.startsWith("// ") ||
                        text.startsWith("/*") && !text.startsWith("/* ")
                    ) {
                        add(TextEdit(start + 2, start + 2, "", " "))
                    }
                    if (text.startsWith("/*") && text.endsWith("*/") && !text.endsWith(" */")) {
                        val offset = comment.textRange.endOffset - 2
                        add(TextEdit(offset, offset, "", " "))
                    }
                }.sortedBy { it.startOffset }
                if (edits.isEmpty()) return

                reporter.report(
                    context,
                    DESCRIPTOR,
                    comment,
                    "comment-formatting",
                    DESCRIPTOR.message("comment-formatting"),
                    listOf(
                        CorrectionPlan(
                            id = "${DESCRIPTOR.id}.format-comment",
                            label = DESCRIPTOR.correctionLabel("format-comment"),
                            fileId = context.fileId,
                            sourceSha256 = context.sourceSha256,
                            edits = edits,
                        ),
                    ),
                )
            }
        }

    /** Stable descriptor for comment formatting. */
    public companion object {
        /** Rule metadata exposed by the catalog. */
        public val DESCRIPTOR: InspectionDescriptor = ruleDescriptor(
            id = RuleContracts.COMMENT_FORMATTING,
            aliases = setOf(RuleContracts.COMMENT_FORMATTING_ALIAS),
            category = InspectionCategory.CODE_STYLE,
            severity = RuleSeverity.WARNING,
        )

        private fun PsiComment.indentationEdit(context: InspectionContext): TextEdit? {
            val start = textRange.startOffset
            val lineStart = context.sourceText.lastIndexOf('\n', start - 1) + 1
            val indentation = context.sourceText.substring(lineStart, start)
            if (indentation.any { !it.isWhitespace() }) return null
            val reference = indentationReference() ?: return null
            val referenceStart = reference.textRange.startOffset
            val referenceLineStart = context.sourceText.lastIndexOf('\n', referenceStart - 1) + 1
            val expected = context.sourceText.substring(
                referenceLineStart,
                referenceStart,
            ).takeWhile(Char::isWhitespace)
            if (indentation == expected) return null
            return TextEdit(lineStart, start, indentation, expected)
        }

        private fun PsiComment.indentationReference(): PsiElement? =
            generateSequence(prevSibling, PsiElement::getPrevSibling)
                .firstOrNull { it.isCodeIndentReference() }
                ?: generateSequence(nextSibling, PsiElement::getNextSibling)
                    .firstOrNull { it.isCodeIndentReference() }

        private fun PsiElement.isCodeIndentReference(): Boolean =
            this !is PsiWhiteSpace && this !is PsiComment && text !in indentationStructuralTokens

        private val indentationStructuralTokens: Set<String> = setOf("{", "}", "(", ")", "[", "]", ",")
    }
}
