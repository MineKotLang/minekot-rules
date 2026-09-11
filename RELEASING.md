# Release guide

`minekot-rules` publishes signed GitHub Release artifacts after compatible `minekot-inspections-core` is available from MineKot Maven.

## One-time repository setup

1. Create public repository `MineKotLang/minekot-rules` with default branch `master`.
2. Add repository remote as `origin`.
3. Protect `master`; require minimum/current core jobs from `Build and check` before merge.
4. Allow release workflow `contents: write` and `id-token: write` permissions.

No Maven credentials are required. Keyless Sigstore signing uses GitHub Actions OIDC and accepts only canonical repository workflow identity.

## Before first push

1. Publish `org.minekot.inspections:minekot-inspections-core:1.0.0`.
2. Confirm both core linkage matrix entries in `.github/workflows/build.yml` select supported published versions.
3. Run `./gradlew clean check --warning-mode=fail --no-scan --no-daemon --no-configuration-cache`.
4. Confirm `master` contains intended release state. Every push to canonical `master` creates `v1.0.<run_number>`.

## Release evidence

Successful release must contain exactly:

- `minekot-rules-1.0.N.jar`
- `minekot-rules-1.0.N.manifest.json`
- `minekot-rules-1.0.N.manifest.sigstore.json`
- `SHA256SUMS`

Release workflow verifies Sigstore certificate issuer and workflow identity before upload. Stable channel updates only after immutable release verification. Reruns accept existing assets only when JAR, manifest, checksums, and signature verify exactly.
