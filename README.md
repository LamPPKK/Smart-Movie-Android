# SmartMovie 2.0

[![Android CI](https://github.com/LamPPKK/Android.Smart.Movie/actions/workflows/android-ci.yml/badge.svg?branch=main)](https://github.com/LamPPKK/Android.Smart.Movie/actions/workflows/android-ci.yml)
[![Compose Multiplatform CI](https://github.com/LamPPKK/Android.Smart.Movie/actions/workflows/multiplatform-ci.yml/badge.svg?branch=main)](https://github.com/LamPPKK/Android.Smart.Movie/actions/workflows/multiplatform-ci.yml)

SmartMovie is a cinematic Movies + TV catalog. The native Android application supports phones, tablets, foldables, ChromeOS, Android TV, Android XR Home Space, and Wear OS companion watches. A separate Compose Multiplatform application reuses one Kotlin UI/data layer across macOS, Windows, Linux, and Web/Wasm. The native SwiftUI application in the companion `SmartMovie` repository is the only Apple mobile client.

See the [screen gallery](docs/SCREENSHOTS.md) for the main app flows and responsive device layouts. [Platform support](docs/PLATFORM_SUPPORT.md) defines the exact experience and release boundary on every device class, including why Android Auto is not declared.

The multiplatform project is isolated under [`multiplatform/`](multiplatform/README.md), so its current Kotlin/Compose toolchain cannot destabilize the locked Android/Play build.

## Requirements

- JDK 17 for the native Android build
- JDK 21 for the Compose desktop/web build
- Android SDK 36 and Build Tools 36.0.0
- No TMDb or Cloudflare credential is stored in the app or repository

Run `./scripts/doctor.sh` before local verification. It reports missing toolchains without changing the machine.

Lifecycle is pinned to `2.10.0`. Lifecycle `2.11.0` declares `minCompileSdk 37`, which conflicts with this release's locked `compileSdk 36`; the remaining requested toolchain and library versions are pinned in the version catalog.

The debug build calls `https://staging-catalog.smartmovie.app/`; release calls `https://catalog.smartmovie.app/`. Both expose only the SmartMovie Worker `/v1` contract and must be backed by the matching Cloudflare custom domains before a store release.

`catalog-contract/` is a checksummed snapshot of the canonical OpenAPI 3.1 document and fixtures from the Apple/backend repository. Run `./scripts/verify-release.sh mobile` for native Android, `./scripts/verify-release.sh kmp` for desktop/web, or omit the scope to check both. Native Android and desktop tests decode the same fixtures, and the Worker cannot promote a new production contract until Android `main` pins its OpenAPI and fixture checksums.

The Android release workflow additionally requires `catalog-contract/manifest.json` to contain the 40-character upstream commit written by the cross-repository sync workflow. The local bootstrap marker is valid for development tests only and cannot produce a store candidate.

## Build and verification

```bash
./gradlew --dependency-verification=strict \
  :core:model:test \
  :core:remote:test \
  testDebugUnitTest \
  validateDebugScreenshotTest \
  lintDebug \
  :app:assembleDebug \
  :wear:assembleDebug \
  :app:assembleDebugAndroidTest \
  :app:bundleRelease \
  :wear:bundleRelease
```

The native automated baseline currently contains 38 unit, contract, remote, feature, and screenshot-validation tests, including four deterministic golden previews for compact phone, expanded tablet, 1080p TV, and a round Wear OS remote. CI additionally runs Compose instrumentation tests on an API 35 phone emulator and a dedicated Android TV launch/D-pad smoke test. See the [testing guide](docs/TESTING.md) for the suite breakdown, local commands, and emulator requirements.

Compose Multiplatform verification is independent:

```bash
cd multiplatform
./gradlew --dependency-verification=strict :composeApp:desktopTest \
  :composeApp:compileKotlinDesktop \
  :composeApp:jsBrowserDevelopmentExecutableDistribution \
  :composeApp:wasmJsBrowserDistribution
```

Running `bundleRelease` without signing environment variables produces unsigned local verification bundles. Use the protected `Android release AAB` workflow to create both signed Play artifacts.

## Architecture

- `app`: adaptive mobile entry point, dedicated Android TV entry point, Navigation 3, and constructor-injected `AppContainer`
- `core:model`: immutable domain contracts and repository interfaces
- `core:remote`: versioned phone/watch commands and state serialization
- `core:network`: Retrofit 3, Kotlin Serialization, installation UUID, retry/cancellation policy
- `core:database`: Room library and committed schema
- `core:data`: repositories, image URL policy, and Paging sources
- `core:designsystem`: cinematic palette, offline Newsreader/Manrope fonts, and shared accessible components
- `feature:*`: Home, Explore, Search, Library, Detail, and About
- `wear`: non-standalone Wear OS remote using Compose for Wear OS and the Google Play services Data Layer
- `catalog-contract`: versioned snapshot of the canonical Worker OpenAPI document and decoder fixtures
- `multiplatform/composeApp`: shared UDF state, Worker client, persistent library, adaptive UI, and desktop/web entry points

The UI uses unidirectional data flow with immutable `UiState`, `StateFlow`, and screen-level ViewModels. Large windows switch to a navigation rail and list-detail layout; ChromeOS receives Ctrl/Cmd+1–4 tab shortcuts and Ctrl/Cmd+F for Search. Android TV uses a separate 10-foot composition with TV navigation, D-pad focus treatment, search IME, and retained catalog state behind the detail overlay. Wear OS mirrors the active phone detail and can open it, launch its trailer, or toggle Favorite and Watchlist.

## Private release inputs

The protected `production` GitHub environment must provide:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The Worker environments in the native Apple/backend repository require `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID`, and a rotated `TMDB_BEARER_TOKEN`.

Google Auto Backup includes only `smartmovie_library.db`; HTTP cache and the installation UUID are deliberately excluded. SmartMovie has no account, analytics, ads, IAP, or real-time cross-device synchronization.

On Compose Multiplatform, the library remains local to each platform: Java Preferences on desktop and `localStorage` on web. Notarized macOS and signed Windows installers require the corresponding identities in protected release environments.

## Release checklist

1. Revoke the historical TMDb credential and add a newly issued `TMDB_BEARER_TOKEN` to the protected staging and production environments.
2. Configure the `staging-catalog.smartmovie.app` and `catalog.smartmovie.app` Cloudflare custom domains, then run the Worker workflow with staging smoke tests for all six locales.
3. Add the four Android signing secrets above and run the protected `Android release AAB` workflow; upload both the main and Wear companion AABs to the same Play listing.
4. Confirm Android CI passes its unit, lint, golden, phone emulator, and TV emulator jobs before uploading the AABs to Play Console.
