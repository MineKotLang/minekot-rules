package org.minekot.rules.performance

import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.minekot.inspections.core.*
import org.minekot.rules.internal.*

/** Detects Gradle Kotlin DSL patterns that harm configuration avoidance or configuration-cache reuse. */
public class GradlePerformanceInspection : PsiInspection() {
    override fun createVisitor(context: InspectionContext, reporter: InspectionReporter): KtVisitorVoid =
        object : KtTreeVisitorVoid() {
            override fun visitClassOrObject(classOrObject: KtClassOrObject) {
                super.visitClassOrObject(classOrObject)
                if (!classOrObject.inGradleScript() || !classOrObject.isUnclassifiedTaskType()) return
                reporter.report(
                    context,
                    DESCRIPTOR,
                    classOrObject,
                    "missing-task-cache-policy",
                    DESCRIPTOR.message("missing-cache-annotation"),
                )
            }

            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                if (!expression.inGradleScript()) return
                val diagnostic = when {
                    expression.isAfterEvaluate() -> Diagnostic(
                        "after-evaluate",
                        DESCRIPTOR.message("after-evaluate"),
                    )
                    expression.realizesTaskProvider() -> Diagnostic(
                        "task-provider-realization",
                        DESCRIPTOR.message("task-provider-get"),
                    )
                    expression.eagerlyTraversesTasks() -> Diagnostic(
                        "eager-task-traversal",
                        DESCRIPTOR.message("eager-task-lookup"),
                    )
                    expression.resolvesConfiguration() -> Diagnostic(
                        "configuration-time-resolution",
                        DESCRIPTOR.message("eager-configuration-resolution"),
                    )
                    expression.readsSystemStateDuringConfiguration() -> Diagnostic(
                        "configuration-time-system-read",
                        DESCRIPTOR.message("ambient-system-input"),
                    )
                    expression.usesTaskGraphCallback() -> Diagnostic(
                        "task-graph-callback",
                        DESCRIPTOR.message("task-graph-callback"),
                    )
                    else -> null
                } ?: return
                reporter.report(context, DESCRIPTOR, expression, diagnostic.id, diagnostic.message)
            }
        }

    /** Stable descriptor for Gradle configuration performance. */
    public companion object {
        /** Rule metadata exposed by the catalog. */
        public val DESCRIPTOR: InspectionDescriptor = ruleDescriptor(
            id = RuleContracts.GRADLE_PERFORMANCE,
            aliases = setOf(RuleContracts.GRADLE_PERFORMANCE_ALIAS),
            category = InspectionCategory.PERFORMANCE,
            severity = RuleSeverity.WARNING,
        )

        private fun KtElement.inGradleScript(): Boolean = containingKtFile.name.endsWith(".gradle.kts")

        private fun KtClassOrObject.isUnclassifiedTaskType(): Boolean {
            val extendsDefaultTask = superTypeListEntries.any { entry ->
                entry.text.substringBefore('(').endsWith("DefaultTask")
            }
            if (!extendsDefaultTask) return false
            val hasTaskAction = declarations.filterIsInstance<KtNamedFunction>().any { function ->
                function.annotationEntries.any { annotation -> annotation.shortName?.asString() == "TaskAction" }
            }
            if (!hasTaskAction) return false
            return annotationEntries.none { annotation ->
                annotation.shortName?.asString() in taskCachePolicyAnnotations
            }
        }

        private fun KtCallExpression.isAfterEvaluate(): Boolean = calleeExpression?.text == "afterEvaluate"

        private fun KtCallExpression.realizesTaskProvider(): Boolean {
            if (calleeExpression?.text != "get" || isInsideTaskExecution()) return false
            val receiver = (parent as? KtDotQualifiedExpression)?.receiverExpression?.text ?: return false
            return taskProviderPattern.containsMatchIn(receiver)
        }

        private fun KtCallExpression.eagerlyTraversesTasks(): Boolean {
            if (calleeExpression?.text !in eagerTraversalCalls || isInsideTaskExecution()) return false
            val receiver = (parent as? KtDotQualifiedExpression)?.receiverExpression?.text ?: return false
            return receiver == "tasks" || receiver.endsWith(".tasks") || receiver.startsWith("tasks.")
        }

        private fun KtCallExpression.resolvesConfiguration(): Boolean {
            if (calleeExpression?.text !in configurationResolutionCalls || isInsideTaskExecution()) return false
            val receiver = (parent as? KtDotQualifiedExpression)?.receiverExpression?.text ?: return false
            return receiver == "configurations" || receiver.startsWith("configurations.") ||
                    receiver.contains("Configuration")
        }

        private fun KtCallExpression.readsSystemStateDuringConfiguration(): Boolean {
            if (calleeExpression?.text !in systemReadCalls || isInsideTaskExecution()) return false
            val qualified = parent as? KtDotQualifiedExpression ?: return false
            return qualified.receiverExpression.text in setOf("System", "java.lang.System")
        }

        private fun KtCallExpression.usesTaskGraphCallback(): Boolean {
            if (calleeExpression?.text !in taskGraphCallbackCalls) return false
            val receiver = (parent as? KtDotQualifiedExpression)?.receiverExpression?.text ?: return false
            return receiver.endsWith("taskGraph") || ".taskGraph." in receiver
        }

        private fun KtCallExpression.isInsideTaskExecution(): Boolean = parents.any { ancestor ->
            ancestor is KtNamedFunction && ancestor.annotationEntries.any { annotation ->
                annotation.shortName?.asString() == "TaskAction"
            } || ancestor is KtLambdaExpression && (ancestor.parent?.parent as? KtCallExpression)
                ?.calleeExpression?.text in taskExecutionCalls
        }

        private data class Diagnostic(val id: String, val message: String)

        private val taskCachePolicyAnnotations: Set<String> =
            setOf("CacheableTask", "DisableCachingByDefault", "UntrackedTask")
        private val eagerTraversalCalls: Set<String> = setOf("all", "filter", "forEach", "iterator", "map", "toList")
        private val configurationResolutionCalls: Set<String> = setOf("resolve", "singleFile")
        private val systemReadCalls: Set<String> = setOf("getenv", "getProperty")
        private val taskGraphCallbackCalls: Set<String> = setOf("addTaskExecutionGraphListener", "whenReady")
        private val taskExecutionCalls: Set<String> = setOf("doFirst", "doLast")
        private val taskProviderPattern: Regex =
            Regex("(?:^|\\.)tasks\\.(?:named|register|withType)(?:<[^>]+>)?\\s*\\(")
    }
}
