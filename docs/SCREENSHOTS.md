# SmartMovie screen gallery

This gallery documents the SmartMovie 3.0 interface across the shared Compose Multiplatform flow and native Android form factors. Captures use deterministic local catalog data so they remain stable and never expose an account session, adult title, personal list, or production credential.

## Core flow — Compose Multiplatform

The same shared Kotlin UI and data layer powers macOS, Windows, Linux, and responsive Web/Wasm. Compact browser captures below show the primary user journey from discovery to a saved title.

<table>
  <tr>
    <td width="33%" align="center"><strong>Home</strong><br><sub>Movie/TV switch, hero, and trending shelf</sub></td>
    <td width="33%" align="center"><strong>Explore</strong><br><sub>Media, rating, and year filters</sub></td>
    <td width="33%" align="center"><strong>Search</strong><br><sub>Debounced title search and scope selection</sub></td>
  </tr>
  <tr>
    <td><img src="images/screenshots/multiplatform-home-phone.png" alt="SmartMovie Home on a compact screen" width="390"></td>
    <td><img src="images/screenshots/multiplatform-explore-phone.png" alt="SmartMovie Explore on a compact screen" width="390"></td>
    <td><img src="images/screenshots/multiplatform-search-phone.png" alt="SmartMovie Search on a compact screen" width="390"></td>
  </tr>
  <tr>
    <td width="33%" align="center"><strong>Detail</strong><br><sub>Trailer, Favorite, Watchlist, story, and cast</sub></td>
    <td width="33%" align="center"><strong>Library</strong><br><sub>Local Favorites and Watchlist</sub></td>
    <td width="33%" align="center"><strong>Adaptive desktop</strong><br><sub>Navigation rail and expanded content grid</sub></td>
  </tr>
  <tr>
    <td><img src="images/screenshots/multiplatform-detail-phone.png" alt="SmartMovie title detail on a compact screen" width="390"></td>
    <td><img src="images/screenshots/multiplatform-library-phone.png" alt="SmartMovie Library on a compact screen" width="390"></td>
    <td><img src="images/screenshots/multiplatform-home-desktop.png" alt="SmartMovie Home on desktop" width="640"></td>
  </tr>
</table>

## Native Android form factors

These images are the committed screenshot-test baselines. They verify the cinematic palette, typography, spacing, responsive navigation, TV presentation, and Wear OS remote without relying on a live catalog service.

### Android phone

Compact phones use bottom navigation for Home, Explore, Search, and Library.

<p align="center">
  <img src="../app/src/screenshotTestDebug/reference/com/lamndt/smartmovie/CinematicGoldenPreviewsKt/PhoneHomeGolden_phone_compact_8dbe636a_0.png" alt="SmartMovie native Android phone Home golden screenshot" width="390">
</p>

### Tablet, foldable, and ChromeOS

Expanded windows move navigation to a rail and increase shelf density. The same adaptive composition covers tablets, unfolded devices, desktop-sized ChromeOS windows, and Android XR Home Space panels.

<p align="center">
  <img src="../app/src/screenshotTestDebug/reference/com/lamndt/smartmovie/CinematicGoldenPreviewsKt/TabletHomeGolden_tablet_expanded_97f2797e_0.png" alt="SmartMovie native Android tablet Home golden screenshot" width="960">
</p>

### Android TV

The 10-foot layout has dedicated TV navigation, high-visibility focus treatment, D-pad traversal, and retained focus when returning from details.

<p align="center">
  <img src="../app/src/screenshotTestDebug/reference/com/lamndt/smartmovie/CinematicGoldenPreviewsKt/TvHomeGolden_tv_1080p_b8c175df_0.png" alt="SmartMovie Android TV Home golden screenshot" width="960">
</p>

### Wear OS remote

The watch companion mirrors the safe title or exact episode open on the paired phone. A title exposes detail, trailer, Favorite, and Watchlist actions; an episode exposes only an exact series/season/episode handoff.

<p align="center">
  <img src="../wear/src/screenshotTestDebug/reference/com/lamndt/smartmovie/wear/WearRemoteGoldenPreviewKt/WearRemoteGolden_wear_round_remote_a6040ff4_0.png" alt="SmartMovie Wear OS remote golden screenshot" width="320">
</p>

## Capture notes

- Compose Multiplatform captures were generated at `390 × 844` and `1280 × 720` from deterministic contract-compatible preview data.
- Native images are Roborazzi golden baselines used by Android CI for compact phone, expanded tablet, 1080p TV, and round Wear OS coverage.
- Artwork placeholders are intentional in deterministic previews; production builds load configured poster and backdrop URLs from the SmartMovie Worker.
- Android Auto is intentionally excluded because SmartMovie is a video catalog and does not declare an Android for Cars app category.
