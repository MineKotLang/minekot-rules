package org.minekot.rules

import org.minekot.inspections.core.*
import org.minekot.rules.codestyle.*
import org.minekot.rules.language.ForEachPreferenceInspection
import org.minekot.rules.language.KotlinxPreferenceInspection
import org.minekot.rules.language.CoroutinePreferenceInspection
import org.minekot.rules.language.ResolvedApiPreferenceInspection
import org.minekot.rules.performance.MainThreadBlockingInspection
import org.minekot.rules.performance.GradlePerformanceInspection
import org.minekot.rules.practices.*
import org.minekot.rules.structure.*

/** Catalog of official MineKot inspections. */
public class MineKotRulesCatalog : MineKotInspectionCatalog, MineKotInspectionCatalogDefaults {
    override val spiMajor: Int = MINEKOT_INSPECTIONS_SPI_MAJOR

    override val inspections: List<InspectionDescriptor> = listOf(
        ForbiddenTryCatchInspection.DESCRIPTOR,
        CommentFormattingInspection.DESCRIPTOR,
        LineWrappingInspection.DESCRIPTOR,
        MiniMessageTextInspection.DESCRIPTOR,
        StringTemplateBracesInspection.DESCRIPTOR,
        TrailingCommaInspection.DESCRIPTOR,
        WhitespaceFormattingInspection.DESCRIPTOR,
        MagicNumberInspection.DESCRIPTOR,
        MissingKDocInspection.DESCRIPTOR,
        ResultHandlingInspection.DESCRIPTOR,
        ExplicitScopeInNestedScopeInspection.DESCRIPTOR,
        ForEachPreferenceInspection.DESCRIPTOR,
        KotlinxPreferenceInspection.DESCRIPTOR,
        CoroutinePreferenceInspection.DESCRIPTOR,
        ResolvedApiPreferenceInspection.DESCRIPTOR,
        SourceFilePolicyInspection.DESCRIPTOR,
        ImportPolicyInspection.DESCRIPTOR,
        GradleDslConventionsInspection.DESCRIPTOR,
        GradlePerformanceInspection.DESCRIPTOR,
        MainThreadBlockingInspection.DESCRIPTOR,
        GradleRepositoryPolicyInspection.DESCRIPTOR,
        GradleDependencyPolicyInspection.DESCRIPTOR,
        GradleTaskContractInspection.DESCRIPTOR,
        GradleTestConfigurationInspection.DESCRIPTOR,
    )

    override val ruleDefaults: Map<String, InspectionRuleDefaults> = mapOf(
        KotlinxPreferenceInspection.DESCRIPTOR.id to InspectionRuleDefaults(
            defaultEnabled = false,
            lifecycle = InspectionRuleLifecycle.DEPRECATED,
            replacementRuleId = ResolvedApiPreferenceInspection.DESCRIPTOR.id,
            recommendedSeverity = RuleSeverity.WEAK_WARNING,
        ),
        ForEachPreferenceInspection.DESCRIPTOR.id to InspectionRuleDefaults(
            defaultEnabled = false,
            lifecycle = InspectionRuleLifecycle.DEPRECATED,
            recommendedSeverity = RuleSeverity.WEAK_WARNING,
        ),
        ExplicitScopeInNestedScopeInspection.DESCRIPTOR.id to InspectionRuleDefaults(
            defaultEnabled = false,
            recommendedSeverity = RuleSeverity.WEAK_WARNING,
        ),
        MagicNumberInspection.DESCRIPTOR.id to InspectionRuleDefaults(
            defaultEnabled = false,
            recommendedSeverity = RuleSeverity.WEAK_WARNING,
        ),
    )

    override fun createInspection(ruleId: String): MineKotInspection? =
        when (ruleId) {
            StringTemplateBracesInspection.DESCRIPTOR.id,
            in StringTemplateBracesInspection.DESCRIPTOR.aliases,
                -> StringTemplateBracesInspection()
            TrailingCommaInspection.DESCRIPTOR.id,
            in TrailingCommaInspection.DESCRIPTOR.aliases,
                -> TrailingCommaInspection()
            WhitespaceFormattingInspection.DESCRIPTOR.id,
            in WhitespaceFormattingInspection.DESCRIPTOR.aliases,
                -> WhitespaceFormattingInspection()
            ForbiddenTryCatchInspection.DESCRIPTOR.id,
            in ForbiddenTryCatchInspection.DESCRIPTOR.aliases,
                -> ForbiddenTryCatchInspection()
            CommentFormattingInspection.DESCRIPTOR.id,
            in CommentFormattingInspection.DESCRIPTOR.aliases,
                -> CommentFormattingInspection()
            LineWrappingInspection.DESCRIPTOR.id,
            in LineWrappingInspection.DESCRIPTOR.aliases,
                -> LineWrappingInspection()
            MiniMessageTextInspection.DESCRIPTOR.id,
            in MiniMessageTextInspection.DESCRIPTOR.aliases,
                -> MiniMessageTextInspection()
            MagicNumberInspection.DESCRIPTOR.id,
            in MagicNumberInspection.DESCRIPTOR.aliases,
                -> MagicNumberInspection()
            MissingKDocInspection.DESCRIPTOR.id,
            in MissingKDocInspection.DESCRIPTOR.aliases,
                -> MissingKDocInspection()
            ResultHandlingInspection.DESCRIPTOR.id,
            in ResultHandlingInspection.DESCRIPTOR.aliases,
                -> ResultHandlingInspection()
            ExplicitScopeInNestedScopeInspection.DESCRIPTOR.id,
            in ExplicitScopeInNestedScopeInspection.DESCRIPTOR.aliases,
                -> ExplicitScopeInNestedScopeInspection()
            ForEachPreferenceInspection.DESCRIPTOR.id,
            in ForEachPreferenceInspection.DESCRIPTOR.aliases,
                -> ForEachPreferenceInspection()
            KotlinxPreferenceInspection.DESCRIPTOR.id,
            in KotlinxPreferenceInspection.DESCRIPTOR.aliases,
                -> KotlinxPreferenceInspection()
            CoroutinePreferenceInspection.DESCRIPTOR.id,
            in CoroutinePreferenceInspection.DESCRIPTOR.aliases,
                -> CoroutinePreferenceInspection()
            ResolvedApiPreferenceInspection.DESCRIPTOR.id,
            in ResolvedApiPreferenceInspection.DESCRIPTOR.aliases,
                -> ResolvedApiPreferenceInspection()
            SourceFilePolicyInspection.DESCRIPTOR.id,
            in SourceFilePolicyInspection.DESCRIPTOR.aliases,
                -> SourceFilePolicyInspection()
            ImportPolicyInspection.DESCRIPTOR.id,
            in ImportPolicyInspection.DESCRIPTOR.aliases,
                -> ImportPolicyInspection()
            GradleDslConventionsInspection.DESCRIPTOR.id,
            in GradleDslConventionsInspection.DESCRIPTOR.aliases,
                -> GradleDslConventionsInspection()
            GradlePerformanceInspection.DESCRIPTOR.id,
            in GradlePerformanceInspection.DESCRIPTOR.aliases,
                -> GradlePerformanceInspection()
            MainThreadBlockingInspection.DESCRIPTOR.id,
            in MainThreadBlockingInspection.DESCRIPTOR.aliases,
                -> MainThreadBlockingInspection()
            GradleRepositoryPolicyInspection.DESCRIPTOR.id,
            in GradleRepositoryPolicyInspection.DESCRIPTOR.aliases,
                -> GradleRepositoryPolicyInspection()
            GradleDependencyPolicyInspection.DESCRIPTOR.id,
            in GradleDependencyPolicyInspection.DESCRIPTOR.aliases,
                -> GradleDependencyPolicyInspection()
            GradleTaskContractInspection.DESCRIPTOR.id,
            in GradleTaskContractInspection.DESCRIPTOR.aliases,
                -> GradleTaskContractInspection()
            GradleTestConfigurationInspection.DESCRIPTOR.id,
            in GradleTestConfigurationInspection.DESCRIPTOR.aliases,
                -> GradleTestConfigurationInspection()

            else -> null
        }
}
