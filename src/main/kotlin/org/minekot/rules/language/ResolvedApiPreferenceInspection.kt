package org.minekot.rules.language

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.minekot.inspections.core.*
import org.minekot.rules.internal.*

/** Reports direct JDK APIs when a Kotlin, kotlinx, or MineKot layer owns the capability. */
public class ResolvedApiPreferenceInspection : PsiInspection() {
    override fun createVisitor(context: InspectionContext, reporter: InspectionReporter): KtVisitorVoid =
        object : KtTreeVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                if (context.isInsideBoundary(context.boundaryPrefixes())) return
                val callableId = context.resolveCall(expression) ?: return
                val preferenceKey = callableId.mineKotReplacement(context, expression) ?:
                    resolvedPreferences.entries.firstOrNull {
                    callableId == it.key || callableId.startsWith("${it.key}.")
                }?.value ?: return
                reporter.report(
                    context,
                    DESCRIPTOR,
                    expression,
                    "prefer-kotlin-api",
                    DESCRIPTOR.message(
                        "prefer-kotlin-api",
                        MineKotRulesBundle.message("replacement.${preferenceKey}"),
                        callableId,
                    ),
                )
            }
        }

    /** Stable descriptor for resolved API preference. */
    public companion object {
        /** Rule metadata exposed by the catalog. */
        public val DESCRIPTOR: InspectionDescriptor = ruleDescriptor(
            id = RuleContracts.RESOLVED_API_PREFERENCE,
            aliases = setOf(RuleContracts.RESOLVED_API_PREFERENCE_ALIAS),
            category = InspectionCategory.CODE_STYLE,
            severity = RuleSeverity.WARNING,
            options = listOf(
                InspectionOptionDescriptor.ListOption(
                    key = "boundaryPackagePrefixes",
                    description = MineKotRulesBundle.message("option.boundary-package-prefixes.description"),
                    defaultValue = InspectionOptionValue.ListValue(
                        listOf(
                            InspectionOptionValue.StringValue("org.minekot.kotlin"),
                            InspectionOptionValue.StringValue("org.minekot.platform"),
                        ),
                    ),
                    elementType = InspectionOptionType.STRING,
                ),
            ),
        )

        private fun InspectionContext.boundaryPrefixes(): List<String> =
            (options["boundaryPackagePrefixes"] as InspectionOptionValue.ListValue).value
                .map { (it as InspectionOptionValue.StringValue).value }

        private fun String.mineKotReplacement(
            context: InspectionContext,
            expression: KtCallExpression,
        ): String? {
            if (this != "kotlin.runCatching") return null
            val facts = (context.capabilities as? ExpressionFactsCapability)?.expressionFacts(expression) ?: return null
            if (!facts.suspendContext) return null
            val availability = context.capabilities as? SymbolAvailabilityCapability ?: return null
            return if (availability.isSymbolAvailable(MINEKOT_CATCHING_SYMBOL)) {
                "minekot-catching-cancellable"
            } else {
                null
            }
        }

        private val resolvedPreferences: Map<String, String> = mapOf(
            "java.io.BufferedInputStream.<init>" to "kotlinx-io-or-minekot-wrapper",
            "java.io.BufferedOutputStream.<init>" to "kotlinx-io-or-minekot-wrapper",
            "java.io.File.<init>" to "path-plus-minekot-wrapper",
            "java.io.FileInputStream.<init>" to "kotlinx-io-or-minekot-wrapper",
            "java.io.FileOutputStream.<init>" to "kotlinx-io-or-minekot-wrapper",
            "java.io.FileReader.<init>" to "kotlinx-io-or-minekot-wrapper",
            "java.io.FileWriter.<init>" to "kotlinx-io-or-minekot-wrapper",
            "java.nio.file.Files" to "kotlin-path-kotlinx-io-or-minekot-wrapper",
            "java.util.ArrayList.<init>" to "kotlin-mutable-list-factory",
            "java.util.HashMap.<init>" to "kotlin-mutable-map-factory",
            "java.util.HashSet.<init>" to "kotlin-mutable-set-factory",
        )
        private const val MINEKOT_CATCHING_SYMBOL: String =
            "org.minekot.kotlin.coroutines.runMineKotCatchingCancellable"
    }
}
