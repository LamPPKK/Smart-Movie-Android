# SmartMovie platform support

This document defines what “supported” means for every SmartMovie 2.0 target. It is deliberately stricter than a compatibility claim: a platform is listed as supported only when its input model, layout, packaging, and release behavior are represented in the codebase.

## Support matrix

| Platform | Delivery | Experience | Status |
| --- | --- | --- | --- |
| Android phone | Main AAB | Four-tab touch UI and full-screen detail | Supported, API 26+ |
| Tablet and foldable | Main AAB | Navigation rail, adaptive grids, and list-detail pane | Supported |
| ChromeOS | Main AAB | Resizable large-screen UI, keyboard/mouse input, Ctrl/Cmd+1–4 tabs, Ctrl/Cmd+F Search | Supported |
| Android TV | Main AAB | Dedicated 10-foot UI, Leanback launcher, D-pad focus, TV IME | Supported |
| Wear OS | Companion AAB | Remote for the title detail currently open on the paired phone | Supported, non-standalone |
| Android XR | Main AAB | Large-screen app in Home Space with pointer/keyboard input | Compatible; not immersive/spatial |
| Android Auto | None | No car surface is declared | Not eligible under the current product scope |
| iPhone and iPad | Native SwiftUI app in the `SmartMovie` repository | Apple-native catalog and private CloudKit library | Owned and released from the Apple repository |
| macOS | Native desktop application | Resizable keyboard/mouse UI and local library | Supported, macOS 13+, Apple silicon |
| Windows | Native desktop application | Resizable keyboard/mouse UI and local library | Supported, Windows 10+, 64-bit |
| Linux | Native desktop application | Resizable keyboard/mouse UI and local library | Supported, Ubuntu 20.04+ compatible, 64-bit |
| Web | Static Wasm distribution with JavaScript fallback | Responsive browser UI, PWA shell, local library | Beta platform; WasmGC browser required for Wasm build |
| watchOS, tvOS, visionOS | Native SwiftUI apps in the `SmartMovie` repository | Platform-specific Apple experiences | Not built from this repository |

## Phone, tablet, foldable, and ChromeOS

`MainActivity` is resizable and does not lock orientation or aspect ratio. Compose derives layout from the live window width, so resizing a desktop window, unfolding a device, or entering split-screen recomposes the same state instead of restarting into a second layout tree.

At 600 dp and above, bottom navigation becomes a navigation rail and title selection opens a supporting detail pane. Catalog grids use adaptive cell sizing. This same behavior is the ChromeOS desktop experience, augmented by keyboard shortcuts:

- Ctrl/Cmd+1: Home
- Ctrl/Cmd+2: Explore
- Ctrl/Cmd+3 or Ctrl/Cmd+F: Search
- Ctrl/Cmd+4: Library

This follows Android's guidance that ChromeOS window resizing should be handled as a normal configuration change by adaptive layouts: [ChromeOS window management](https://developer.android.com/topic/arc/window-management).

## Android TV

`TvActivity` is a dedicated `LEANBACK_LAUNCHER` entry point. It shares repositories and models with mobile but not the touch layout. TV screens use Compose for TV navigation, visible red focus treatment with restrained scaling, D-pad/back behavior, a TV search field, and retained catalog state while a detail overlay is open.

Leanback and touchscreen are both optional in the main manifest so one main AAB remains installable across phone, tablet, ChromeOS, TV, and compatible XR devices. See [Android TV app requirements](https://developer.android.com/training/tv/get-started/create).

## Wear OS remote

The `wear` module is a non-standalone companion application. It uses application ID `com.lamndt.smartmovie` (or the matching `.debug` suffix), the same version, and the same signing configuration as the main application. Both AABs belong in the same Play listing, as required by [Wear OS app packaging](https://developer.android.com/training/wearables/packaging).

The phone advertises a SmartMovie-specific capability and publishes the currently visible detail context as a persistent Data Item. The watch targets only reachable nodes with that capability and sends transient commands through the Message API. This matches the intended split in the [Wear OS Data Layer](https://developer.android.com/training/wearables/data/overview): synchronized state for the current title and messages for remote-control actions.

The remote mirrors:

- title, media type, year, artwork, and rating;
- whether a YouTube trailer is available;
- Favorite and Watchlist membership.

It can request:

- open the current detail;
- play the selected YouTube trailer on the phone;
- toggle Favorite;
- toggle Watchlist.

Commands are accepted only while the paired phone is connected, SmartMovie is in the foreground, and the watch's `libraryKey` matches the phone's active detail. This prevents a stale watch screen from mutating another title. Toggle responses update the watch immediately, while Room remains the source of truth on the phone.

The watch app has no catalog API client, account, database, or independent browsing mode. Its manifest therefore declares `com.google.android.wearable.standalone=false`.

## Android XR

SmartMovie ships as an Android XR-compatible large-screen app in Home Space. It is resizable, has no unsupported required hardware feature, and supports external pointer and keyboard input. Android classifies optimized large-screen applications as compatible XR apps when they meet those requirements: [Android XR quality guidelines](https://developer.android.com/docs/quality-guidelines/android-xr).

The manifest marks the spatial API feature as optional so the main AAB remains installable on API 26 phones and other non-XR hardware. SmartMovie does not currently link the Developer Preview Jetpack XR SDK and does not claim an immersive Full Space experience. A spatial panel, 3D media environment, or XR-specific interaction model would be a separate product phase: [Jetpack XR SDK](https://developer.android.com/develop/xr/jetpack-xr-sdk).

## Why Android Auto is not declared

SmartMovie is a movie and TV discovery catalog; it does not stream video or audio, navigate, message, control IoT devices, or provide another supported driving app category. Android for Cars accepts defined categories such as audio media, navigation, POI, IoT, weather, messaging/calling, and selected parked experiences. A catalog app must not mislabel itself as one of those categories merely to appear in Android Auto: [Android for Cars app categories](https://developer.android.com/training/cars/apps/library/set-up-project) and [car app distribution](https://developer.android.com/training/cars/distribute).

Accordingly, this release intentionally has no `CarAppService`, automotive descriptor, or Android Auto manifest category. Adding a legitimate car experience would require changing the locked product scope—for example, an eligible audio-media core purpose or a supported parked streaming-video experience—and completing the corresponding car quality review. Until then, “Android Auto support” would be a false capability and a Play policy risk.

## Desktop and web

The `multiplatform` build contains one shared Compose application for Home, Explore, Search, Library, and Detail. It uses immutable `StateFlow` state and a Ktor 3 client that calls only the SmartMovie Worker `/v1` contract. Installation IDs and Favorite/Watchlist snapshots remain in the platform store except for the anonymous installation ID sent as the Worker client header.

Desktop distributions are configured for DMG/PKG, MSI/EXE, and DEB/RPM, while CI also produces portable application images on each desktop OS. Web builds include both Wasm and JavaScript outputs, a manifest, and a service worker; the production Worker must allow the deployed web origin through CORS.

The selected Compose Multiplatform toolchain targets desktop JVM and Web/Wasm. The minimums are macOS 13 on Apple silicon, Windows 10, Ubuntu 20.04-compatible Linux, and 64-bit desktop systems. Web/Wasm remains Beta while desktop is Stable. See [Compose Multiplatform platform support](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html) and [Kotlin Multiplatform stability levels](https://kotlinlang.org/docs/multiplatform/supported-platforms.html).

Apple mobile and spatial platforms are intentionally excluded from the Compose build. Android TV, Wear OS remote control, and Android XR Home Space remain the purpose-built implementations documented above.

## Release artifacts

The protected release workflow creates:

1. `app-release.aab` for phone, tablet, foldable, ChromeOS, Android TV, and Android XR Home Space.
2. `wear-release.aab` for the paired Wear OS remote.
3. A Web/Wasm static distribution and portable macOS, Windows, and Linux application images from the Compose Multiplatform workflow.

Both Android AABs must be signed by the same release key. Debug variants likewise share `com.lamndt.smartmovie.debug`, allowing Data Layer communication during development.
