package org.minekot.rules.performance

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.minekot.inspections.core.*
import org.minekot.rules.internal.*

/** Detects resolved blocking calls in host-proven main-thread suspend contexts. */
public class MainThreadBlockingInspection : PsiInspection() {
    override fun createVisitor(context: InspectionContext, reporter: InspectionReporter): KtVisitorVoid =
        object : KtTreeVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                val callableId = context.resolveCall(expression) ?: return
                if (callableId !in blockingCallableIds) return
                val facts = (context.capabilities as? ExpressionFactsCapability)
                    ?.expressionFacts(expression)
                    ?: return
                if (!facts.mainThreadContext) return
                val corrections = if (facts.suspendContext) {
                    listOf(expression.ioContextCorrection(context, context.mineKotIoAvailable()))
                } else {
                    emptyList()
                }
                reporter.report(
                    context,
                    DESCRIPTOR,
                    expression,
                    "blocking-main-thread-call",
                    DESCRIPTOR.message("blocking-main-thread-call", callableId),
                    corrections,
                )
            }
        }

    /** Stable descriptor for platform-thread blocking protection. */
    public companion object {
        /** Rule metadata exposed by catalog. */
        public val DESCRIPTOR: InspectionDescriptor = ruleDescriptor(
            id = RuleContracts.MAIN_THREAD_BLOCKING,
            aliases = setOf(RuleContracts.MAIN_THREAD_BLOCKING_ALIAS),
            category = InspectionCategory.CONCURRENCY,
            severity = RuleSeverity.ERROR,
        )

        private fun InspectionContext.mineKotIoAvailable(): Boolean =
            (capabilities as? SymbolAvailabilityCapability)?.isSymbolAvailable(MINEKOT_IO_SYMBOL) == true

        private fun KtCallExpression.ioContextCorrection(
            context: InspectionContext,
            useMineKotHelper: Boolean,
        ): CorrectionPlan =
            CorrectionPlan(
                id = if (useMineKotHelper) {
                    "${DESCRIPTOR.id}.run-minekot-io"
                } else {
                    "${DESCRIPTOR.id}.wrap-io-context"
                },
                label = DESCRIPTOR.correctionLabel(
                    if (useMineKotHelper) "run-minekot-io" else "wrap-io-context",
                ),
                fileId = context.fileId,
                sourceSha256 = context.sourceSha256,
                edits = listOf(
                    TextEdit(
                        startOffset = textRange.startOffset,
                        endOffset = textRange.endOffset,
                        expectedText = text,
                        replacement = if (useMineKotHelper) {
                            "org.minekot.kotlin.coroutines.mineKotIo { ${text} }"
                        } else {
                            "kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ${text} }"
                        },
                    ),
                ),
            )

        private val blockingCallableIds: Set<String> = setOf(
            "java.lang.Thread.sleep",
            "java.nio.file.Files.copy",
            "java.nio.file.Files.readAllBytes",
            "java.nio.file.Files.readString",
            "java.nio.file.Files.write",
            "java.nio.file.Files.writeString",
            "java.sql.PreparedStatement.execute",
            "java.sql.PreparedStatement.executeQuery",
            "java.sql.PreparedStatement.executeUpdate",
            "java.sql.Statement.execute",
            "java.sql.Statement.executeQuery",
            "java.sql.Statement.executeUpdate",
            "kotlin.io.path.readBytes",
            "kotlin.io.path.readText",
            "kotlin.io.path.writeBytes",
            "kotlin.io.path.writeText",
        )
        private const val MINEKOT_IO_SYMBOL: String = "org.minekot.kotlin.coroutines.mineKotIo"
    }
}
