# SmartMovie Android testing guide

## Purpose

The native Android lane covers the Play app and Wear companion; the Compose Multiplatform lane covers desktop JVM, JavaScript, and Wasm. They run independently for diagnosis, but every lane blocks the coordinated SmartMovie 3.0 release train.

## Native test matrix

| Area | Tests | Responsibilities |
| --- | ---: | --- |
| App and screenshot validation | Variable | Backup/privacy rules plus phone, tablet, TV, Profile/detail behavior, and golden previews |
| Data | Variable | Paging, Room migrations, local-first library merge, durable account/library outboxes, restart, retry, and acknowledgement checks |
| Domain model and contract | Variable | `/v1` + `/v2` discriminator fixtures, nullable/unknown fields, pagination, and additive compatibility |
| Network and error contract | Variable | Catalog/account routes, normalized errors, retry/cancellation, auth state, CSRF, and unknown-field compatibility |
| Phone/Wear remote protocol | Variable | Backward-compatible safe-context messages, exact episode identity, reachability, stale-command rejection, title-only mutations, and Wear state |
| Feature models | Variable | Home, Explore, entity Search/Detail, media-gallery filtering/deduplication, Library, Profile, adult PIN, rating, and custom-list state |

Do not hard-code a release verdict to a historical test count. The authoritative result is zero failures from the current tasks plus committed Room schema and contract checks.

## Local verification

Run Doctor first so missing SDKs and JDKs are reported separately from product failures:

```bash
./scripts/doctor.sh
```

Use JDK 17 for the native Android lane, then run the same non-device checks as CI:

```bash
./scripts/verify-release.sh mobile

./gradlew --dependency-verification=strict \
  :core:model:test \
  :core:remote:test \
  testDebugUnitTest \
  validateDebugScreenshotTest \
  lintDebug \
  :app:assembleDebug \
  :wear:assembleDebug

git diff --exit-code -- core/database/schemas
```

To force a fresh test execution rather than accept Gradle's `UP-TO-DATE` result:

```bash
./gradlew --dependency-verification=strict --rerun-tasks \
  :core:model:test \
  :core:remote:test \
  testDebugUnitTest \
  validateDebugScreenshotTest
```

The 27 August 2026 catalog metadata gate passed the full 1,074-task native Android suite (unit tests, lint, screenshot validation, main/Wear debug builds, instrumentation APK, and both release bundles) plus KMP desktop tests/compile, JavaScript distribution, and optimized Wasm distribution. The current media slice adds native Android and KMP presentation tests for stable image/video deduplication, valid YouTube-only filtering, sorted non-blank external IDs, and canonical non-empty Movie/TV/Season/Episode media fixtures with runtime/production/vote metadata. The snapshot also verifies typed Movie/TV alternative titles, Movie release events, TV content ratings, translations, missing nullable values, unknown fields, exact device-or-override region selection, and localized title preference. Companion coverage verifies backward-compatible title messages, exact series/season/episode handoff, and suppression of episode trailer/library commands. The previous capability gate covers canonical `browser_auth`/`tv_qr_auth` fixture decoding, missing/false/true fail-closed behavior, phone-versus-TV mode selection, cold-start callback deferral, stale in-flight completion invalidation, durable outbox isolation while disabled, disabled auth/profile requests, and localized unavailable UI. Advanced Discover coverage includes `/v1` basic fallback routing, capability-gated Profile provider-region loading, complete Movie/TV date/language/country/certification/runtime/vote/provider/monetization queries, device-or-override region, explicit local age confirmation, six-digit adult PIN validation, five-attempt/five-minute lockout parity, deterministic encoding, filter normalization, stale-provider clearing, regional provider configuration, and canonical fixture decoding. CI device jobs remain authoritative for emulator input/launch behavior.

## Emulator verification

Phone instrumentation requires an API 35 Google APIs x86_64 system image:

```bash
./gradlew --dependency-verification=strict connectedDebugAndroidTest
```

The TV smoke lane requires an API 36 Android TV x86_64 image. It installs the main debug APK, launches `com.lamndt.smartmovie.TvActivity`, sends left/down/center/back D-pad input, and confirms that the TV activity remains active.

The 17 August 2026 local device run was not executed because the only configured API 34 AVD was incomplete: its SDK directory was approximately 35 MB and did not contain `system.img`. No physical device was connected. This is a local SDK blocker rather than an application failure; the API 35 phone and API 36 TV jobs remain required in GitHub Actions before release.

The latest local KMP gate passed desktop tests and non-incremental compile, JavaScript development distribution, optimized Wasm distribution, and a portable macOS application image with strict dependency verification. The macOS ARM packaging path was also simulated with `os.arch=aarch64`; its Compose Desktop POM and Skiko runtime JAR/POM checksums were matched byte-for-byte against the SHA-256 values published by Maven Central before the distributable completed. CI must reproduce portable images on macOS, Windows, and Linux.

## Release interpretation

- A native unit, lint, golden, APK, instrumentation, or TV smoke failure blocks the Android candidate.
- A desktop test/compile/package, JavaScript, or Wasm failure blocks the coordinated release train.
- Missing local JDKs, system images, signing secrets, or devices are environment blockers and must not be recorded as product regressions.
- The main and Wear AABs must share application ID, semantic version, and signing key.
- `catalog-contract/manifest.json` must match the canonical OpenAPI checksum, fixture checksum, contract version, and release train before production promotion.
