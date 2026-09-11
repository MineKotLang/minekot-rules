package org.minekot.rules.internal

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.minekot.inspections.core.*

/** Common lifecycle boundary for PSI-only inspections. */
public abstract class PsiInspection : MineKotInspection {
    final override fun createSession(context: InspectionContext): MineKotInspectionSession =
        object : MineKotInspectionSession {
            override fun createVisitor(reporter: InspectionReporter): KtVisitorVoid = createVisitor(context, reporter)
        }

    /** Creates the isolated visitor for one file. */
    protected abstract fun createVisitor(context: InspectionContext, reporter: InspectionReporter): KtVisitorVoid
}

/** Returns whether the element is inside comment-delimited formatter control. */
internal fun KtElement.isInsideFormatterControl(): Boolean {
    var disabledDepth = 0
    containingKtFile.collectDescendantsOfType<PsiComment>()
        .asSequence()
        .takeWhile { it.textRange.startOffset < textRange.startOffset }
        .flatMap { formatterTagPattern.findAll(it.text) }
        .forEach { match ->
            if (match.groupValues[1] == "off") disabledDepth++ else if (disabledDepth > 0) disabledDepth--
        }
    return disabledDepth > 0
}

private val formatterTagPattern: Regex = Regex("@formatter:(off|on)")

/** Reports one finding after applying the host suppression policy. */
internal fun InspectionReporter.report(
    context: InspectionContext,
    descriptor: InspectionDescriptor,
    element: PsiElement,
    messageId: String,
    message: String,
    corrections: List<CorrectionPlan> = emptyList(),
) {
    val suppressionElement = element as? KtElement ?: context.file
    if (context.suppressionResolver.isSuppressed(descriptor.id, suppressionElement)) return
    report(
        InspectionFinding(
            ruleId = descriptor.id,
            messageId = messageId,
            message = message,
            messageArguments = emptyList(),
            startOffset = element.textRange.startOffset,
            endOffset = element.textRange.endOffset,
            severity = descriptor.defaultSeverity,
            corrections = corrections,
        ),
    )
}
