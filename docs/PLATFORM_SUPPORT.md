# SmartMovie platform support

This document defines what “supported” means for every SmartMovie 3.0 target. It is deliberately stricter than a compatibility claim: a platform is supported only when its input model, layout, account/privacy behavior, packaging, and release gate are represented in the codebase.

## Support matrix

| Platform | Delivery | Experience | Status |
| --- | --- | --- | --- |
| Android phone | Main AAB | Five-destination touch UI, entity/account flows, and full-screen details with catalog reviews, same-media-type recommendations, and Movie/TV/Season/Episode media galleries | Supported, API 26+ |
| Tablet and foldable | Main AAB | Navigation rail, adaptive grids, and list-detail pane | Supported |
| ChromeOS | Main AAB | Resizable large-screen UI, keyboard/mouse input, Ctrl/Cmd+1–4 tabs, Ctrl/Cmd+F Search | Supported |
| Android TV | Main AAB | Dedicated 10-foot UI, catalog reviews/recommendations, Movie/TV media galleries, Leanback launcher, D-pad focus, TV IME | Supported |
| Wear OS | Companion AAB | Safe title/episode context and exact-detail handoff to the paired phone | Supported, non-standalone |
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
- Ctrl/Cmd+5: Profile

This follows Android's guidance that ChromeOS window resizing should be handled as a normal configuration change by adaptive layouts: [ChromeOS window management](https://developer.android.com/topic/arc/window-management).

## Android TV

`TvActivity` is a dedicated `LEANBACK_LAUNCHER` entry point. It shares repositories and models with mobile but not the touch layout. TV screens use Compose for TV navigation, visible red focus treatment with restrained scaling, D-pad/back behavior, a TV search field, and retained catalog state while a detail overlay is open.

Leanback and touchscreen are both optional in the main manifest so one main AAB remains installable across phone, tablet, ChromeOS, TV, and compatible XR devices. See [Android TV app requirements](https://developer.android.com/training/tv/get-started/create).

## Wear OS remote

The `wear` module is a non-standalone companion application. It mirrors only the safe title or exact episode currently open on the paired phone; episode handoff preserves series, season, and episode identity, while trailer and Favorite/Watchlist actions remain title-only. It uses application ID `com.lamndt.smartmovie` (or the matching `.debug` suffix), the same version, and the same signing configuration as the main application. Both AABs belong in the same Play listing, as required by [Wear OS app packaging](https://developer.android.com/training/wearables/packaging).

The phone advertises a SmartMovie-specific capability and publishes the currently visible title/episode detail context as a persistent Data Item. The watch targets only reachable nodes with that capability and sends transient commands through the Message API. Context identity is additive and backward compatible: title commands retain `libraryKey`, while episode commands add an exact `contextKey`. This matches the intended split in the [Wear OS Data Layer](https://developer.android.com/training/wearables/data/overview): synchronized state for the current detail and messages for remote-control actions.

The remote mirrors only non-adult context:

- title, media type, year, artwork, and rating;
- whether a YouTube trailer is available;
- Favorite and Watchlist membership.

It can request:

- open the current detail;
- play the selected YouTube trailer on the phone;
- toggle Favorite;
- toggle Watchlist.

Commands are accepted only while the paired phone is connected, SmartMovie is in the foreground, and the watch's exact `contextKey` matches the phone's active detail. A missing `contextKey` falls back to `libraryKey` only for legacy watch requests. This prevents a stale watch screen from opening another episode or mutating another title. Toggle responses update the watch immediately, while Room remains the source of truth on the phone.

The watch app has no catalog API client, account, adult content, database, or independent browsing mode. Its manifest therefore declares `com.google.android.wearable.standalone=false`.

## Android XR

SmartMovie ships as an Android XR-compatible large-screen app in Home Space. It is resizable, has no unsupported required hardware feature, and supports external pointer and keyboard input. Android classifies optimized large-screen applications as compatible XR apps when they meet those requirements: [Android XR quality guidelines](https://developer.android.com/docs/quality-guidelines/android-xr).

The manifest marks the spatial API feature as optional so the main AAB remains installable on API 26 phones and other non-XR hardware. SmartMovie does not currently link the Developer Preview Jetpack XR SDK and does not claim an immersive Full Space experience. A spatial panel, 3D media environment, or XR-specific interaction model would be a separate product phase: [Jetpack XR SDK](https://developer.android.com/develop/xr/jetpack-xr-sdk).

## Why Android Auto is not declared

SmartMovie is a movie and TV discovery catalog; it does not stream video or audio, navigate, message, control IoT devices, or provide another supported driving app category. Android for Cars accepts defined categories such as audio media, navigation, POI, IoT, weather, messaging/calling, and selected parked experiences. A catalog app must not mislabel itself as one of those categories merely to appear in Android Auto: [Android for Cars app categories](https://developer.android.com/training/cars/apps/library/set-up-project) and [car app distribution](https://developer.android.com/training/cars/distribute).

Accordingly, this release intentionally has no `CarAppService`, automotive descriptor, or Android Auto manifest category. Adding a legitimate car experience would require changing the locked product scope—for example, an eligible audio-media core purpose or a supported parked streaming-video experience—and completing the corresponding car quality review. Until then, “Android Auto support” would be a false capability and a Play policy risk.

## Desktop and web

The `multiplatform` build contains one shared Compose application for Home, Explore, Search, Library, Profile, deep entity details, catalog reviews, same-media-type TMDb recommendations, separate similar-title shelves, deduplicated Movie/TV/Season/Episode image and YouTube-video galleries, TMDb authorization, ratings, and custom lists. External ID, Person known-for/credits, Collection parts, Company/Network/Keyword titles, Credit Detail, recommendations and similar-title shelves all carry the local adult gate and fail closed again in the controller before display. It uses immutable `StateFlow`, a Ktor 3 `/v2` client, local PIN state, and account-scoped durable outboxes. Installation identity, library snapshots, and pending mutations remain in Java Preferences or browser `localStorage`; Web session credentials use secure Worker cookies.

Desktop distributions are configured for DMG/PKG, MSI/EXE, and DEB/RPM, while CI also produces portable images on each desktop OS. Web builds include Wasm and JavaScript outputs, a manifest, and a service worker; production must configure CORS, callback allowlist, secure cookies, CSRF, and correct `application/wasm` hosting.

The selected Compose Multiplatform toolchain targets desktop JVM and Web/Wasm. The minimums are macOS 13 on Apple silicon, Windows 10, Ubuntu 20.04-compatible Linux, and 64-bit desktop systems. Web/Wasm remains Beta while desktop is Stable. See [Compose Multiplatform platform support](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html) and [Kotlin Multiplatform stability levels](https://kotlinlang.org/docs/multiplatform/supported-platforms.html).

Apple mobile and spatial platforms are intentionally excluded from the Compose build. Android TV, Wear OS remote control, and Android XR Home Space remain the purpose-built implementations documented above.

## Release artifacts

The protected release workflow creates:

1. `app-release.aab` for phone, tablet, foldable, ChromeOS, Android TV, and Android XR Home Space.
2. `wear-release.aab` for the paired Wear OS remote.
3. A Web/Wasm static distribution and portable macOS, Windows, and Linux application images from the Compose Multiplatform workflow.

Both Android AABs must be signed by the same release key. Debug variants likewise share `com.lamndt.smartmovie.debug`, allowing Data Layer communication during development.

All three desktop portable images plus JavaScript and Wasm distributions are release blockers for the coordinated 3.0 train; they are not optional side artifacts.
