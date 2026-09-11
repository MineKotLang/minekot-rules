# MineKot rule coverage

This document is the canonical roadmap for concrete MineKot inspections, corrections, options, and behavioral fixtures. Shared SPI and adapters belong to [`minekot-inspections`](https://github.com/MineKotLang/minekot-inspections/blob/master/CHECKS.md). Gradle/Detekt execution belongs to `minekot-toolchain`; IntelliJ activation belongs to `minekot-toolkit`.

## Status meanings

- **Active**: deterministic, enabled by default, and covered by positive and negative fixtures.
- **Opt-in**: useful but requires project intent or stronger semantic proof.
- **Report-only**: deterministic diagnostic without a universally safe correction.
- **Human review**: subjective requirement intentionally excluded from blocking automation.

## Kotlin rules

| Requirement | Rule | Default | Correction |
|:------------|:-----|:--------|:-----------|
| Comment marker and indentation formatting | `minekot.codestyle.comment-formatting` | Active | Guarded |
| Safe 120-column structural wrapping | `minekot.codestyle.line-wrapping` | Active | Guarded for understood PSI shapes |
| Generic overlong strings and unresolved expressions | `minekot.codestyle.line-wrapping` | Report-only | None |
| String-template braces | `minekot.codestyle.string-template-braces` | Active | Guarded |
| Trailing commas | `minekot.codestyle.trailing-comma` | Active | Guarded |
| Operator, comma, colon, arrow, brace, blank-line, tab, and trailing whitespace | `minekot.codestyle.whitespace-formatting` | Active | Guarded |
| Import thresholds and nested-class imports | `minekot.codestyle.import-policy` | Active | Guarded where import replacement is complete |
| Formatter controls and byte-order mark | `minekot.codestyle.source-file-policy` | Active | Guarded where PSI preserves source bytes |
| Cancellation-safe exception boundaries | `minekot.correctness.forbidden-try-catch` | Active | Report-only |
| Explicit Result consumption | `minekot.correctness.result-handling` | Active | Report-only |
| Resolved Kotlin, kotlinx, and available MineKot replacements | `minekot.codestyle.resolved-api-preference` | Active | Report-only |
| Direct scheduler and concurrency APIs | `minekot.concurrency.coroutine-preference` | Active | Report-only |
| Blocking work on proven main-thread handlers | `minekot.concurrency.main-thread-blocking` | Active | Guarded IO-dispatch wrapper in suspend contexts |
| Explicit outer receiver in nested scopes | `minekot.codestyle.explicit-scope-in-nested-scope` | Opt-in weak warning | Report-only |
| Intent-bearing numeric literals | `minekot.codestyle.magic-number` | Opt-in weak warning | Report-only |
| Public and protected API contracts | `minekot.codestyle.missing-kdoc` | Active | Report-only |
| Adventure MiniMessage sinks | `minekot.codestyle.minimessage-text` | Active | Report-only |
| Universal loop conversion | `minekot.codestyle.for-each-preference` | Deprecated and disabled | None |
| Name-based JDK preference | `minekot.codestyle.kotlinx-preference` | Deprecated and disabled | Replaced by resolved API rule |

Raw UTF-8, LF, and final-newline verification remains a repository-host check because PSI may normalize source bytes. American English, comment necessity, architectural modularity, non-obvious internal documentation, and conceptual extension extraction require human review.

## Gradle Kotlin DSL rules

| Requirement | Rule | Default |
|:------------|:-----|:--------|
| Plugin/block organization, type-safe accessors, convention boundaries, and container names | `minekot.codestyle.gradle-dsl-conventions` | Active |
| Repository placement, ordering, and concise syntax | `minekot.gradle.repository-policy` | Active |
| Fixed dependency versions and supported dependency notation | `minekot.gradle.dependency-policy` | Active |
| Lazy task registration, action naming, and typed complex work | `minekot.gradle.task-contract` | Active |
| Explicit test framework and parallelism configuration | `minekot.gradle.test-configuration` | Active |
| Configuration avoidance, cache inputs, and task cache policy | `minekot.performance.gradle-configuration` | Active |

Version catalogs, `gradle.properties`, raw file policy, dependency graphs, and cross-project convention duplication require repository context and remain toolchain checks.

## Fixture contract

Every catalog entry must provide positive and negative fixtures, exact stable message IDs, valid source ranges, suppression behavior, default/option behavior, malformed-source safety, and localized presentation text. Every correction must assert exact output, stale guards, overlap rejection, formatter preservation, second-run idempotence, and fixed-point behavior. Semantic rules additionally cover missing capabilities, unavailable replacements, overload identities, boundary packages, and host-provided facts.

Detekt and IDEA adapters must produce equal rule IDs, ranges, messages, severities, and corrected bytes for same source and policy. Real-source regressions include MineKot `Selection.kt` arrow spacing.
