# SmartMovie Compose Multiplatform

This project delivers SmartMovie to the remaining production Compose Multiplatform targets without changing the locked Android build in the repository root.

## Targets

- macOS: macOS 13+ on Apple silicon
- Windows: Windows 10+ on 64-bit systems
- Linux: Ubuntu 20.04-compatible 64-bit distributions
- Web: WasmGC production bundle plus a JavaScript fallback bundle

The separate `SmartMovie` repository owns the native SwiftUI clients for iPhone, iPad, Mac, Apple TV, Apple Watch, and Apple Vision Pro. Compose Multiplatform intentionally does not produce an Apple mobile framework or host application.

## Architecture

`composeApp` owns the shared `/v2` catalog/account contracts, Ktor Worker client, entity and External ID search, navigable Credit Detail flows, paginated Movie/TV account recommendations, retry/cancellation behavior, anonymous installation ID, local-first Favorite/Watchlist library, account-scoped durable mutation outbox, adult PIN state, six-locale copy, UDF controller, and adaptive cinematic UI. Platform source sets provide storage, secure session behavior, URL handoff, API base URL, and entry points.

Desktop conformance tests decode the repository-level `catalog-contract` fixtures. The vendored snapshot is shared with native Android and is updated by an automated cross-repository pull request whenever the Worker contract changes.

All catalog/account traffic goes through `https://catalog.smartmovie.app/v2`; `/v1` remains for deterministic legacy preview and 2.0 compatibility. Desktop development can override the origin with `SMARTMOVIE_CATALOG_BASE_URL`; web development can use `?api=https://…`. The `?preview=1` switch is reserved for the deterministic local preview server.

## Build

Use JDK 21 or newer. Run commands from this directory:

```bash
./gradlew --dependency-verification=strict :composeApp:desktopTest :composeApp:compileKotlinDesktop
./gradlew :composeApp:jsBrowserDevelopmentExecutableDistribution
./gradlew :composeApp:wasmJsBrowserDistribution
```

Launch desktop with `./gradlew :composeApp:run`. Launch web during development with `./gradlew :composeApp:wasmJsBrowserDevelopmentRun`.

Desktop package formats are configured in `composeApp/build.gradle.kts`. `./gradlew :composeApp:createDistributable` creates a portable image for the current OS, and `./gradlew :composeApp:packageDistributionForCurrentOS` creates its configured installers when the OS packaging prerequisites are installed.

## Local deterministic preview

First build the Wasm distribution, then run:

```bash
python3 tools/preview_server.py
```

Open `http://127.0.0.1:8099/?preview=1`. The server supplies deterministic `/v1` responses without a TMDb token and is intended only for UI QA.

## Release boundaries

- Web hosting must serve `.wasm` with `application/wasm`; production must configure CORS, browser callback allowlist, secure cookies, and CSRF.
- Notarized macOS and signed Windows installers require protected signing identities.
- There is no separate SmartMovie identity. Optional TMDb approval synchronizes account content while local caches/outboxes remain platform-specific.
- Desktop JVM, JavaScript, and Wasm are release blockers for the coordinated 3.0 train.
