package org.minekot.rules.structure

import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.minekot.inspections.core.*
import org.minekot.rules.internal.*

/** Enforces deterministic repository placement, order, and concise Maven declarations. */
public class GradleRepositoryPolicyInspection : PsiInspection() {
    override fun createVisitor(context: InspectionContext, reporter: InspectionReporter): KtVisitorVoid =
        object : KtTreeVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                if (!expression.containingKtFile.name.endsWith(".gradle.kts")) return
                when {
                    expression.isRepositoryDeclarationOutsideBlock() -> report(expression, "outside-repositories-block")
                    expression.isVerboseMavenDeclaration() -> reportVerboseDeclaration(expression)
                    expression.calleeExpression?.text == "repositories" -> reportInvalidOrder(expression)
                }
            }

            private fun report(element: KtCallExpression, messageId: String) {
                reporter.report(context, DESCRIPTOR, element, messageId, DESCRIPTOR.message(messageId))
            }

            private fun reportInvalidOrder(expression: KtCallExpression) {
                val calls = expression.lambdaArguments.firstOrNull()?.getLambdaExpression()
                    ?.bodyExpression
                    ?.statements
                    ?.filterIsInstance<KtCallExpression>()
                    .orEmpty()
                val ranks = calls.mapNotNull { call -> call.repositoryRank() }
                if (ranks.zipWithNext().any { (first, second) -> first > second }) {
                    report(expression, "repository-order")
                }
            }

            private fun reportVerboseDeclaration(expression: KtCallExpression) {
                val match = simpleVerboseMavenPattern.matchEntire(expression.text)
                val corrections = match?.let { result ->
                    listOf(
                        CorrectionPlan(
                            id = "${DESCRIPTOR.id}.simplify-maven-repository",
                            label = DESCRIPTOR.correctionLabel("simplify-maven-repository"),
                            fileId = context.fileId,
                            sourceSha256 = context.sourceSha256,
                            edits = listOf(
                                TextEdit(
                                    expression.textRange.startOffset,
                                    expression.textRange.endOffset,
                                    expression.text,
                                    "maven(${result.groupValues[1]})",
                                ),
                            ),
                        ),
                    )
                }.orEmpty()
                reporter.report(
                    context,
                    DESCRIPTOR,
                    expression,
                    "verbose-maven-repository",
                    DESCRIPTOR.message("verbose-maven-repository"),
                    corrections,
                )
            }
        }

    /** Stable descriptor for Gradle repository policy. */
    public companion object {
        /** Rule metadata exposed by catalog. */
        public val DESCRIPTOR: InspectionDescriptor = ruleDescriptor(
            id = RuleContracts.GRADLE_REPOSITORY_POLICY,
            aliases = setOf(RuleContracts.GRADLE_REPOSITORY_POLICY_ALIAS),
            category = InspectionCategory.CODE_STYLE,
            severity = RuleSeverity.WARNING,
        )

        private fun KtCallExpression.isRepositoryDeclarationOutsideBlock(): Boolean =
            calleeExpression?.text in repositoryCalls && parents.filterIsInstance<KtCallExpression>().none {
                it.calleeExpression?.text == "repositories"
            }

        private fun KtCallExpression.isVerboseMavenDeclaration(): Boolean =
            calleeExpression?.text == "maven" && lambdaArguments.any { argument ->
                argument.getLambdaExpression()?.text?.contains(repositoryUrlAssignmentPattern) == true
            }

        private fun KtCallExpression.repositoryRank(): Int? = when (calleeExpression?.text) {
            "mavenLocal" -> LOCAL_REPOSITORY_RANK
            "maven" -> PROJECT_REPOSITORY_RANK
            "mavenCentral" -> CENTRAL_REPOSITORY_RANK
            "google", "gradlePluginPortal", "ivy" -> PUBLIC_REPOSITORY_RANK
            else -> null
        }

        private val repositoryCalls: Set<String> =
            setOf("google", "gradlePluginPortal", "ivy", "maven", "mavenCentral", "mavenLocal")
        private val repositoryUrlAssignmentPattern: Regex = Regex("url\\s*=\\s*uri\\(")
        private val simpleVerboseMavenPattern: Regex =
            Regex("maven\\s*\\{\\s*url\\s*=\\s*uri\\((\"[^\"]*\")\\)\\s*}")
        private const val LOCAL_REPOSITORY_RANK: Int = 0
        private const val PROJECT_REPOSITORY_RANK: Int = 1
        private const val CENTRAL_REPOSITORY_RANK: Int = 2
        private const val PUBLIC_REPOSITORY_RANK: Int = 3
    }
}
