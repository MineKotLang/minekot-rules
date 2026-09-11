package org.minekot.rules

import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.minekot.inspections.core.*
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Regression matrix migrated from the toolchain-owned Detekt rules. */
class MigratedRulesTest {
    private val catalog = MineKotRulesCatalog()

    /** Every catalog rule owns at least one positive and negative behavioral fixture. */
    @Test
    fun `every migrated rule has should flag and should not flag coverage`() {
        assertEquals(catalog.inspections.map { descriptor -> descriptor.aliases.first() }.toSet(), RULE_CASES.keys)
        RULE_CASES.forEach { (alias, case) ->
            val flagged = findings(
                alias,
                case.shouldFlag,
                case.filename,
                case.resolvedCalls,
                case.outerReceiverReferences,
                case.expressionFacts,
                case.availableSymbols,
            )
            assertTrue(flagged.isNotEmpty(), "${alias} should flag: ${case.shouldFlag}")
            flagged.forEach { finding ->
                assertTrue(finding.message.isNotBlank(), "${alias} emitted blank message")
                assertTrue(finding.messageId.isNotBlank(), "${alias} emitted blank message ID")
                assertTrue(finding.startOffset in case.shouldFlag.indices, "${alias} start offset")
                assertTrue(finding.endOffset in 1..case.shouldFlag.length, "${alias} end offset")
                assertTrue(finding.startOffset < finding.endOffset, "${alias} empty range")
            }
            assertTrue(
                findings(alias, case.shouldNotFlag, case.filename).isEmpty(),
                "${alias} should not flag: ${case.shouldNotFlag}",
            )
        }
    }

    /** Every legacy rule alias resolves to its catalog implementation. */
    @Test
    fun `catalog descriptors are unique and legacy aliases resolve`() {
        assertEquals(catalog.inspections.size, catalog.inspections.map { it.id }.toSet().size)
        assertEquals(
            setOf(
                "CommentFormatting",
                "CoroutinePreference",
                "ExplicitScopeInNestedScope",
                "ForEachPreference",
                "ForbiddenTryCatch",
                "GradleDslConventions",
                "GradlePerformance",
                "GradleRepositoryPolicy",
                "GradleDependencyPolicy",
                "GradleTaskContract",
                "GradleTestConfiguration",
                "ImportPolicy",
                "KotlinxPreference",
                "LineWrapping",
                "MagicNumber",
                "MainThreadBlocking",
                "MiniMessageText",
                "MissingKDoc",
                "ResolvedApiPreference",
                "ResultHandling",
                "SourceFilePolicy",
                "StringTemplateBraces",
                "TrailingComma",
                "WhitespaceFormatting",
            ),
            catalog.inspections.flatMap { it.aliases }.filter { it.firstOrNull()?.isUpperCase() == true }.toSet(),
        )
        catalog.inspections.forEach { descriptor ->
            assertNotNull(catalog.createInspection(descriptor.id), descriptor.id)
            descriptor.aliases.forEach { alias -> assertNotNull(catalog.createInspection(alias), alias) }
        }
    }

    /** Catalog defaults keep subjective and deprecated rules inactive unless project policy opts in. */
    @Test
    fun `catalog defaults activate only safe rules`() {
        val resolved = InspectionPolicy(MINEKOT_INSPECTIONS_POLICY_SCHEMA_VERSION, emptyMap()).resolve(catalog)
        val defaults = catalog.ruleDefaults

        setOf(
            "minekot.codestyle.explicit-scope-in-nested-scope",
            "minekot.codestyle.for-each-preference",
            "minekot.codestyle.kotlinx-preference",
            "minekot.codestyle.magic-number",
        ).forEach { ruleId -> assertTrue(resolved.getValue(ruleId).enabled.not(), ruleId) }
        assertTrue(resolved.getValue("minekot.concurrency.main-thread-blocking").enabled)
        assertEquals(
            InspectionRuleLifecycle.DEPRECATED,
            defaults.getValue("minekot.codestyle.kotlinx-preference").lifecycle,
        )
        assertEquals(
            "minekot.codestyle.resolved-api-preference",
            defaults.getValue("minekot.codestyle.kotlinx-preference").replacementRuleId,
        )
    }

    /** Exception boundaries preserve cancellation and accept typed recovery. */
    @Test
    fun `forbidden try catch parity matrix`() {
        assertEquals(
            1,
            findings(
                "ForbiddenTryCatch",
                "fun run() { try { error(\"boom\") } catch (failure: RuntimeException) { println(failure) } }",
            ).size,
        )
        assertTrue(
            findings(
                "ForbiddenTryCatch",
                "fun load(): String = try { error(\"boom\") } catch (failure: java.io.IOException) { \"fallback\" }",
            ).isEmpty(),
        )
    }

    /** Numeric intent exceptions remain narrow. */
    @Test
    fun `magic number parity matrix`() {
        assertEquals(
            expected = 5,
            actual = findings("MagicNumber", "fun values() = listOf(-7, 100L, 1_000u, 0xff, 10.0f)").size,
        )
        assertTrue(findings("MagicNumber", "fun run() = configure(rows = 3, index = 13)").isEmpty())
    }

    /** Constructor properties may inherit documentation from class property tags. */
    @Test
    fun `missing kdoc recognizes class property documentation`() {
        val documented = "/** Service. @property name Service name. */ class Service(val name: String)"
        assertTrue(findings("MissingKDoc", documented).isEmpty())
        assertEquals(1, findings("MissingKDoc", "/** Service. */ class Service(val name: String)").size)
    }

    /** Internal KDoc judgment stays opt-in while public contracts remain active. */
    @Test
    fun `missing kdoc internal contracts require explicit option`() {
        val source = "internal val state: MutableMap<String, Int> = mutableMapOf()"

        assertTrue(findings("MissingKDoc", source).isEmpty())
        assertEquals(
            1,
            findings(
                "MissingKDoc",
                source,
                options = mapOf("includeInternalContracts" to InspectionOptionValue.BooleanValue(true)),
            ).size,
        )
    }

    /** Result consumption follows branches and argument flow. */
    @Test
    fun `result handling recognizes consumed and discarded values`() {
        assertEquals(1, findings("ResultHandling", "fun run() { runCatching { Unit }; println(\"done\") }").size)
        assertTrue(
            findings(
                "ResultHandling",
                "fun consume(value: Result<Unit>) = Unit\nfun run() = consume(runCatching { Unit })",
            ).isEmpty(),
        )
        assertTrue(
            findings(
                "ResultHandling",
                "fun run(ok: Boolean): Result<Unit> = if (ok) runCatching { Unit } else Result.failure(Error())",
            ).isEmpty(),
        )
    }

    /** Unsafe extraction and every value-producing Result container are classified explicitly. */
    @Test
    fun `result handling complete consumption matrix`() {
        assertEquals(
            listOf("unsafe-get-or-throw"),
            findings("ResultHandling", "fun run() = runCatching { Unit }.getOrThrow()").map { it.messageId },
        )
        assertTrue(findings("ResultHandling", "fun run() = customResult.getOrThrow()").isEmpty())
        val consumed = listOf(
            "val result = runCatching { Unit }",
            "fun run(): Result<Unit> = runCatching { Unit }",
            "fun run(): Result<Unit> { return runCatching { Unit } }",
            "fun consume(result: Result<Unit>) = Unit\nfun run() = consume(runCatching { Unit })",
            "fun run(ok: Boolean): Result<Unit> = if (ok) runCatching { Unit } else Result.success(Unit)",
            "fun run(value: Int): Result<Unit> = when (value) { 1 -> runCatching { Unit }; " +
                "else -> Result.success(Unit) }",
            "fun run(): Result<Unit> = try { runCatching { Unit } } finally { println(\"done\") }",
        )

        consumed.forEach { source -> assertTrue(findings("ResultHandling", source).isEmpty(), source) }
    }

    /** Loop-form preference remains report-only and excludes control-sensitive forms. */
    @Test
    fun `for each preference does not rewrite loops`() {
        val source = "fun run(values: List<Int>) { for (value in values) { println(value) } }"
        assertTrue(findings("ForEachPreference", source).single().corrections.isEmpty())
        assertTrue(
            findings(
                "ForEachPreference",
                "fun run(values: List<Int>) { for (i in values.indices) { println(i) } }",
            ).isEmpty(),
        )
    }

    /** BOM correction and formatter diagnostics retain their independent identities. */
    @Test
    fun `source file policy corrects bom and reports formatter imbalance`() {
        val source = "\uFEFF// @formatter:" + "off\nfun run() = Unit"
        val results = findings("SourceFilePolicy", source)
        assertEquals(setOf("utf8-bom", "unclosed-formatter-off"), results.map { it.messageId }.toSet())
        val corrected = results.single { it.messageId == "utf8-bom" }.corrections.single()
            .applyTo(source, source.sha256())
        assertTrue(!corrected.startsWith('\uFEFF'))
    }

    /** Formatter-control diagnostics distinguish unmatched closing and unclosed opening tags. */
    @Test
    fun `source file formatter tag matrix`() {
        assertEquals(
            listOf("unmatched-formatter-on"),
            findings("SourceFilePolicy", "// @formatter:" + "on\nfun run() = Unit").map { it.messageId },
        )
        assertEquals(
            listOf("unclosed-formatter-off"),
            findings("SourceFilePolicy", "// @formatter:" + "off\nfun run() = Unit").map { it.messageId },
        )
        assertTrue(
            findings(
                "SourceFilePolicy",
                "// @formatter:" + "off\nfun run() = Unit\n// @formatter:" + "on",
            ).isEmpty(),
        )
    }

    /** Wildcard correction preserves a valid package import and reaches a fixed point. */
    @Test
    fun `import wildcard correction is complete and idempotent`() {
        val source = "import java.util.ArrayList\n\nfun run() = ArrayList<String>()"
        val corrected = findings("ImportPolicy", source).single().corrections.single()
            .applyTo(source, source.sha256())
        assertTrue(corrected.startsWith("import java.util.*"), corrected)
        assertTrue(findings("ImportPolicy", corrected).isEmpty())
    }

    /** Import policy covers nested types, thresholds, aliases, and comment-safe refusal. */
    @Test
    fun `import policy complete matrix`() {
        assertEquals(
            listOf("nested-class-import"),
            findings("ImportPolicy", "import sample.Outer.Inner\nfun run() = Unit").map { it.messageId },
        )
        assertTrue(findings("ImportPolicy", "import sample.Outer\nfun run() = Unit").isEmpty())
        assertTrue(findings("ImportPolicy", "import sample.Type as Alias\nfun run() = Unit").isEmpty())

        val thresholdSource = (1..WILDCARD_IMPORT_THRESHOLD).joinToString("\n") { index ->
            "import sample.Type${index}"
        }
        val threshold = findings("ImportPolicy", thresholdSource).single()
        assertEquals("wildcard-threshold", threshold.messageId)
        val corrected = threshold.corrections.single().applyTo(thresholdSource, thresholdSource.sha256())
        assertEquals("import sample.*", corrected.trim())

        val commented = "import sample.Type1 // keep\n" +
            (SECOND_IMPORT_INDEX..WILDCARD_IMPORT_THRESHOLD).joinToString("\n") { index ->
                "import sample.Type${index}"
            }
        assertTrue(findings("ImportPolicy", commented).single().corrections.isEmpty())
    }

    /** Trailing commas correct supported lists without corrupting subjectless when conditions. */
    @Test
    fun `trailing comma correction is safe and idempotent`() {
        val source = "fun run() = listOf(\n    1,\n    2\n)"
        val corrected = findings("TrailingComma", source).single().corrections.single()
            .applyTo(source, source.sha256())
        assertEquals("fun run() = listOf(\n    1,\n    2,\n)", corrected)
        assertTrue(findings("TrailingComma", corrected).isEmpty())

        val subjectlessWhen =
            "fun run(a: Boolean, b: Boolean) = when {\n    a ||\n        b -> Unit\n    else -> Unit\n}"
        assertTrue(findings("TrailingComma", subjectlessWhen).isEmpty())
    }

    /** One guarded plan fixes every spacing issue in a comment. */
    @Test
    fun `comment formatting correction is complete and idempotent`() {
        val source = "fun run() {\n//compact\n    Unit\n}"
        val finding = findings("CommentFormatting", source).single()
        val corrected = finding.corrections.single().applyTo(source, source.sha256())
        assertEquals("fun run() {\n    // compact\n    Unit\n}", corrected)
        assertTrue(findings("CommentFormatting", corrected).isEmpty())
    }

    /** Files path correction updates calls and imports as one guarded plan. */
    @Test
    fun `kotlinx path correction is atomic and idempotent`() {
        val source =
            "import java.nio.file.Files\nimport java.nio.file.Path\nfun exists(path: Path) = Files.exists(path)"
        val corrected = findings("KotlinxPreference", source).single().corrections.single()
            .applyTo(source, source.sha256())
        assertTrue("import java.nio.file.Files" !in corrected, corrected)
        assertTrue("import kotlin.io.path.exists" in corrected, corrected)
        assertTrue("path.exists()" in corrected, corrected)
        assertTrue(findings("KotlinxPreference", corrected).isEmpty())
    }

    /** Long call correction preserves string literals and reaches a fixed point. */
    @Test
    fun `line wrapping correction is safe and idempotent`() {
        val source =
            "fun run() = consume(firstArgument, secondArgument, thirdArgument, fourthArgument, fifthArgument, sixthArgument, seventhArgument)"
        val corrected = findings("LineWrapping", source).single().corrections.single()
            .applyTo(source, source.sha256())
        assertTrue("\n        firstArgument," in corrected, corrected)
        assertTrue(findings("LineWrapping", corrected).isEmpty())
        val longString = "val text = \"${"x".repeat(n = 130)}\""
        val fallback = findings("LineWrapping", longString).single()
        assertEquals("overlong-line", fallback.messageId)
        assertTrue(fallback.corrections.isEmpty())
    }

    /** Every supported long-line syntax owns a correction fixture and reaches a fixed point. */
    @Test
    fun `line wrapping complete syntax matrix`() {
        val longNames = List(LONG_LINE_NAME_COUNT) { index -> "conditionNumber${index}WithLongDescriptiveName" }
        val cases = mapOf(
            "long-condition" to
                "fun run(${longNames.joinToString { "${it}: Boolean" }}) = " +
                    "if (${longNames.joinToString(" && ")}) Unit else Unit",
            "long-getter" to
                "val generatedValue: Any get() = createValue(" +
                    longNames.joinToString() + ")",
            "long-cast" to
                "fun cast(value: Any) = value as " +
                    "ExtremelyLongGeneratedContractTypeWhoseNameForcesTheCastPastTheConfiguredLineLengthLimit" +
                    "AndStillRemainsReadable",
            "long-when-entry" to
                "fun run(value: String) = when (value) { " +
                    longNames.joinToString() + " -> Unit; else -> Unit }",
        )
        cases.forEach { (messageId, source) ->
            val finding = assertNotNull(
                findings("LineWrapping", source).singleOrNull { it.messageId == messageId },
                "${messageId}: ${source}",
            )
            val corrected = finding.corrections.single().applyTo(source, source.sha256())

            assertTrue('\n' in corrected, messageId)
            assertTrue(findings("LineWrapping", corrected).none { it.messageId == messageId }, corrected)
        }
    }

    /** Gradle diagnostics are file-scoped and concise repository syntax is corrected. */
    @Test
    fun `gradle dsl convention correction is guarded`() {
        val source = "repositories {\n    maven { url = uri(\"https://repo.example\") }\n}"
        val finding = findings("GradleRepositoryPolicy", source, "build.gradle.kts").single()
        val corrected = finding.corrections.single().applyTo(source, source.sha256())
        assertTrue("maven(\"https://repo.example\")" in corrected, corrected)
        assertTrue(findings("GradleRepositoryPolicy", corrected, "build.gradle.kts").isEmpty())
        assertTrue(findings("GradleRepositoryPolicy", "mavenCentral()", "Test.kt").isEmpty())
    }

    /** Gradle DSL checks cover every declarative and reproducibility branch. */
    @Test
    fun `gradle dsl conventions complete matrix`() {
        val cases = mapOf(
            "plugins-order" to "repositories {\n}\nplugins {\n}",
            "imperative-plugin" to "apply(plugin = \"java\")",
            "single-line-block" to "plugins { id(\"java\") }",
            "untyped-accessor" to "getByType(Service::class.java)",
            "invalid-container-name" to "configurations.create(\"bad_name\")",
            "broad-subproject-configuration" to "subprojects {\n    repositories {\n    }\n}",
        )
        cases.forEach { (messageId, source) ->
            assertTrue(
                findings("GradleDslConventions", source, "build.gradle.kts").any { it.messageId == messageId },
                messageId,
            )
        }
    }

    /** Focused Gradle rules own repository, dependency, task, and test contracts. */
    @Test
    fun `focused gradle rules cover deterministic contracts`() {
        val cases = mapOf(
            "GradleRepositoryPolicy" to mapOf(
                "outside-repositories-block" to "mavenCentral()",
                "repository-order" to "repositories {\n    mavenCentral()\n    mavenLocal()\n}",
                "verbose-maven-repository" to
                        "repositories {\n    maven { url = uri(\"https://repo.example\") }\n}",
            ),
            "GradleDependencyPolicy" to mapOf(
                "dynamic-version" to "dependencies {\n    implementation(\"sample:library:1.+\")\n}",
                "short-kotlin-notation" to "dependencies {\n    implementation(kotlin(\"stdlib\"))\n}",
                "file-project-dependency" to
                        "dependencies {\n    implementation(files(\"../core.jar\"))\n}",
            ),
            "GradleTaskContract" to mapOf(
                "invalid-task-name" to "tasks.register(\"bad_name\")",
                "eager-task-configuration" to "tasks.getByName(\"check\")",
                "complex-inline-action" to
                        "tasks.named(\"check\") {\n    doLast {\n        println(1)\n        println(2)\n    }\n}",
            ),
            "GradleTestConfiguration" to mapOf(
                "incomplete-test-configuration" to
                        "tasks.named<Test>(\"test\") {\n    useJUnitPlatform()\n}",
                "incomplete-test-configuration-with-type" to
                        "tasks.withType<Test>().configureEach {\n    useJUnitPlatform()\n}",
            ),
        )

        cases.forEach { (alias, diagnostics) ->
            diagnostics.forEach { (caseId, source) ->
                val messageId = caseId.substringBefore("-with-type")
                assertTrue(
                    findings(alias, source, "build.gradle.kts").any { finding ->
                        finding.messageId == messageId
                    },
                    "${alias}: ${messageId}",
                )
            }
        }
        assertTrue(
            findings(
                "GradleTestConfiguration",
                "tasks.named<Test>(\"test\") {\n    useJUnitPlatform()\n    maxParallelForks = 2\n}",
                "build.gradle.kts",
            ).isEmpty(),
        )
        assertTrue(
            findings(
                "GradleTestConfiguration",
                "tasks.withType<Test>().configureEach {\n    useJUnitPlatform()\n    maxParallelForks = 2\n}",
                "build.gradle.kts",
            ).isEmpty(),
        )
    }

    /** Generic long-line fallback avoids formatter-disabled source and never guesses a correction. */
    @Test
    fun `line wrapping fallback respects formatter controls`() {
        val longExpression = "val result = " +
                List(LONG_FALLBACK_PART_COUNT) { index -> "value${index}" }.joinToString(" + ")
        val finding = findings("LineWrapping", longExpression).single()

        assertEquals("overlong-line", finding.messageId)
        assertEquals(0, finding.startOffset)
        assertEquals(longExpression.length, finding.endOffset)
        assertTrue(finding.corrections.isEmpty())
        assertTrue(
            findings(
                "LineWrapping",
                "// @formatter:off\n${longExpression}\n// @formatter:on",
            ).isEmpty(),
        )
    }

    /** Whitespace formatting covers each deterministic MineKot spacing contract. */
    @Test
    fun `whitespace formatting complete matrix`() {
        val cases = listOf(
            WhitespaceCase("tab-indentation", "\tfun run() = Unit", "    fun run() = Unit"),
            WhitespaceCase("trailing-whitespace", "fun run() = Unit  ", "fun run() = Unit"),
            WhitespaceCase(
                "blank-line-before-closing-brace",
                "fun run() {\n    Unit\n\n}",
                "fun run() {\n    Unit\n}",
            ),
            WhitespaceCase("binary-operator-spacing", "fun run() = 1+2", "fun run() = 1 + 2"),
            WhitespaceCase(
                "unary-operator-spacing",
                "fun run(value: Boolean) = ! value",
                "fun run(value: Boolean) = !value",
            ),
            WhitespaceCase("comma-spacing", "fun run() = listOf(1,2)", "fun run() = listOf(1, 2)"),
            WhitespaceCase("colon-spacing", "fun run(value :Int) = Unit", "fun run(value: Int) = Unit"),
            WhitespaceCase("arrow-spacing", "fun run() = { value: Int->value }", "fun run() = { value: Int -> value }"),
            WhitespaceCase(
                "type-operator-spacing",
                "fun run(value: Any) = value as  String",
                "fun run(value: Any) = value as String",
            ),
            WhitespaceCase("curly-brace-spacing", "fun run(){\n    Unit\n}", "fun run() {\n    Unit\n}"),
            WhitespaceCase("curly-brace-spacing", "fun run() {Unit\n}", "fun run() { Unit\n}"),
            WhitespaceCase("curly-brace-spacing", "fun run() {\n    Unit}", "fun run() {\n    Unit }"),
            WhitespaceCase(
                "blank-line-between-when-branches",
                "fun run(value: Int) = when (value) {\n    1 -> Unit\n\n    else -> Unit\n}",
                "fun run(value: Int) = when (value) {\n    1 -> Unit\n    else -> Unit\n}",
            ),
        )
        cases.forEach { case ->
            val result = findings("WhitespaceFormatting", case.source).single { it.messageId == case.messageId }
            val corrected = result.corrections.single().applyTo(case.source, case.source.sha256())
            assertEquals(case.corrected, corrected, case.messageId)
            assertTrue(findings("WhitespaceFormatting", corrected).isEmpty(), case.messageId)
        }
        val invalidIndentation = findings("WhitespaceFormatting", "fun run() {\n  Unit\n}")
            .single { finding -> finding.messageId == "indentation-width" }
        assertTrue(invalidIndentation.corrections.isEmpty())
        assertEquals(
            listOf("empty-first-body-line"),
            findings("WhitespaceFormatting", "fun run() {\n\n    Unit\n}").map { it.messageId },
        )
        assertTrue(
            findings(
                "WhitespaceFormatting",
                "// @formatter:" + "off\nfun run() = 1+2\n// @formatter:" + "on",
            ).isEmpty(),
        )
        assertTrue(findings("WhitespaceFormatting", "fun text(value: Int) = \"${'$'}{value}\"").isEmpty())
        assertTrue(findings("WhitespaceFormatting", "fun run() {}").isEmpty())
        assertTrue(
            findings(
                "WhitespaceFormatting",
                "val text = \"\"\"\n\tcontent\n\n}\n\"\"\"",
            ).isEmpty(),
        )
    }

    /** Lambda arrows ending a line stay clean and never create trailing-whitespace corrections. */
    @Test
    fun `multiline lambda arrow does not create trailing whitespace`() {
        val valid = "fun run(values: List<Int>) = values.map { value ->\n    value + 1\n}"
        assertTrue(findings("WhitespaceFormatting", valid).isEmpty())

        val invalid = "fun run(values: List<Int>) = values.map { value->\n    value + 1\n}"
        val finding = findings("WhitespaceFormatting", invalid).single()
        assertEquals("arrow-spacing", finding.messageId)
        assertEquals("Use one space around arrows.", finding.corrections.single().label)
        val corrected = finding.corrections.single().applyTo(invalid, invalid.sha256())
        assertEquals(valid, corrected)
        assertTrue(corrected.lineSequence().none { line -> line.endsWith(' ') || line.endsWith('\t') })
        assertTrue(findings("WhitespaceFormatting", corrected).isEmpty())
    }

    /** Real MineKot API source remains a regression fixture for multiline-arrow and correction interactions. */
    @Test
    fun `selection source has no whitespace findings`() {
        val source = checkNotNull(javaClass.getResource("/fixtures/codestyle/Selection.kt")).readText()

        assertTrue(findings("WhitespaceFormatting", source, "Selection.kt").isEmpty())
    }

    /** Gradle performance checks cover configuration avoidance, caching, and cache-safe inputs. */
    @Test
    fun `gradle performance complete matrix`() {
        val cases = mapOf(
            "after-evaluate" to "afterEvaluate { println(\"configured\") }",
            "task-provider-realization" to "val checkTask = tasks.named(\"check\").get()",
            "eager-task-traversal" to "tasks.forEach { task -> println(task.name) }",
            "configuration-time-resolution" to "configurations.runtimeClasspath.get().resolve()",
            "configuration-time-system-read" to "val mode = System.getProperty(\"mode\")",
            "task-graph-callback" to "gradle.taskGraph.whenReady { println(\"ready\") }",
            "missing-task-cache-policy" to
                    "abstract class GenerateTask : DefaultTask() { @TaskAction fun generate() = Unit }",
        )
        cases.forEach { (messageId, source) ->
            assertTrue(
                findings("GradlePerformance", source, "build.gradle.kts").any { it.messageId == messageId },
                messageId,
            )
        }
        val safeSource = """
            @CacheableTask
            abstract class GenerateTask : DefaultTask() {
                @TaskAction
                fun generate() {
                    System.getProperty("mode")
                }
            }
            tasks.named("check") {
                dependsOn(tasks.named("generate"))
            }
        """.trimIndent()
        assertTrue(findings("GradlePerformance", safeSource, "build.gradle.kts").isEmpty())
        assertTrue(findings("GradlePerformance", "afterEvaluate { Unit }", "Test.kt").isEmpty())
    }

    /** Trailing-comma visitor covers every supported multiline PSI list. */
    @Test
    fun `trailing comma complete syntax matrix`() {
        val sources = listOf(
            "fun run(\n    first: Int,\n    second: Int\n) = Unit",
            "fun <\n    A,\n    B\n> run() = Unit",
            "fun run() = consume<\n    String,\n    Int\n>()",
            "fun run() = arrayOf(1, 2)[\n    0,\n    1\n]",
            "fun run() { val (\n    first,\n    second\n) = Pair(1, 2) }",
            "fun run() = {\n    first: Int,\n    second: Int -> first + second\n}",
            "fun run(value: Int) = when (value) {\n    1,\n    2 -> Unit\n    else -> Unit\n}",
        )
        sources.forEach { source ->
            val corrected = findings("TrailingComma", source).single().corrections.single()
                .applyTo(source, source.sha256())
            assertTrue(findings("TrailingComma", corrected).isEmpty(), corrected)
        }
    }

    /** Every supported Files migration emits compilable imports and deterministic edits. */
    @Test
    fun `kotlin path migration complete call matrix`() {
        val cases = mapOf(
            "Files.exists(path)" to "path.exists()",
            "Files.newInputStream(path)" to "path.inputStream()",
            "Files.newOutputStream(path)" to "path.outputStream()",
            "Files.deleteIfExists(path)" to "path.deleteIfExists()",
            "Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)" to
                "moveMineKotReplacing(source, target)",
        )
        cases.forEach { (call, replacement) ->
            val source = "import java.nio.file.Files\nimport java.nio.file.Path\n" +
                "import java.nio.file.StandardCopyOption\nfun run(path: Path, source: Path, target: Path) = ${call}"
            val finding = findings("KotlinxPreference", source).single()
            val corrected = finding.corrections.single().applyTo(source, source.sha256())

            assertTrue(replacement in corrected, call)
            assertTrue("import java.nio.file.Files" !in corrected, corrected)
            assertTrue(findings("KotlinxPreference", corrected).isEmpty(), corrected)
        }
    }

    /** Unsupported Files calls stay intact while supported calls in the same file still migrate. */
    @Test
    fun `kotlin path migration preserves unsupported calls`() {
        val source = "import java.nio.file.Files\nimport java.nio.file.Path\n" +
            "fun run(path: Path) { Files.readString(path); Files.exists(path) }"
        val finding = findings("KotlinxPreference", source).single()
        val corrected = finding.corrections.single().applyTo(source, source.sha256())

        assertTrue("import java.nio.file.Files" in corrected)
        assertTrue("Files.readString(path)" in corrected)
        assertTrue("path.exists()" in corrected)
        assertTrue("import kotlin.io.path.exists" in corrected)
    }

    /** Loop-control, indexed iteration, returns, and mutation retain loop syntax. */
    @Test
    fun `for each guard matrix avoids unsafe rewrites`() {
        val safeLoops = listOf(
            "fun run(values: List<Int>) { for (index in values.indices) { println(index) } }",
            "fun run(values: List<Int>) { for ((index, value) in values.withIndex()) { println(index + value) } }",
            "fun run() { for (value in 0..10) { println(value) } }",
            "fun run(values: List<Int>) { for (value in values) { if (value == 1) break } }",
            "fun run(values: List<Int>) { for (value in values) { if (value == 1) continue } }",
            "fun run(values: List<Int>) { for (value in values) { return } }",
            "fun run(values: MutableList<Int>) { for (value in values) { values.add(value) } }",
            "fun run(values: MutableList<Int>) { for (value in values) { values[0] = value } }",
        )

        safeLoops.forEach { source -> assertTrue(findings("ForEachPreference", source).isEmpty(), source) }
    }

    /** Host resolution prevents same-named custom Thread APIs from becoming false positives. */
    @Test
    fun `semantic API preferences consume only host resolved identities`() {
        val threadSource = "fun run() { Thread() }"
        assertEquals(
            1,
            findings(
                "CoroutinePreference",
                threadSource,
                resolvedCalls = mapOf("Thread()" to "java.lang.Thread.<init>"),
            ).size,
        )
        assertTrue(
            findings(
                "CoroutinePreference",
                threadSource,
                resolvedCalls = mapOf("Thread()" to "sample.Thread.<init>"),
            ).isEmpty(),
        )
        assertEquals(
            1,
            findings(
                "ResolvedApiPreference",
                "fun run() = java.io.File(\"x\")",
                resolvedCalls = mapOf("File(\"x\")" to "java.io.File.<init>"),
            ).size,
        )
    }

    /** Adventure text is checked only when both producer and sink resolve to known APIs. */
    @Test
    fun `minimessage rule follows resolved Adventure flow`() {
        val source = "fun run() { audience.sendMessage(Component.text(\"&aHello\")) }"
        val resolved = mapOf(
            "text(\"&aHello\")" to "net.kyori.adventure.text.Component.text",
            "sendMessage(Component.text(\"&aHello\"))" to
                    "net.kyori.adventure.audience.Audience.sendMessage",
        )
        assertEquals(1, findings("MiniMessageText", source, resolvedCalls = resolved).size)
        assertTrue(findings("MiniMessageText", source).isEmpty())
    }

    /** Explicit-scope policy consumes the host's receiver classification and remains report-only. */
    @Test
    fun `explicit scope reports only host classified outer references`() {
        val source = "class Service { val name = \"MineKot\"; fun run() { listOf(1).forEach { println(name) } } }"
        val findings = findings(
            "ExplicitScopeInNestedScope",
            source,
            outerReceiverReferences = setOf("name"),
        )
        assertEquals(1, findings.size)
        assertTrue(findings.single().corrections.isEmpty())
        assertTrue(findings("ExplicitScopeInNestedScope", source).isEmpty())
    }

    /** Main-thread blocking requires semantic thread proof and guards its suspend correction. */
    @Test
    fun `main thread blocking is semantic and correction reaches fixed point`() {
        val source = "suspend fun handle() { Thread.sleep(10) }"
        val callText = "sleep(10)"
        val finding = findings(
            "MainThreadBlocking",
            source,
            resolvedCalls = mapOf(callText to "java.lang.Thread.sleep"),
            expressionFacts = mapOf(
                callText to InspectionExpressionFacts(suspendContext = true, mainThreadContext = true),
            ),
        ).single()
        val corrected = finding.corrections.single().applyTo(source, source.sha256())

        assertTrue("withContext(kotlinx.coroutines.Dispatchers.IO)" in corrected)
        val mineKotCorrected = findings(
            "MainThreadBlocking",
            source,
            resolvedCalls = mapOf(callText to "java.lang.Thread.sleep"),
            expressionFacts = mapOf(
                callText to InspectionExpressionFacts(suspendContext = true, mainThreadContext = true),
            ),
            availableSymbols = setOf("org.minekot.kotlin.coroutines.mineKotIo"),
        ).single().corrections.single().applyTo(source, source.sha256())
        assertTrue("org.minekot.kotlin.coroutines.mineKotIo" in mineKotCorrected)
        assertTrue(
            findings(
                "MainThreadBlocking",
                source,
                resolvedCalls = mapOf(callText to "java.lang.Thread.sleep"),
                expressionFacts = mapOf(
                    callText to InspectionExpressionFacts(suspendContext = false, mainThreadContext = true),
                ),
            ).single().corrections.isEmpty(),
        )
        assertTrue(
            findings(
                "MainThreadBlocking",
                source,
                resolvedCalls = mapOf(callText to "java.lang.Thread.sleep"),
                expressionFacts = mapOf(
                    callText to InspectionExpressionFacts(suspendContext = true, mainThreadContext = false),
                ),
            ).isEmpty(),
        )
    }

    /** MineKot replacement warning requires suspend compatibility and classpath availability. */
    @Test
    fun `resolved api preference prioritizes available MineKot helper`() {
        val source = "suspend fun load() = runCatching { Unit }"
        val callText = "runCatching { Unit }"
        val resolvedCalls = mapOf(callText to "kotlin.runCatching")
        val facts = mapOf(callText to InspectionExpressionFacts(suspendContext = true))

        assertEquals(
            1,
            findings(
                "ResolvedApiPreference",
                source,
                resolvedCalls = resolvedCalls,
                expressionFacts = facts,
                availableSymbols = setOf("org.minekot.kotlin.coroutines.runMineKotCatchingCancellable"),
            ).size,
        )
        assertTrue(
            findings(
                "ResolvedApiPreference",
                source,
                resolvedCalls = resolvedCalls,
                expressionFacts = facts,
            ).isEmpty(),
        )
    }

    @OptIn(CompilerConfiguration.Internals::class, K1Deprecation::class)
    private fun findings(
        alias: String,
        source: String,
        filename: String = "Test.kt",
        resolvedCalls: Map<String, String> = emptyMap(),
        outerReceiverReferences: Set<String> = emptySet(),
        expressionFacts: Map<String, InspectionExpressionFacts> = emptyMap(),
        availableSymbols: Set<String> = emptySet(),
        options: Map<String, InspectionOptionValue> = emptyMap(),
    ): List<InspectionFinding> {
        val disposable = Disposer.newDisposable()
        val environment = KotlinCoreEnvironment.createForProduction(
            disposable,
            CompilerConfiguration().apply { put(CommonConfigurationKeys.MODULE_NAME, "migration-test") },
            EnvironmentConfigFiles.JVM_CONFIG_FILES,
        )
        try {
            val file = KtPsiFactory(environment.project, false).createFile(filename, source)
            val findings = mutableListOf<InspectionFinding>()
            val inspection: MineKotInspection = assertNotNull(catalog.createInspection(alias))
            val descriptor = catalog.inspections.single { alias == it.id || alias in it.aliases }
            val context = InspectionContext(
                file = file,
                fileId = filename,
                sourceText = source,
                sourceSha256 = source.sha256(),
                options = descriptor.validateOptions(options),
                suppressionResolver = InspectionSuppressionResolver { _, _ -> false },
                capabilities = object :
                    ExplicitOuterReceiverCapability,
                    ExpressionFactsCapability,
                    SymbolAvailabilityCapability {
                    override fun supports(capabilityId: String): Boolean = false

                    override fun resolveCall(expression: KtCallExpression): ResolvedInspectionCall? =
                        resolvedCalls[expression.text]?.let(::ResolvedInspectionCall)

                    override fun isImplicitOuterReceiver(expression: KtNameReferenceExpression): Boolean =
                        expression.text in outerReceiverReferences

                    override fun expressionFacts(expression: KtExpression): InspectionExpressionFacts? =
                        expressionFacts[expression.text]

                    override fun isSymbolAvailable(symbolId: String): Boolean = symbolId in availableSymbols
                },
            )
            inspection.createSession(context).use { session -> file.accept(session.createVisitor(findings::add)) }
            return findings
        } finally {
            Disposer.dispose(disposable)
        }
    }

    private data class RuleCase(
        val shouldFlag: String,
        val shouldNotFlag: String,
        val filename: String = "Test.kt",
        val resolvedCalls: Map<String, String> = emptyMap(),
        val outerReceiverReferences: Set<String> = emptySet(),
        val expressionFacts: Map<String, InspectionExpressionFacts> = emptyMap(),
        val availableSymbols: Set<String> = emptySet(),
    )

    private data class WhitespaceCase(
        val messageId: String,
        val source: String,
        val corrected: String,
    )

    private companion object {
        private const val WILDCARD_IMPORT_THRESHOLD: Int = 5
        private const val SECOND_IMPORT_INDEX: Int = 2
        private const val LONG_LINE_NAME_COUNT: Int = 8
        private const val LONG_FALLBACK_PART_COUNT: Int = 24

        val RULE_CASES: Map<String, RuleCase> = mapOf(
            "CommentFormatting" to RuleCase("fun run() {\n//compact\n}", "fun run() {\n    // compact\n}"),
            "CoroutinePreference" to RuleCase(
                "fun run() { Thread() }",
                "fun run() { Thread() }",
                resolvedCalls = mapOf("Thread()" to "java.lang.Thread.<init>"),
            ),
            "ExplicitScopeInNestedScope" to RuleCase(
                "class A { val name = \"x\"; fun run() { listOf(1).forEach { println(name) } } }",
                "class A { fun run() { listOf(1).forEach { println(it) } } }",
                outerReceiverReferences = setOf("name"),
            ),
            "ForEachPreference" to RuleCase(
                "fun run(values: List<Int>) { for (value in values) { println(value) } }",
                "fun run(values: List<Int>) { for (index in values.indices) { println(index) } }",
            ),
            "ForbiddenTryCatch" to RuleCase(
                "fun run() { try { Unit } catch (failure: RuntimeException) { println(failure) } }",
                "fun run() = try { Unit } catch (failure: java.io.IOException) { Unit }",
            ),
            "GradleDslConventions" to RuleCase(
                "apply(plugin = \"java\")",
                "plugins {\n    id(\"java\")\n}",
                filename = "build.gradle.kts",
            ),
            "GradleRepositoryPolicy" to RuleCase(
                "repositories {\n    mavenCentral()\n    mavenLocal()\n}",
                "repositories {\n    mavenLocal()\n    mavenCentral()\n}",
                filename = "build.gradle.kts",
            ),
            "GradleDependencyPolicy" to RuleCase(
                "dependencies {\n    implementation(\"sample:library:1.+\")\n}",
                "dependencies {\n    implementation(libs.sample.library)\n}",
                filename = "build.gradle.kts",
            ),
            "GradleTaskContract" to RuleCase(
                "tasks.getByName(\"check\")",
                "tasks.named(\"check\")",
                filename = "build.gradle.kts",
            ),
            "GradleTestConfiguration" to RuleCase(
                "tasks.named<Test>(\"test\") {\n    useJUnitPlatform()\n}",
                "tasks.named<Test>(\"test\") {\n    useJUnitPlatform()\n    maxParallelForks = 2\n}",
                filename = "build.gradle.kts",
            ),
            "GradlePerformance" to RuleCase(
                "afterEvaluate { println(\"configured\") }",
                "tasks.named(\"check\") { enabled = true }",
                filename = "build.gradle.kts",
            ),
            "ImportPolicy" to RuleCase(
                "import java.util.ArrayList\nfun run() = ArrayList<String>()",
                "import java.util.*\nfun run() = ArrayList<String>()",
            ),
            "KotlinxPreference" to RuleCase(
                "import java.nio.file.Files\nimport java.nio.file.Path\nfun run(path: Path) = Files.exists(path)",
                "import java.nio.file.Path\nimport kotlin.io.path.exists\nfun run(path: Path) = path.exists()",
            ),
            "LineWrapping" to RuleCase(
                "fun run() = consume(firstArgument, secondArgument, thirdArgument, fourthArgument, fifthArgument, " +
                        "sixthArgument, seventhArgument)",
                "fun run() = consume(value)",
            ),
            "MagicNumber" to RuleCase("fun answer() = 42", "fun binary(value: Int) = value + 1"),
            "MainThreadBlocking" to RuleCase(
                "suspend fun run() { Thread.sleep(10) }",
                "suspend fun run() { Thread.sleep(10) }",
                resolvedCalls = mapOf("sleep(10)" to "java.lang.Thread.sleep"),
                expressionFacts = mapOf(
                    "sleep(10)" to InspectionExpressionFacts(suspendContext = true, mainThreadContext = true),
                ),
            ),
            "MiniMessageText" to RuleCase(
                "fun run() { audience.sendMessage(Component.text(\"&aHello\")) }",
                "fun run() { println(\"&aHello\") }",
                resolvedCalls = mapOf(
                    "text(\"&aHello\")" to "net.kyori.adventure.text.Component.text",
                    "sendMessage(Component.text(\"&aHello\"))" to
                            "net.kyori.adventure.audience.Audience.sendMessage",
                ),
            ),
            "MissingKDoc" to RuleCase("class Service", "/** Service contract. */ class Service"),
            "ResolvedApiPreference" to RuleCase(
                "fun run() = java.io.File(\"x\")",
                "fun run() = kotlin.io.path.Path(\"x\")",
                resolvedCalls = mapOf("File(\"x\")" to "java.io.File.<init>"),
            ),
            "ResultHandling" to RuleCase(
                "fun run() { runCatching { Unit } }",
                "fun run(): Result<Unit> = runCatching { Unit }",
            ),
            "SourceFilePolicy" to RuleCase("\uFEFFfun run() = Unit", "fun run() = Unit"),
            "StringTemplateBraces" to RuleCase(
                "fun greet(name: String) = \"Hello, ${'$'}name\"",
                "fun greet(name: String) = \"Hello, ${'$'}{name}\"",
            ),
            "TrailingComma" to RuleCase(
                "fun run() = listOf(\n    1,\n    2\n)",
                "fun run() = listOf(1, 2)",
            ),
            "WhitespaceFormatting" to RuleCase("fun run() = 1+2", "fun run() = 1 + 2"),
        )
    }
}

private fun String.sha256(): String =
    MessageDigest.getInstance("SHA-256").digest(toByteArray()).joinToString("") { "%02x".format(it) }
