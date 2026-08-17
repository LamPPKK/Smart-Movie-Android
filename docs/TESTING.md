# SmartMovie Android testing guide

## Purpose

The native Android verification lane covers the Play app and Wear companion independently from Compose Multiplatform desktop/web releases. Contract fixtures are shared across the native and desktop clients, but a desktop or web failure does not block an Android store candidate unless the catalog contract itself is incompatible.

## Native test matrix

| Area | Tests | Responsibilities |
| --- | ---: | --- |
| App and screenshot validation | 4 | Backup rules plus phone, tablet, and TV golden previews |
| Data | 6 | Repository behavior, Paging sources, Favorite/Watchlist storage, and offline reads |
| Domain model and contract | 6 | Catalog model invariants plus canonical fixture decoding and additive compatibility |
| Network and error contract | 8 | Six-route decoding, normalized errors, retry, cancellation, and unknown-field compatibility |
| Phone/Wear remote protocol | 7 | Versioned remote messages, reachability behavior, and Wear ViewModel state |
| Feature models | 7 | Home, Explore, Search, Detail, and Library state transitions |

Total native baseline: 38 tests across 17 result suites. Compose Multiplatform desktop contract and state tests are reported separately by the multiplatform CI lane.

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

On 17 August 2026, the fresh command executed 292 Gradle tasks and all 38 native tests passed with zero failures, errors, or skips. The complete non-device gate also passed lint, screenshot validation, the main debug APK, the Wear debug APK, release/contract consistency, and the committed Room schema check.

## Emulator verification

Phone instrumentation requires an API 35 Google APIs x86_64 system image:

```bash
./gradlew --dependency-verification=strict connectedDebugAndroidTest
```

The TV smoke lane requires an API 36 Android TV x86_64 image. It installs the main debug APK, launches `com.lamndt.smartmovie.TvActivity`, sends left/down/center/back D-pad input, and confirms that the TV activity remains active.

The 17 August 2026 local device run was not executed because the only configured API 34 AVD was incomplete: its SDK directory was approximately 35 MB and did not contain `system.img`. No physical device was connected. This is a local SDK blocker rather than an application failure; the API 35 phone and API 36 TV jobs remain required in GitHub Actions before release.

## Release interpretation

- A native unit, lint, golden, APK, instrumentation, or TV smoke failure blocks the Android candidate.
- Missing local JDKs, system images, signing secrets, or devices are environment blockers and must not be recorded as product regressions.
- The main and Wear AABs must share application ID, semantic version, and signing key.
- `catalog-contract/manifest.json` must match the canonical OpenAPI checksum, fixture checksum, contract version, and release train before production promotion.
