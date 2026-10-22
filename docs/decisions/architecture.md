# Architecture

## Current state
Proposed 2026-10-22 from a survey of `~/repos/pipepipe` and `~/repos/smarttube`; not yet confirmed by the user.

Stack: Kotlin 2.3, AGP 9.2, JDK 25 (PipePipeExtractor's toolchain), minSdk 23, compileSdk 37, Jetpack Compose for TV
(`androidx.tv:tv-material` 1.1.0), Media3 ExoPlayer, Room, DataStore, Coil, OkHttp, coroutines + StateFlow, manual DI.

Reference roles. PipePipe gives the data and playback layers: the extractor (pure Java, 7 services: YouTube, SoundCloud,
MediaCCC, PeerTube, Bandcamp, BiliBili, NicoNico), `DownloaderImpl` (OkHttp, cookies, ReCaptcha), the YouTube
playback glue (`player/datasource/Sabr*`, `YoutubeHttpDataSource`, `youtube/*PoToken*`, DASH manifest creators in the
extractor), SponsorBlock, the Room schema and the subscriptions JSON import/export. SmartTube gives the 10-foot UX only:
section layout, long-press context menu, remote key map, double-back exit, seek preview from storyboards, settings as
list + pane. SmartTube code is not reused: Java 8, forked Leanback 1.0.0, Amazon ExoPlayer 2.10.6, and MediaServiceCore
is a YouTube-only innertube client.

Extractor delivery: git submodule `extractor/` = PipePipeExtractor pinned to tag v5.5.0, consumed with
`includeBuild("extractor")` exactly as PipePipeClient does. JitPack publication is unverified, so no Maven coordinate.

Gradle modules (five; features are packages inside `:app` until build time says otherwise):
- `:app` — single Activity, Compose navigation, manifest (`LEANBACK_LAUNCHER`, touchscreen not required, 320x180 banner),
  `AppContainer` wiring. Feature packages: `home`, `player`, `video`, `search`, `channel`, `library`, `settings`.
- `:core:ui` — TV theme as the token home (`Theme.kt`, `Color.kt`, `Type.kt`, `Spacing.kt`, `Focus.kt`), shared
  composables (`VideoCard`, `MediaRow`, `NavDrawer`, `SettingRow`), the design gate test.
- `:core:service` — wraps the extractor: `Downloader` on OkHttp, `NewPipe.init`, repositories that run the blocking
  extractor on `Dispatchers.IO`, mapping to UI models. Nothing above this module imports `org.schabi.*`.
- `:core:database` — Room: subscriptions, stream history + resume position, local playlists, search history. Entity
  shapes follow PipePipe so its export JSON imports unchanged.
- `:core:player` — Media3 player, the PipePipe YouTube data sources ported to Media3, quality resolver, play queue,
  SponsorBlock skipping, media session for the TV remote.

Speed budget (the user's "slick and quick like PipePipe"), measured on a 2 GB Google TV box, enforced before a release:
- Cold start to an interactive Home under 1.5 s. No network on the critical path: Home renders from the Room and disk
  cache first and refreshes in the background. Baseline and startup profiles are generated from the D-pad walk.
- Focus moves within one frame: scale and border via `graphicsLayer`, no relayout on focus, rows are lazy with keys.
- Thumbnails via Coil sized to the card, memory and disk cached, 150 ms crossfade at most, decoded off the main thread.
- Play within 2 s of Centre: stream info is prefetched after 300 ms dwell on a focused card, the player is pre-warmed.
- Extractor and Room calls always on `Dispatchers.IO`; StrictMode in debug; one Macrobenchmark for startup and one for
  row scroll jank in `:benchmark`. R8 full mode and resource shrinking in release; no ffmpeg module.

Layering, one direction only: Compose screen -> ViewModel (StateFlow, unidirectional) -> repository -> extractor, Room,
DataStore. ViewModels never touch Android views or the extractor directly.

Screens and D-pad map (see `.design-mockups/`): Home (drawer + rows), Player (controls overlay, seek preview), Video
(poster, actions, related), Search (field, suggestions, results), Channel, Library (Subscriptions, History, Playlists),
Settings (category list + pane). Centre = play/pause, left/right = seek, Back = up one level, long-press centre = context
menu, double Back on Home = exit.

Tooling: ktlint Gradle plugin for format and lint, Android Lint with `HardcodedText` and `ContentDescription` at error,
JUnit 4 + kotlinx-coroutines-test + Turbine for unit tests, Roborazzi for composable screenshots, TV AVD (Android TV
1080p) for instrumented tests and D-pad walks.

## Open items
- User confirms or vetoes the stack; then `settings.gradle.kts`, the five modules and the extractor submodule are created.
- Spike: port `SabrDashMediaSource`, `SabrSegmentDataSource`, `YoutubeHttpDataSource` to Media3. If it exceeds two days,
  fall back to ExoPlayer 2.18.7 and copy the glue verbatim.
- PoToken needs the JS solver asset (`assets/ejs/yt.solver.lib.min.js`) and a JS runtime; decide WebView vs QuickJS.
- Decide the sidebar set; proposal: Home, Subscriptions, Live, Search, Library, Settings plus user channel groups.
- Decide whether non-YouTube services ship in v1 or stay behind a settings toggle.

## Gotchas
- The extractor toolchain is JDK 25; the Android build must run on JDK 25 too (AGP 9.2 handles the bytecode).
- The extractor is blocking Java with OkHttp; every call goes through `withContext(Dispatchers.IO)`.
- Compose for TV uses `androidx.tv.material3.MaterialTheme`; never mix the phone `material3` theme into the same module.
- PipePipeClient has `LEANBACK_LAUNCHER` but a phone UI; it is a data-layer reference, not a UI one.

## Tried / rejected
- Leanback (androidx.leanback): maintenance mode; SmartTube had to fork it to get modern focus behaviour. Dead.
- Reusing SmartTube MediaServiceCore: YouTube innertube only, no extractor abstraction. Dead.
- Hilt: adds a compiler step for one graph with under twenty bindings; manual `AppContainer` first. Revisit at 40+.
