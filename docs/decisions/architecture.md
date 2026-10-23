# Architecture

## Current state
Confirmed by the user 2026-10-22; the Gradle skeleton (five modules, theme tokens, drawer with placeholders) builds.

Stack: Kotlin 2.3, AGP 9.2, JDK 21 (overrides the JDK 25 toolchain PipePipeExtractor declares), minSdk 24, compileSdk 37, Jetpack Compose for TV
(`androidx.tv:tv-material` 1.1.0), Media3 ExoPlayer 1.11.1 (spike passed, see `player.md`), Room, DataStore, Coil, OkHttp, coroutines + StateFlow, manual DI.

Reference roles. PipePipe gives the data and playback layers: the extractor (pure Java), `DownloaderImpl` (OkHttp,
cookies, ReCaptcha), the YouTube playback glue (`player/datasource/Sabr*`, `YoutubeHttpDataSource`, `youtube/*PoToken*`,
DASH manifest creators), SponsorBlock, the Room schema and the subscriptions JSON format. SmartTube gives 10-foot UX
patterns only (sections, long-press menu, remote key map, double-back exit, storyboard seek preview); none of its code.

Extractor delivery: git submodule `extractor/` = PipePipeExtractor pinned to commit `1a09d20e` (version v5.5.0; upstream has no tags), unmodified. Its sources are
compiled by our `extractor-build/{extractor,timeago-parser}` build files on JDK 21 (projects `:extractor`, `:timeago-parser`).

Gradle modules (five; features are packages inside `:app` until build time says otherwise):
- `:app` — single Activity, Compose navigation, manifest (`LEANBACK_LAUNCHER`, touchscreen not required, 320x180 banner),
  `AppContainer` wiring. Feature packages: `home`, `player`, `video`, `search`, `channel`, `library`, `settings`.
- `:core:ui` — TV theme as the token home (`Theme.kt`, `TubepipeColors.kt`, `Type.kt`, `TubepipeSpacing.kt`, `Focus.kt`), shared
  composables (`VideoCard`, `MediaRow`, `NavDrawer`, `SettingRow`), the design gate test.
- `:core:service` — wraps the extractor: `Downloader` on OkHttp, `NewPipe.init`, repositories that run the blocking
  extractor on `Dispatchers.IO`, mapping to UI models. Only it and `:core:player` (the playback glue needs `StreamInfo`) import `org.schabi.*`.
- `:core:database` — Room: subscriptions, stream history + resume position, local playlists, search history. Entity
  shapes follow PipePipe so its export JSON imports unchanged.
- `:core:player` — Media3 player, the PipePipe YouTube data sources ported to Media3, quality resolver, play queue, SponsorBlock skipping, media session for the TV remote.

Speed budget (the user's "slick and quick like PipePipe"), measured on a 2 GB Google TV box, enforced before a release:
- Cold start to an interactive Home under 1.5 s. No network on the critical path: Home renders from the Room and disk
  cache first and refreshes in the background. Baseline and startup profiles are generated from the D-pad walk.
- Focus moves within one frame: scale and border via `graphicsLayer`, no relayout on focus, rows are lazy with keys.
- Thumbnails via Coil sized to the card, memory and disk cached, 150 ms crossfade at most, decoded off the main thread.
- Time to play: no prefetch; Centre fetches details, then the stream (~4 s on the Mi Box). The user accepts a few seconds.
- Extractor and Room calls always on `Dispatchers.IO`; StrictMode in debug; one Macrobenchmark for startup and one for
  row scroll jank in `:benchmark`. R8 full mode and resource shrinking in release; no ffmpeg module.

Layering, one direction only: Compose screen -> ViewModel (StateFlow, unidirectional) -> repository -> extractor, Room,
DataStore. ViewModels never touch Android views or the extractor directly.

Product structure (decided 2026-10-22 with the user). YouTube only in v1; the extractor's other services stay unwired.
Drawer, 6 of the 7 allowed slots: Home, Trending, Subscriptions, Search, Library, Settings; the seventh is one pinned
saved search. Trending is a tab strip over a grid: Music, Movies and shows, Gaming. Home is sections the user
can reorder or hide in Settings: Continue watching (history with resume), Because you watched X (related items of the
three most recent seeds), Mix for X (YouTube mix playlists), New from subscriptions (feed extractor), Trending music,
Trending movies and shows. Library holds History, Playlists, Liked (a built-in local playlist), Saved searches; a saved
search can be pinned to the drawer. Channel page: header (avatar, name, subscriber count, Subscribe) over a horizontal
tab strip of the tabs the channel has (Videos, Shorts, Live, Playlists, Podcasts, Courses) and a 4-column grid; Up from
the grid returns to the strip. Player: controls overlay, Down reveals a Related row. D-pad: Centre = play/pause,
left/right = seek, Back = up one level, long-press Centre = context menu (channel, add to playlist, like, play next),
double Back on Home = exit. No Live tab.

Kiosks: PipePipeExtractor ships only "Recommended Lives". Upstream NewPipeExtractor (`~/repos/newpipe-extractor`,
commit 405f549, shipped in NewPipe 0.29.1, verified on the user's phone) adds Trending music and Trending movies and
shows from YouTube Charts (`charts.youtube.com` browse, `FEmusic_analytics_charts_home`, charts `TRENDING_VIDEOS` and
`TRENDING_MOVIES`), Trending gaming (channel `UCOpNcN46UbXVtpKMrmU4Abg`) and Trending podcasts (skipped, see below).
The three kept extractors (spike: about 790 lines with two base classes) are ported into `:core:service`
as subclasses of PipePipeExtractor's `KioskExtractor`, with `COURSES` added to our channel tab list the same way.

Tooling: ktlint for format and lint, Android Lint with `HardcodedText` at error, JUnit 4 + coroutines-test + Turbine,
Roborazzi for composable screenshots, the Android TV 1080p AVD for instrumented tests and D-pad walks.

## Open items
- Drawer: expand to labels on focus (256dp), focus = red border, selected = wine; now the tv-material default pill.
- Content country: a Settings choice, default the device locale's country; Music tab hidden where Charts has no data (BD).
- Play-on-select confirmed: Centre on a card starts playback; info and Related live under the player.

## Gotchas
- The extractor declares a JDK 25 toolchain and `includeBuild` cannot override it, so our build files compile it on 21.
- `NewPipe.init` calls `trustEveryone()`, disabling TLS checks for all `HttpsURLConnection`s; `:core:service` must save
  and restore the default socket factory and hostname verifier around it. OkHttp and WebView are unaffected.
- Extractor bump: copy dependency changes from `extractor/extractor/build.gradle` into `extractor-build/` and the catalog.
- The extractor is blocking Java with OkHttp; every call goes through `withContext(Dispatchers.IO)`.
- Compose for TV uses `androidx.tv.material3.MaterialTheme`; never mix the phone `material3` theme into the same module.
- PipePipeClient has `LEANBACK_LAUNCHER` but a phone UI; it is a data-layer reference, not a UI one.

## Tried / rejected
- Leanback (androidx.leanback): maintenance mode; SmartTube had to fork it to get modern focus behaviour. Dead.
- Reusing SmartTube MediaServiceCore: YouTube innertube only, no extractor abstraction. Dead.
- Hilt: adds a compiler step for one graph with under twenty bindings; manual `AppContainer` first. Revisit at 40+.
- Trending podcasts kiosk: `FEpodcasts_destination` returns an empty page to logged-out web clients (2026-10-22). Skipped.
