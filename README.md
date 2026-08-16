# SmartMovie Android 2.0

[![Android CI](https://github.com/LamPPKK/Android.Smart.Movie/actions/workflows/android-ci.yml/badge.svg?branch=main)](https://github.com/LamPPKK/Android.Smart.Movie/actions/workflows/android-ci.yml)

SmartMovie is a cinematic Movies + TV catalog for Android phones, tablets, foldables, and Android TV. Version 2.0 is a clean Kotlin/Jetpack Compose rewrite that shares one application ID and one AAB across form factors.

## Requirements

- JDK 17
- Android SDK 36 and Build Tools 36.0.0
- No TMDb or Cloudflare credential is stored in the app or repository

Lifecycle is pinned to `2.10.0`. Lifecycle `2.11.0` declares `minCompileSdk 37`, which conflicts with this release's locked `compileSdk 36`; the remaining requested toolchain and library versions are pinned in the version catalog.

The debug build calls `https://staging-catalog.smartmovie.app/`; release calls `https://catalog.smartmovie.app/`. Both expose only the SmartMovie Worker `/v1` contract and must be backed by the matching Cloudflare custom domains before a store release.

## Build and verification

```bash
./gradlew --dependency-verification=strict \
  :core:model:test \
  testDebugUnitTest \
  validateDebugScreenshotTest \
  lintDebug \
  :app:assembleDebug \
  :app:assembleDebugAndroidTest \
  :app:bundleRelease
```

The current automated baseline is 30 Android unit tests and three deterministic golden previews for compact phone, expanded tablet, and 1080p TV. CI additionally runs Compose instrumentation tests on an API 35 phone emulator and a dedicated Android TV launch/D-pad smoke test.

Running `bundleRelease` without signing environment variables produces an unsigned local verification bundle. Use the protected `Android release AAB` workflow to create the signed Play artifact.

## Architecture

- `app`: adaptive mobile entry point, dedicated Android TV entry point, Navigation 3, and constructor-injected `AppContainer`
- `core:model`: immutable domain contracts and repository interfaces
- `core:network`: Retrofit 3, Kotlin Serialization, installation UUID, retry/cancellation policy
- `core:database`: Room library and committed schema
- `core:data`: repositories, image URL policy, and Paging sources
- `core:designsystem`: cinematic palette, offline Newsreader/Manrope fonts, and shared accessible components
- `feature:*`: Home, Explore, Search, Library, Detail, and About

The UI uses unidirectional data flow with immutable `UiState`, `StateFlow`, and screen-level ViewModels. Android TV uses a separate 10-foot composition with TV navigation, D-pad focus treatment, search IME, and retained catalog state behind the detail overlay.

## Private release inputs

The protected `production` GitHub environment must provide:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The Worker environments in the iOS/backend repository require `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID`, and a rotated `TMDB_BEARER_TOKEN`.

Google Auto Backup includes only `smartmovie_library.db`; HTTP cache and the installation UUID are deliberately excluded. SmartMovie has no account, analytics, ads, IAP, or real-time cross-device synchronization.

## Release checklist

1. Revoke the historical TMDb credential and add a newly issued `TMDB_BEARER_TOKEN` to the protected staging and production environments.
2. Configure the `staging-catalog.smartmovie.app` and `catalog.smartmovie.app` Cloudflare custom domains, then run the Worker workflow with staging smoke tests for all six locales.
3. Add the four Android signing secrets above and run the protected `Android release AAB` workflow.
4. Confirm Android CI passes its unit, lint, golden, phone emulator, and TV emulator jobs before uploading the AAB to Play Console.
