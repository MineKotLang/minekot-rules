# MineKot rules

`minekot-rules` is the thin, dynamically distributed rule catalog for MineKot Kotlin and Gradle Kotlin DSL sources. Rules implement the published `minekot-inspections-core` SPI; host-specific Detekt and IntelliJ adapters remain in `minekot-inspections`.

[CHECKS.md](./CHECKS.md) is canonical coverage and implementation roadmap. Host repositories link to it instead of duplicating rule status.

## Architecture

- `MineKotRulesCatalog` exposes immutable descriptors and lazy inspection factories through `ServiceLoader`.
- Each inspection creates a file-scoped PSI visitor and reports pure findings and guarded text corrections.
- The JAR compiles against `minekot-inspections-core` and Kotlin PSI with `compileOnly` scope. It does not shade host, Kotlin, PSI, Detekt, or IntelliJ classes.
- Stable rule IDs and deprecated aliases live in `RuleContracts`. They are shared machine contracts for configuration, suppressions, findings, and adapters.
- Rule names, descriptions, diagnostics, correction labels, and option descriptions live in the English `MineKotRulesBundle.properties` language resource. Production visitors do not embed user-facing English.

## Distribution

The project is not published to Maven. The release workflow builds a reproducible thin JAR, creates its signed manifest and checksums, tags the canonical commit, and attaches the artifacts to a GitHub Release. MineKot toolchain and toolkit hosts verify and cache that release before loading the catalog.

## Creating a rule

1. Add the inspection under the closest domain package.
2. Add its stable ID and legacy alias to `RuleContracts`; never reuse an ID.
3. Add its descriptor metadata and every user-facing diagnostic or correction label to `src/main/resources/messages/MineKotRulesBundle.properties`.
4. Register its descriptor and lazy factory in `MineKotRulesCatalog`.
5. Add positive, negative, exact-range, correction-output, and second-run idempotence fixtures.

The localization contract test rejects inline descriptor IDs and presentation strings at their production use sites.

## Verification

Run the local release gate:

```bash
./gradlew check --no-scan --no-configuration-cache
```

Release maintainers must follow [RELEASING.md](./RELEASING.md). Pull requests run minimum/current published-core linkage before any push reaches release-producing `master`.

`check` compiles and tests the catalog, verifies coverage, validates the service entry, and scans the produced JAR to ensure it remains thin.
