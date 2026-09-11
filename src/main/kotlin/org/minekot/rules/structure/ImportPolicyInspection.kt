package org.minekot.rules.structure

import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.minekot.inspections.core.*
import org.minekot.rules.internal.*

/** Enforces wildcard thresholds and nested-class import policy. */
public class ImportPolicyInspection : PsiInspection() {
    override fun createVisitor(context: InspectionContext, reporter: InspectionReporter): KtVisitorVoid =
        object : KtTreeVisitorVoid() {
            override fun visitKtFile(file: KtFile) {
                super.visitKtFile(file)
                file.reportMissingWildcardImports()
            }

            override fun visitImportDirective(importDirective: KtImportDirective) {
                super.visitImportDirective(importDirective)
                val segments = importDirective.importPath?.pathStr?.split('.') ?: return
                if (
                    !importDirective.isAllUnder && segments.size >= 2 &&
                    segments.takeLast(2).all { it.firstOrNull()?.isUpperCase() == true }
                ) {
                    reporter.report(
                        context,
                        DESCRIPTOR,
                        importDirective,
                        "nested-class-import",
                        DESCRIPTOR.message("nested-class-import"),
                    )
                }
            }

            private fun KtFile.reportMissingWildcardImports() {
                importDirectives.filterNot { it.isAllUnder || it.aliasName != null }
                    .groupBy { it.importedFqName?.parent()?.asString().orEmpty() }
                    .forEach { (scope, imports) ->
                        if (scope.substringAfterLast('.').firstOrNull()?.isUpperCase() == true) return@forEach
                        val usesOnDemandThreshold = scope == JAVA_UTIL_PACKAGE ||
                                RECURSIVE_ON_DEMAND_PACKAGES.any { packageName ->
                                    scope == packageName || scope.startsWith("${packageName}.")
                                }
                        val threshold = when {
                            usesOnDemandThreshold -> ON_DEMAND_WILDCARD_THRESHOLD
                            else -> PACKAGE_IMPORT_THRESHOLD
                        }
                        if (scope.isBlank() || imports.size < threshold) return@forEach
                        val correction = wildcardCorrection(context, scope, imports)?.let(::listOf).orEmpty()
                        reporter.report(
                            context,
                            DESCRIPTOR,
                            imports.first(),
                            "wildcard-threshold",
                            DESCRIPTOR.message("wildcard-threshold", imports.size, scope),
                            correction,
                        )
                    }
            }
        }

    /** Stable descriptor for import policy. */
    public companion object {
        /** Rule metadata exposed by the catalog. */
        public val DESCRIPTOR: InspectionDescriptor = ruleDescriptor(
            id = RuleContracts.IMPORT_POLICY,
            aliases = setOf(RuleContracts.IMPORT_POLICY_ALIAS),
            category = InspectionCategory.CODE_STYLE,
            severity = RuleSeverity.WARNING,
        )

        private fun wildcardCorrection(
            context: InspectionContext,
            scope: String,
            imports: List<KtImportDirective>,
        ): CorrectionPlan? {
            if (imports.any { it.text.contains("//") || it.text.contains("/*") }) return null
            val first = imports.minByOrNull { it.textRange.startOffset } ?: return null
            val edits = buildList {
                add(
                    TextEdit(
                        first.textRange.startOffset,
                        first.textRange.endOffset,
                        first.text,
                        "import ${scope}.*",
                    ),
                )
                imports.filterNot { it == first }.forEach { directive ->
                    val end = if (context.sourceText.getOrNull(directive.textRange.endOffset) == '\n') {
                        directive.textRange.endOffset + 1
                    } else {
                        directive.textRange.endOffset
                    }
                    add(
                        TextEdit(
                            directive.textRange.startOffset,
                            end,
                            context.sourceText.substring(directive.textRange.startOffset, end),
                            "",
                        ),
                    )
                }
            }.sortedBy { it.startOffset }
            return CorrectionPlan(
                id = "${DESCRIPTOR.id}.use-wildcard",
                label = DESCRIPTOR.correctionLabel("replace-with-wildcard"),
                fileId = context.fileId,
                sourceSha256 = context.sourceSha256,
                edits = edits,
            )
        }

        private const val PACKAGE_IMPORT_THRESHOLD: Int = 5
        private const val ON_DEMAND_WILDCARD_THRESHOLD: Int = 1
        private const val JAVA_UTIL_PACKAGE: String = "java.util"
        private val RECURSIVE_ON_DEMAND_PACKAGES: Set<String> = setOf("io.ktor", "kotlinx.android.synthetic")
    }
}
