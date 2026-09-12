# Changelog

All notable changes to this project will be documented in this file.

## Unreleased

### Changed

- Update the verified build and release tuple to Gradle 9.7.1, Kotlin 2.4.20, Detekt 2.0.0-alpha.6, IntelliJ IDEA 2026.1.5, inspections core 1.0.2, and Develocity 4.5.1. IDEA 2026.2 remains excluded until JetBrains resolves plugin-test startup blocker IJPL-248701.
- Generate signed release manifests from a typed serialization model, dependency catalog values, and the JAR service declaration instead of a duplicated JSON template.

### Features

- Add focused Gradle repository, dependency, task, and test-configuration inspections.
- Add semantic main-thread blocking detection with MineKot-first IO correction and generic coroutine fallback.
- Add report-only generic line-length and indentation-width diagnostics.

### Fixes

- Emit standard Sigstore bundles consumable by JVM hosts and withdraw legacy-bundle generation `1.0.2` from the signed stable channel.
- Restore disabled defaults for deprecated or intent-sensitive rules, remove unsafe loop rewriting, and make non-public KDoc judgment opt-in.
- Prefer available cancellation-safe MineKot catching helpers only in compatible suspend contexts.

### Tests and documentation

- Expand every-rule, Gradle branch, lifecycle, correction, helper-availability, formatter, and real-source regression matrices.
- Establish `CHECKS.md` as canonical concrete inspection roadmap.
