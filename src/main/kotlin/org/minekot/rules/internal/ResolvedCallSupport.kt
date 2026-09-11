package org.minekot.rules.internal

import org.jetbrains.kotlin.psi.KtCallExpression
import org.minekot.inspections.core.InspectionContext
import org.minekot.inspections.core.ResolvedCallCapability

/** Resolves a call through the host capability, or returns null when semantic analysis is unavailable. */
internal fun InspectionContext.resolveCall(expression: KtCallExpression): String? =
    (capabilities as? ResolvedCallCapability)?.resolveCall(expression)?.callableId

/** Returns whether the file is within a configured platform boundary. */
internal fun InspectionContext.isInsideBoundary(prefixes: List<String>): Boolean {
    val packageName = file.packageFqName.asString()
    return prefixes.any { packageName == it || packageName.startsWith("${it}.") }
}
