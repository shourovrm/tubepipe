# Service

## Current state
`:core:service` is the app's door to YouTube: `ContentRepository` (interface, faked in app tests) implemented by
`YoutubeContentRepository`; every extractor call runs on `Dispatchers.IO`. Models (`VideoItem`, `VideoDetails`,
`ChannelPage`, `ContentPage`, opaque `PageCursor`) carry no `org.schabi` type. `ExtractorInitializer` starts the extractor
with `DownloaderImpl` (OkHttp, trimmed from PipePipeClient) and restores the TLS defaults `NewPipe.init` disables.
Trending kiosks (music, movies and shows, gaming) are ported from NewPipeExtractor into `kiosk/`; podcasts skipped.
Live smoke tests: `./gradlew :core:service:testDebugUnitTest -Ptubepipe.live=true` (all 8 passed 2026-10-22).

## Open items
- Channel tabs other than Videos (Shorts, Live, Playlists, Podcasts, Courses) are not exposed yet.
- Search returns videos only; channels and playlists in results are dropped.

## Gotchas
- `searchQHFactory.fromQuery(query)` throws "we have a problem here" without a content filter; pass the "videos" filter.
- Pass `null`, not an empty list, as the sort filter: an empty list throws `UnsupportedEncodingException: UTF_8`.
- Music trending uses YouTube Charts; its launched-country set excludes BD, so `TrendingCatalog` hides the tab there.
- A Mix is `watch?v=<id>&list=RD<id>` opened through `PlaylistInfo`.

## Tried / rejected
- Trending podcasts kiosk: empty page for logged-out clients (see architecture.md).
