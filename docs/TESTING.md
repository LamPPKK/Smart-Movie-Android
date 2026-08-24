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
| Phone/Wear remote protocol | Variable | Versioned safe-context messages, reachability, stale-command rejection, and Wear state |
| Feature models | Variable | Home, Explore, entity Search/Detail, Library, Profile, adult PIN, rating, and custom-list state |

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

The latest local 3.0 feature gate passed unit tests, lint, screenshot validation, main/Wear debug APKs, and strict dependency verification. CI device jobs remain authoritative for emulator input/launch behavior.

## Emulator verification

Phone instrumentation requires an API 35 Google APIs x86_64 system image:

```bash
./gradlew --dependency-verification=strict connectedDebugAndroidTest
```

The TV smoke lane requires an API 36 Android TV x86_64 image. It installs the main debug APK, launches `com.lamndt.smartmovie.TvActivity`, sends left/down/center/back D-pad input, and confirms that the TV activity remains active.

The 17 August 2026 local device run was not executed because the only configured API 34 AVD was incomplete: its SDK directory was approximately 35 MB and did not contain `system.img`. No physical device was connected. This is a local SDK blocker rather than an application failure; the API 35 phone and API 36 TV jobs remain required in GitHub Actions before release.

The latest local KMP gate passed desktop tests and non-incremental compile, JavaScript development distribution, optimized Wasm distribution, and a portable macOS application image with strict dependency verification. CI must reproduce portable images on macOS, Windows, and Linux.

## Release interpretation

- A native unit, lint, golden, APK, instrumentation, or TV smoke failure blocks the Android candidate.
- A desktop test/compile/package, JavaScript, or Wasm failure blocks the coordinated release train.
- Missing local JDKs, system images, signing secrets, or devices are environment blockers and must not be recorded as product regressions.
- The main and Wear AABs must share application ID, semantic version, and signing key.
- `catalog-contract/manifest.json` must match the canonical OpenAPI checksum, fixture checksum, contract version, and release train before production promotion.
