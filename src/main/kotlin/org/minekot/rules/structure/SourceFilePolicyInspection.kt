package org.minekot.rules.structure

import com.intellij.psi.PsiComment
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.minekot.inspections.core.*
import org.minekot.rules.internal.*

/** Enforces source encoding markers and formatter-control pairing. */
public class SourceFilePolicyInspection : PsiInspection() {
    override fun createVisitor(context: InspectionContext, reporter: InspectionReporter): KtVisitorVoid =
        object : KtTreeVisitorVoid() {
            override fun visitKtFile(file: KtFile) {
                super.visitKtFile(file)
                if (context.sourceText.startsWith('\uFEFF')) {
                    reporter.report(
                        context,
                        DESCRIPTOR,
                        file,
                        "utf8-bom",
                        DESCRIPTOR.message("utf8-bom"),
                        listOf(
                            CorrectionPlan(
                                id = "${DESCRIPTOR.id}.remove-bom",
                                label = DESCRIPTOR.correctionLabel("remove-utf8-bom"),
                                fileId = context.fileId,
                                sourceSha256 = context.sourceSha256,
                                edits = listOf(TextEdit(0, 1, "\uFEFF", "")),
                            ),
                        ),
                    )
                }
                reportFormatterTags(file)
            }

            private fun reportFormatterTags(file: KtFile) {
                val disabledRegions = ArrayDeque<PsiComment>()
                file.collectDescendantsOfType<PsiComment>().forEach { comment ->
                    formatterTagPattern.findAll(comment.text).forEach { match ->
                        when (match.groupValues[1]) {
                            "off" -> disabledRegions.addLast(comment)
                            "on" -> if (disabledRegions.isEmpty()) {
                                reporter.report(
                                    context,
                                    DESCRIPTOR,
                                    comment,
                                    "unmatched-formatter-on",
                                    DESCRIPTOR.message("unmatched-formatter-on"),
                                )
                            } else {
                                disabledRegions.removeLast()
                            }
                        }
                    }
                }
                disabledRegions.lastOrNull()?.let { comment ->
                    reporter.report(
                        context,
                        DESCRIPTOR,
                        comment,
                        "unclosed-formatter-off",
                        DESCRIPTOR.message("unclosed-formatter-off"),
                    )
                }
            }
        }

    /** Stable descriptor for source-file policy. */
    public companion object {
        /** Rule metadata exposed by the catalog. */
        public val DESCRIPTOR: InspectionDescriptor = ruleDescriptor(
            id = RuleContracts.SOURCE_FILE_POLICY,
            aliases = setOf(RuleContracts.SOURCE_FILE_POLICY_ALIAS),
            category = InspectionCategory.CODE_STYLE,
            severity = RuleSeverity.WARNING,
        )
        private val formatterTagPattern: Regex = Regex("@formatter:(off|on)")
    }
}
