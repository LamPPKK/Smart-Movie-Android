# SmartMovie Compose Multiplatform

This project delivers SmartMovie to the remaining production Compose Multiplatform targets without changing the locked Android build in the repository root.

## Targets

- iPhone and iPad: iOS/iPadOS 14+ through the SwiftUI host in `iosApp`
- macOS: macOS 13+ on Apple silicon
- Windows: Windows 10+ on 64-bit systems
- Linux: Ubuntu 20.04-compatible 64-bit distributions
- Web: WasmGC production bundle plus a JavaScript fallback bundle

Compose UI is not presented as a watchOS, tvOS, or visionOS target. The repository already contains dedicated Android TV, Wear OS, and Android XR experiences.

## Architecture

`composeApp` owns the shared catalog contracts, Ktor Worker client, retry/cancellation behavior, anonymous installation ID, persisted Favorite/Watchlist library, six-locale copy, UDF controller, and adaptive cinematic UI. Platform source sets provide only storage, URL handoff, API base URL, and entry points.

All catalog traffic goes through `https://catalog.smartmovie.app/v1`. Desktop development can override the origin with `SMARTMOVIE_CATALOG_BASE_URL`; web development can use `?api=https://…`. The `?preview=1` switch is reserved for the deterministic local preview server.

## Build

Use JDK 17 or newer. Run commands from this directory:

```bash
./gradlew --dependency-verification=strict :composeApp:desktopTest :composeApp:compileKotlinDesktop
./gradlew :composeApp:compileKotlinIosSimulatorArm64
./gradlew :composeApp:jsBrowserDevelopmentExecutableDistribution
./gradlew :composeApp:wasmJsBrowserDistribution
```

Launch desktop with `./gradlew :composeApp:run`. Launch web during development with `./gradlew :composeApp:wasmJsBrowserDevelopmentRun`. On a Mac with full Xcode installed, open `iosApp/SmartMovie.xcodeproj`; its pre-build step embeds and signs the shared framework for the selected simulator or device.

Desktop package formats are configured in `composeApp/build.gradle.kts`. `./gradlew :composeApp:createDistributable` creates a portable image for the current OS, and `./gradlew :composeApp:packageDistributionForCurrentOS` creates its configured installers when the OS packaging prerequisites are installed.

## Local deterministic preview

First build the Wasm distribution, then run:

```bash
python3 tools/preview_server.py
```

Open `http://127.0.0.1:8099/?preview=1`. The server supplies deterministic `/v1` responses without a TMDb token and is intended only for UI QA.

## Release boundaries

- Web hosting must serve `.wasm` with `application/wasm` and the production Worker must allow the web origin through CORS.
- App Store archives require an Apple team, distribution certificate, and provisioning profile.
- Notarized macOS and signed Windows installers require protected signing identities.
- The library is local per platform; there is no SmartMovie account or real-time sync.
