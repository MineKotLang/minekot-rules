package org.minekot.rules.practices

import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.minekot.inspections.core.*
import org.minekot.rules.internal.*

/** Requires KDoc for public API and syntactically non-obvious internal properties. */
public class MissingKDocInspection : PsiInspection() {
    override fun createVisitor(context: InspectionContext, reporter: InspectionReporter): KtVisitorVoid =
        object : KtTreeVisitorVoid() {
            override fun visitClassOrObject(classOrObject: KtClassOrObject) {
                super.visitClassOrObject(classOrObject)
                reportIfMissingKDoc(classOrObject, "class-or-object")
            }

            override fun visitNamedFunction(function: KtNamedFunction) {
                super.visitNamedFunction(function)
                reportIfMissingKDoc(function, "function")
            }

            override fun visitProperty(property: KtProperty) {
                super.visitProperty(property)
                reportIfMissingKDoc(property, "property")
            }

            override fun visitParameter(parameter: KtParameter) {
                super.visitParameter(parameter)
                if (parameter.hasValOrVar()) reportIfMissingKDoc(parameter, "constructor-property")
            }

            private fun reportIfMissingKDoc(declaration: KtDeclaration, kind: String) {
                if (
                    declaration.hasKDoc() ||
                    !declaration.requiresContractKDoc(context.includeInternalContracts())
                ) return
                reporter.report(
                    context,
                    DESCRIPTOR,
                    declaration,
                    "missing-contract-kdoc",
                    DESCRIPTOR.message(
                        "missing-contract-kdoc",
                        MineKotRulesBundle.message("declaration-kind.${kind}"),
                    ),
                )
            }
        }

    /** Stable descriptor for documentation policy. */
    public companion object {
        /** Rule metadata exposed by the catalog. */
        public val DESCRIPTOR: InspectionDescriptor = ruleDescriptor(
            id = RuleContracts.MISSING_KDOC,
            aliases = setOf(RuleContracts.MISSING_KDOC_ALIAS),
            category = InspectionCategory.CODE_STYLE,
            severity = RuleSeverity.WARNING,
            options = listOf(
                InspectionOptionDescriptor.BooleanOption(
                    key = "includeInternalContracts",
                    description = MineKotRulesBundle.message("option.include-internal-contracts.description"),
                    defaultValue = InspectionOptionValue.BooleanValue(false),
                ),
            ),
        )

        private fun InspectionContext.includeInternalContracts(): Boolean =
            (options["includeInternalContracts"] as InspectionOptionValue.BooleanValue).value

        private fun KtDeclaration.hasKDoc(): Boolean =
            docComment != null || this is KtParameter && containingClassKDocDocumentsProperty()

        private fun KtParameter.containingClassKDocDocumentsProperty(): Boolean {
            val propertyName = name ?: return false
            val containingClass = generateSequence(parent) { it.parent }
                .filterIsInstance<KtClassOrObject>().firstOrNull() ?: return false
            val classKDoc = containingClass.docComment?.text ?: return false
            return Regex("@property\\s+${Regex.escape(propertyName)}(?:\\s|$)").containsMatchIn(classKDoc)
        }

        private fun KtDeclaration.requiresContractKDoc(includeInternalContracts: Boolean): Boolean {
            if (
                this is KtClassOrObject && name == null ||
                hasModifier(KtTokens.PRIVATE_KEYWORD) ||
                hasModifier(KtTokens.OVERRIDE_KEYWORD) ||
                isLocalDeclaration() ||
                hasNonPublicContainer()
            ) return false
            if (hasModifier(KtTokens.INTERNAL_KEYWORD)) {
                return includeInternalContracts && this is KtCallableDeclaration && hasComplicatedType()
            }
            return true
        }

        private fun KtDeclaration.isLocalDeclaration(): Boolean =
            parent is KtBlockExpression || parent is KtNamedFunction || parent is KtProperty ||
                    generateSequence(parent) { it.parent }
                        .any { it is KtBlockExpression || it is KtNamedFunction }

        private fun KtDeclaration.hasNonPublicContainer(): Boolean =
            generateSequence(parent) { it.parent }.filterIsInstance<KtClassOrObject>().any {
                it.hasModifier(KtTokens.PRIVATE_KEYWORD) || it.hasModifier(KtTokens.INTERNAL_KEYWORD)
            }

        private fun KtCallableDeclaration.hasComplicatedType(): Boolean {
            val type = typeReference?.text ?: (this as? KtProperty)?.initializer?.text ?: return false
            return inferredComplicatedTypePattern.containsMatchIn(type) || complicatedTypeNames.any { name ->
                Regex("(?:^|[<,.? ])${Regex.escape(name)}(?:$|[<>,.? (])").containsMatchIn(type)
            }
        }

        private val inferredComplicatedTypePattern: Regex = Regex(
            "\\b(?:empty(?:List|Map|Set)|mutable(?:List|Map|Set)Of|(?:array|list|map|sequence|set)Of)\\s*(?:<|\\()",
        )
        private val complicatedTypeNames: Set<String> = setOf(
            "Array", "Collection", "Flow", "List", "Map", "MutableCollection", "MutableList", "MutableMap",
            "MutableSet", "Sequence", "Set", "StateFlow",
        )
    }
}
