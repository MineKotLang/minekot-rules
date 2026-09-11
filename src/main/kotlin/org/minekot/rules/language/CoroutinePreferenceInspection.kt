package org.minekot.rules.language

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.minekot.inspections.core.*
import org.minekot.rules.internal.*

/** Reports direct concurrency and scheduler APIs outside approved boundaries. */
public class CoroutinePreferenceInspection : PsiInspection() {
    override fun createVisitor(context: InspectionContext, reporter: InspectionReporter): KtVisitorVoid =
        object : KtTreeVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                if (context.isInsideBoundary(context.boundaryPrefixes())) return
                val callableId = context.resolveCall(expression) ?: return
                val replacementKey = blockedConcurrencyCalls[callableId] ?: return
                reporter.report(
                    context,
                    DESCRIPTOR,
                    expression,
                    "prefer-coroutines",
                    DESCRIPTOR.message(
                        "prefer-coroutines",
                        MineKotRulesBundle.message("replacement.${replacementKey}"),
                        callableId,
                    ),
                )
            }
        }

    /** Stable descriptor for concurrency API preference. */
    public companion object {
        /** Rule metadata exposed by the catalog. */
        public val DESCRIPTOR: InspectionDescriptor = ruleDescriptor(
            id = RuleContracts.COROUTINE_PREFERENCE,
            aliases = setOf(RuleContracts.COROUTINE_PREFERENCE_ALIAS),
            category = InspectionCategory.CONCURRENCY,
            severity = RuleSeverity.WARNING,
            options = listOf(boundaryOption()),
        )

        private fun boundaryOption(): InspectionOptionDescriptor.ListOption = InspectionOptionDescriptor.ListOption(
            key = "boundaryPackagePrefixes",
            description = MineKotRulesBundle.message("option.boundary-package-prefixes.description"),
            defaultValue = InspectionOptionValue.ListValue(
                listOf(
                    InspectionOptionValue.StringValue("org.minekot.kotlin"),
                    InspectionOptionValue.StringValue("org.minekot.platform"),
                ),
            ),
            elementType = InspectionOptionType.STRING,
        )

        private fun InspectionContext.boundaryPrefixes(): List<String> =
            (options["boundaryPackagePrefixes"] as InspectionOptionValue.ListValue).value
                .map { (it as InspectionOptionValue.StringValue).value }

        private val blockedConcurrencyCalls: Map<String, String> = mapOf(
            "java.lang.Thread.<init>" to "lifecycle-coroutine-scope",
            "java.lang.Thread.sleep" to "kotlin-coroutine-delay",
            "java.util.Timer.<init>" to "lifecycle-coroutine-scope",
            "java.util.concurrent.CompletableFuture.runAsync" to "coroutine-launch-or-async",
            "java.util.concurrent.CompletableFuture.supplyAsync" to "coroutine-async",
            "java.util.concurrent.Executors.newCachedThreadPool" to "lifecycle-coroutine-dispatcher",
            "java.util.concurrent.Executors.newFixedThreadPool" to "lifecycle-coroutine-dispatcher",
            "java.util.concurrent.Executors.newScheduledThreadPool" to "lifecycle-coroutine-dispatcher",
            "java.util.concurrent.Executors.newSingleThreadExecutor" to "lifecycle-coroutine-dispatcher",
            "org.bukkit.scheduler.BukkitScheduler.runTask" to "minekot-platform-dispatch",
            "org.bukkit.scheduler.BukkitScheduler.runTaskAsynchronously" to "minekot-platform-dispatch",
            "org.bukkit.scheduler.BukkitScheduler.runTaskLater" to "minekot-platform-dispatch",
            "org.bukkit.scheduler.BukkitScheduler.runTaskLaterAsynchronously" to "minekot-platform-dispatch",
            "org.bukkit.scheduler.BukkitScheduler.runTaskTimer" to "minekot-platform-dispatch",
            "org.bukkit.scheduler.BukkitScheduler.runTaskTimerAsynchronously" to "minekot-platform-dispatch",
        )
    }
}
