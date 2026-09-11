package org.minekot.rules.language

import com.intellij.psi.PsiComment
import org.jetbrains.kotlin.psi.*
import org.minekot.inspections.core.*
import org.minekot.rules.internal.*

/** Legacy syntactic preference rule retained for compatibility with existing configuration. */
public class KotlinxPreferenceInspection : MineKotInspection {
    override fun createSession(context: InspectionContext): MineKotInspectionSession = Session(context)

    private class Session(private val context: InspectionContext) : MineKotInspectionSession {
        private var reporter: InspectionReporter? = null
        private val imports: MutableList<KtImportDirective> = mutableListOf()
        private val pathCorrections: MutableList<PathCorrection> = mutableListOf()
        private var hasUnsupportedFilesCall: Boolean = false

        override fun createVisitor(reporter: InspectionReporter): KtVisitorVoid {
            this.reporter = reporter
            return object : KtTreeVisitorVoid() {
                override fun visitImportDirective(importDirective: KtImportDirective) {
                    super.visitImportDirective(importDirective)
                    val path = importDirective.importedFqName?.asString() ?: return
                    val replacementKey = blockedImports[path] ?: return
                    imports += importDirective
                    if (path != JAVA_NIO_FILES) {
                        reporter.report(
                            context,
                            DESCRIPTOR,
                            importDirective,
                            "prefer-kotlin-api",
                            DESCRIPTOR.message(
                                "prefer-kotlin-api",
                                MineKotRulesBundle.message("replacement.${replacementKey}"),
                                path,
                            ),
                        )
                    }
                }

                override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
                    super.visitDotQualifiedExpression(expression)
                    if (expression.receiverExpression.text != "Files") return
                    if (expression.isInsideFormatterControl() || expression.textContainsComment()) return
                    val correction = (expression.selectorExpression as? KtCallExpression)?.toPathCorrection(expression)
                    if (correction == null) hasUnsupportedFilesCall = true else pathCorrections += correction
                }
            }
        }

        override fun close() {
            val sink = reporter ?: return
            val filesImport = imports.firstOrNull { it.importedFqName?.asString() == JAVA_NIO_FILES } ?: return
            val edits = pathCorrectionEdits(filesImport)
            sink.report(
                context,
                DESCRIPTOR,
                filesImport,
                "prefer-kotlin-api",
                DESCRIPTOR.message(
                    "prefer-kotlin-api",
                    MineKotRulesBundle.message("replacement.kotlin-path-or-kotlinx-io"),
                    JAVA_NIO_FILES,
                ),
                if (edits.isEmpty()) emptyList() else listOf(
                    CorrectionPlan(
                        id = "${DESCRIPTOR.id}.migrate-files-calls",
                        label = DESCRIPTOR.correctionLabel("migrate-files-calls"),
                        fileId = context.fileId,
                        sourceSha256 = context.sourceSha256,
                        edits = edits,
                    ),
                ),
            )
            reporter = null
            imports.clear()
            pathCorrections.clear()
        }

        private fun pathCorrectionEdits(filesImport: KtImportDirective): List<TextEdit> {
            if (pathCorrections.isEmpty()) return emptyList()
            val edits = pathCorrections.map { correction ->
                TextEdit(
                    correction.expression.textRange.startOffset,
                    correction.expression.textRange.endOffset,
                    correction.expression.text,
                    correction.source,
                )
            }.toMutableList()
            val requiredImports = pathCorrections.map(PathCorrection::requiredImport).toSortedSet()
                .filterNot { required ->
                    context.file.importDirectives.any {
                        it.importedFqName?.asString() == required && it.aliasName == null
                    }
                }
            if (!hasUnsupportedFilesCall) {
                val end = filesImport.textRange.endOffset + context.sourceText
                    .substring(filesImport.textRange.endOffset).takeWhile { it == '\n' }.length
                val replacement = requiredImports.joinToString("\n") { "import ${it}" }
                    .let { if (it.isEmpty()) it else "${it}\n" }
                edits += TextEdit(
                    filesImport.textRange.startOffset,
                    end,
                    context.sourceText.substring(filesImport.textRange.startOffset, end),
                    replacement,
                )
            } else if (requiredImports.isNotEmpty()) {
                val anchor = context.file.importList?.textRange?.endOffset
                    ?: context.file.packageDirective?.textRange?.endOffset
                    ?: 0
                val prefix = if (anchor == 0) "" else "\n"
                val replacement = "${prefix}${requiredImports.joinToString("\n") { "import ${it}" }}\n"
                edits += TextEdit(anchor, anchor, "", replacement)
            }
            return edits.sortedBy(TextEdit::startOffset)
        }
    }

    /** Stable descriptor for the deprecated syntactic API preference rule. */
    public companion object {
        /** Rule metadata exposed by the catalog. */
        public val DESCRIPTOR: InspectionDescriptor = ruleDescriptor(
            id = RuleContracts.KOTLINX_PREFERENCE,
            aliases = setOf(RuleContracts.KOTLINX_PREFERENCE_ALIAS),
            category = InspectionCategory.CODE_STYLE,
            severity = RuleSeverity.INFO,
        )

        private fun KtDotQualifiedExpression.textContainsComment(): Boolean =
            node.getChildren(null).any { it.psi is PsiComment } || text.contains("/*") || text.contains("//")

        private fun KtCallExpression.toPathCorrection(expression: KtDotQualifiedExpression): PathCorrection? {
            val arguments = valueArguments.mapNotNull { it.getArgumentExpression()?.text }
            return when (calleeExpression?.text) {
                "exists" -> arguments.singleOrNull()?.let {
                    PathCorrection(expression, "${it}.exists()", "kotlin.io.path.exists")
                }
                "newInputStream" -> arguments.singleOrNull()?.let {
                    PathCorrection(expression, "${it}.inputStream()", "kotlin.io.path.inputStream")
                }
                "newOutputStream" -> arguments.singleOrNull()?.let {
                    PathCorrection(expression, "${it}.outputStream()", "kotlin.io.path.outputStream")
                }
                "deleteIfExists" -> arguments.singleOrNull()?.let {
                    PathCorrection(expression, "${it}.deleteIfExists()", "kotlin.io.path.deleteIfExists")
                }
                "move" -> arguments.takeIf {
                    it.size == EXPECTED_MOVE_ARGUMENT_COUNT &&
                            it.last().substringAfterLast('.') == "REPLACE_EXISTING"
                }?.let {
                    PathCorrection(
                        expression,
                        "moveMineKotReplacing(${it[0]}, ${it[1]})",
                        "org.minekot.kotlin.io.moveMineKotReplacing",
                    )
                }
                else -> null
            }
        }

        private const val JAVA_NIO_FILES: String = "java.nio.file.Files"
        private const val EXPECTED_MOVE_ARGUMENT_COUNT: Int = 3
        private val blockedImports: Map<String, String> = mapOf(
            "java.io.File" to "java-path-with-kotlin-io",
            JAVA_NIO_FILES to "kotlin-path-or-kotlinx-io",
            "java.util.Timer" to "kotlinx-coroutines",
            "java.lang.Thread" to "kotlinx-coroutines",
            "java.util.ArrayList" to "kotlin-mutable-list",
            "java.util.HashMap" to "kotlin-mutable-map",
            "java.util.HashSet" to "kotlin-mutable-set",
            "java.util.concurrent.CompletableFuture" to "kotlinx-coroutines-deferred",
            "java.util.concurrent.Executors" to "kotlinx-coroutines",
            "com.fasterxml.jackson.databind.ObjectMapper" to "kotlinx-serialization",
            "com.google.gson.Gson" to "kotlinx-serialization",
            "org.json.JSONArray" to "kotlinx-serialization",
            "org.json.JSONObject" to "kotlinx-serialization",
        )
    }
}

private data class PathCorrection(
    val expression: KtDotQualifiedExpression,
    val source: String,
    val requiredImport: String,
)
