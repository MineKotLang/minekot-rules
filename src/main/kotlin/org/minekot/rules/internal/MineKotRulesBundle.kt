package org.minekot.rules.internal

import org.minekot.inspections.core.*
import java.text.MessageFormat
import java.util.*

/** Resolves English rule metadata, diagnostics, and correction labels from language resources. */
internal object MineKotRulesBundle {
    private val bundle: ResourceBundle = ResourceBundle.getBundle(
        "messages.MineKotRulesBundle",
        Locale.ENGLISH,
        MineKotRulesBundle::class.java.classLoader,
    )

    fun message(key: String, vararg arguments: Any): String =
        MessageFormat(bundle.getString(key), Locale.ENGLISH).format(arguments)
}

/** Creates localized immutable metadata while stable machine identifiers remain code constants. */
internal fun ruleDescriptor(
    id: String,
    aliases: Set<String>,
    category: InspectionCategory,
    severity: RuleSeverity,
    options: List<InspectionOptionDescriptor> = emptyList(),
): InspectionDescriptor = InspectionDescriptor(
    id = id,
    aliases = aliases,
    displayName = MineKotRulesBundle.message("${id}.name"),
    description = MineKotRulesBundle.message("${id}.description"),
    category = category,
    defaultSeverity = severity,
    options = options,
)

/** Resolves one diagnostic owned by this descriptor. */
internal fun InspectionDescriptor.message(messageId: String, vararg arguments: Any): String =
    MineKotRulesBundle.message("${id}.diagnostic.${messageId}", *arguments)

/** Resolves one correction label owned by this descriptor. */
internal fun InspectionDescriptor.correctionLabel(correctionId: String): String =
    MineKotRulesBundle.message("${id}.fix.${correctionId}")

/** Stable rule IDs and aliases shared by catalogs, findings, suppressions, and language-resource keys. */
internal object RuleContracts {
    const val COMMENT_FORMATTING = "minekot.codestyle.comment-formatting"
    const val COROUTINE_PREFERENCE = "minekot.concurrency.coroutine-preference"
    const val MAIN_THREAD_BLOCKING = "minekot.concurrency.main-thread-blocking"
    const val EXPLICIT_SCOPE = "minekot.codestyle.explicit-scope-in-nested-scope"
    const val FOR_EACH_PREFERENCE = "minekot.codestyle.for-each-preference"
    const val FORBIDDEN_TRY_CATCH = "minekot.correctness.forbidden-try-catch"
    const val GRADLE_DSL = "minekot.codestyle.gradle-dsl-conventions"
    const val GRADLE_PERFORMANCE = "minekot.performance.gradle-configuration"
    const val GRADLE_REPOSITORY_POLICY = "minekot.gradle.repository-policy"
    const val GRADLE_DEPENDENCY_POLICY = "minekot.gradle.dependency-policy"
    const val GRADLE_TASK_CONTRACT = "minekot.gradle.task-contract"
    const val GRADLE_TEST_CONFIGURATION = "minekot.gradle.test-configuration"
    const val IMPORT_POLICY = "minekot.codestyle.import-policy"
    const val KOTLINX_PREFERENCE = "minekot.codestyle.kotlinx-preference"
    const val LINE_WRAPPING = "minekot.codestyle.line-wrapping"
    const val MAGIC_NUMBER = "minekot.codestyle.magic-number"
    const val MINIMESSAGE_TEXT = "minekot.codestyle.minimessage-text"
    const val MISSING_KDOC = "minekot.codestyle.missing-kdoc"
    const val RESOLVED_API_PREFERENCE = "minekot.codestyle.resolved-api-preference"
    const val RESULT_HANDLING = "minekot.correctness.result-handling"
    const val SOURCE_FILE_POLICY = "minekot.codestyle.source-file-policy"
    const val STRING_TEMPLATE_BRACES = "minekot.codestyle.string-template-braces"
    const val TRAILING_COMMA = "minekot.codestyle.trailing-comma"
    const val WHITESPACE_FORMATTING = "minekot.codestyle.whitespace-formatting"

    const val COMMENT_FORMATTING_ALIAS = "CommentFormatting"
    const val COROUTINE_PREFERENCE_ALIAS = "CoroutinePreference"
    const val MAIN_THREAD_BLOCKING_ALIAS = "MainThreadBlocking"
    const val EXPLICIT_SCOPE_ALIAS = "ExplicitScopeInNestedScope"
    const val FOR_EACH_PREFERENCE_ALIAS = "ForEachPreference"
    const val FORBIDDEN_TRY_CATCH_ALIAS = "ForbiddenTryCatch"
    const val GRADLE_DSL_ALIAS = "GradleDslConventions"
    const val GRADLE_PERFORMANCE_ALIAS = "GradlePerformance"
    const val GRADLE_REPOSITORY_POLICY_ALIAS = "GradleRepositoryPolicy"
    const val GRADLE_DEPENDENCY_POLICY_ALIAS = "GradleDependencyPolicy"
    const val GRADLE_TASK_CONTRACT_ALIAS = "GradleTaskContract"
    const val GRADLE_TEST_CONFIGURATION_ALIAS = "GradleTestConfiguration"
    const val IMPORT_POLICY_ALIAS = "ImportPolicy"
    const val KOTLINX_PREFERENCE_ALIAS = "KotlinxPreference"
    const val LINE_WRAPPING_ALIAS = "LineWrapping"
    const val MAGIC_NUMBER_ALIAS = "MagicNumber"
    const val MINIMESSAGE_TEXT_ALIAS = "MiniMessageText"
    const val MISSING_KDOC_ALIAS = "MissingKDoc"
    const val RESOLVED_API_PREFERENCE_ALIAS = "ResolvedApiPreference"
    const val RESULT_HANDLING_ALIAS = "ResultHandling"
    const val SOURCE_FILE_POLICY_ALIAS = "SourceFilePolicy"
    const val STRING_TEMPLATE_BRACES_ALIAS = "StringTemplateBraces"
    const val STRING_TEMPLATE_BRACES_LEGACY_ID = "minekot.formatting.string-template-braces"
    const val TRAILING_COMMA_ALIAS = "TrailingComma"
    const val WHITESPACE_FORMATTING_ALIAS = "WhitespaceFormatting"
}
