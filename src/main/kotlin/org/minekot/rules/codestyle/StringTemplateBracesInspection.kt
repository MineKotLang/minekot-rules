package org.minekot.rules.codestyle

import org.jetbrains.kotlin.psi.KtSimpleNameStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.minekot.inspections.core.*
import org.minekot.rules.internal.*

/** Requires braces around simple Kotlin string-template entries. */
public class StringTemplateBracesInspection : MineKotInspection {
    override fun createSession(context: InspectionContext): MineKotInspectionSession = Session(context)

    /** One isolated traversal session. */
    private class Session(private val context: InspectionContext) : MineKotInspectionSession {
        override fun createVisitor(reporter: InspectionReporter): KtVisitorVoid =
            object : KtTreeVisitorVoid() {
                override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) {
                    super.visitStringTemplateExpression(expression)
                    expression.entries.filterIsInstance<KtSimpleNameStringTemplateEntry>().forEach { entry ->
                        reportEntry(entry, reporter)
                    }
                }

                private fun reportEntry(entry: KtSimpleNameStringTemplateEntry, reporter: InspectionReporter) {
                    if (context.suppressionResolver.isSuppressed(DESCRIPTOR.id, entry)) return
                    val expression = entry.expression?.text ?: return
                    val range = entry.textRange
                    val replacement = "${'$'}{${expression}}"
                    reporter.report(
                        InspectionFinding(
                            ruleId = DESCRIPTOR.id,
                            messageId = "use-braces",
                            message = DESCRIPTOR.message("use-braces", replacement),
                            messageArguments = listOf(expression),
                            startOffset = range.startOffset,
                            endOffset = range.endOffset,
                            severity = DESCRIPTOR.defaultSeverity,
                            corrections = listOf(
                                CorrectionPlan(
                                    id = "${DESCRIPTOR.id}.add-braces",
                                    label = DESCRIPTOR.correctionLabel("add-braces"),
                                    fileId = context.fileId,
                                    sourceSha256 = context.sourceSha256,
                                    edits = listOf(
                                        TextEdit(
                                            startOffset = range.startOffset,
                                            endOffset = range.endOffset,
                                            expectedText = entry.text,
                                            replacement = replacement,
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    )
                }
            }
    }

    /** Descriptor contract shared by the catalog and runtime sessions. */
    public companion object {
        /** Stable descriptor for string-template brace policy. */
        public val DESCRIPTOR: InspectionDescriptor = ruleDescriptor(
            id = RuleContracts.STRING_TEMPLATE_BRACES,
            aliases = setOf(
                RuleContracts.STRING_TEMPLATE_BRACES_ALIAS,
                RuleContracts.STRING_TEMPLATE_BRACES_LEGACY_ID,
            ),
            category = InspectionCategory.CODE_STYLE,
            severity = RuleSeverity.WARNING,
        )
    }
}
