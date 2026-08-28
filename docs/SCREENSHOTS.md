# Smart Movie Android screen gallery

This development gallery combines Web/Wasm preview captures and native Android component goldens. It is not a set of store-release or live-TMDb screenshots. Captures never contain a personal TMDb session, adult title, private list, PIN or production credential. Abstract artwork is generated demo imagery from 2026-08-28, not the poster or portrait for the displayed title/person.

## Compose Multiplatform core flow

The same Kotlin UI/data layer powers native desktop packages for macOS, Windows and Linux plus JavaScript and Wasm browser distributions. Compact captures document every primary destination and a typed `/v2` title detail.

<table>
  <tr>
    <td width="33%" align="center"><strong>Home</strong><br><sub>Movie/TV switch, hero and trending</sub></td>
    <td width="33%" align="center"><strong>Explore</strong><br><sub>Advanced catalog filters</sub></td>
    <td width="33%" align="center"><strong>Search</strong><br><sub>Catalog/external ID and entity scopes</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="images/screenshots/multiplatform-home-phone.png" alt="Smart Movie Compose Multiplatform Home on a compact screen" width="390"></td>
    <td align="center"><img src="images/screenshots/multiplatform-explore-phone.png" alt="Smart Movie Compose Multiplatform Explore on a compact screen" width="390"></td>
    <td align="center"><img src="images/screenshots/multiplatform-search-phone.png" alt="Smart Movie Compose Multiplatform Search on a compact screen" width="390"></td>
  </tr>
  <tr>
    <td width="33%" align="center"><strong>Detail</strong><br><sub>Typed metadata, trailer and library actions</sub></td>
    <td width="33%" align="center"><strong>Library</strong><br><sub>Favorite and Watchlist local state</sub></td>
    <td width="33%" align="center"><strong>Profile</strong><br><sub>TMDb auth, region and adult-content PIN</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="images/screenshots/multiplatform-detail-phone.png" alt="Smart Movie Compose Multiplatform title detail" width="390"></td>
    <td align="center"><img src="images/screenshots/multiplatform-library-phone.png" alt="Smart Movie Compose Multiplatform Library" width="390"></td>
    <td align="center"><img src="images/screenshots/multiplatform-profile-phone.png" alt="Smart Movie Compose Multiplatform Profile" width="390"></td>
  </tr>
</table>

### Expanded desktop and web

At larger widths, navigation moves to a rail and content density increases. This same responsive composition is shipped by desktop JVM, JS and Wasm targets.

<p align="center">
  <img src="images/screenshots/multiplatform-home-desktop.png" alt="Smart Movie Compose Multiplatform expanded Home" width="960">
</p>

## Native Android form factors

These committed Roborazzi baselines exercise the cinematic palette, typography, spacing and responsive compositions without a live catalog dependency. They are component captures, not full app/device captures; their null image paths intentionally exercise placeholders and cannot establish whether native network images load.

### Android phone

The compact golden covers the native Home hero and horizontal catalog shelves used by Android phones.

<p align="center">
  <img src="../app/src/screenshotTestDebug/reference/com/lamndt/smartmovie/CinematicGoldenPreviewsKt/PhoneHomeGolden_phone_compact_8dbe636a_0.png" alt="Smart Movie Android native phone Home golden" width="390">
</p>

### Tablet, foldable, ChromeOS and Android XR Home Space

Expanded windows increase shelf density and use adaptive navigation. The same 2D composition covers tablets, unfolded devices, resizable ChromeOS windows and XR Home Space panels.

<p align="center">
  <img src="../app/src/screenshotTestDebug/reference/com/lamndt/smartmovie/CinematicGoldenPreviewsKt/TabletHomeGolden_tablet_expanded_97f2797e_0.png" alt="Smart Movie Android expanded tablet Home golden" width="960">
</p>

### Android TV

The dedicated 10-foot composition provides high-visibility focus treatment, D-pad traversal and retained focus when returning from details.

<p align="center">
  <img src="../app/src/screenshotTestDebug/reference/com/lamndt/smartmovie/CinematicGoldenPreviewsKt/TvHomeGolden_tv_1080p_b8c175df_0.png" alt="Smart Movie Android TV Home golden" width="960">
</p>

### Wear OS companion

Wear OS mirrors only the safe title or exact episode open on the paired phone. A title can hand off to detail/trailer/library actions; an episode hands off to the exact series, season and episode and never exposes adult content.

<p align="center">
  <img src="../wear/src/screenshotTestDebug/reference/com/lamndt/smartmovie/wear/WearRemoteGoldenPreviewKt/WearRemoteGolden_wear_round_remote_a6040ff4_0.png" alt="Smart Movie Wear OS remote golden" width="320">
</p>

## Capture provenance

| Surface | Capture | Source |
| --- | --- | --- |
| Android phone | Native Home | Roborazzi compact golden |
| Tablet/foldable/ChromeOS/XR | Native expanded Home | Roborazzi expanded golden |
| Android TV | Native 1080p Home | Roborazzi TV golden |
| Wear OS | Round remote | Roborazzi Wear golden |
| Desktop-sized web viewport | Expanded Home | KMP Web/Wasm build, not a native desktop package capture |
| Responsive Web/Wasm | Home, Explore, Search, Detail, Library, Profile | Local preview at `390 × 844`, captured 2026-08-28 |

Native desktop packages (macOS/Windows/Linux), JS fallback, physical Android/TV/Wear devices and XR need separate release-candidate captures. A shared composition does not replace platform-specific evidence.

Production clients obtain image configuration/paths from the Worker and load image bytes from the configured CDN. The local preview serves generated illustrations from `multiplatform/tools/preview_artwork`; it does not replace missing production images. See the shared [image diagnostics](https://github.com/LamPPKK/Smart-Movie-iOS/blob/main/docs/IMAGE_LOADING.md) for the unresolved Worker DNS blocker. Android Auto is intentionally excluded because Smart Movie is a video catalog and does not declare an Android for Cars category.
