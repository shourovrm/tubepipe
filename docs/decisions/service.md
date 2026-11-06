# Service

## Current state
`:core:service` is the app's door to YouTube: `ContentRepository` (interface, faked in app tests) implemented by
`YoutubeContentRepository`; every extractor call runs on `Dispatchers.IO`. Models (`VideoItem`, `VideoDetails`,
`ChannelPage`, `ContentPage`, opaque `PageCursor`) carry no `org.schabi` type. `ExtractorInitializer` starts the extractor
with `DownloaderImpl` (OkHttp, trimmed from PipePipeClient) and restores the TLS defaults `NewPipe.init` disables.
Trending kiosks (music, movies and shows, gaming) are ported from NewPipeExtractor into `kiosk/`; podcasts skipped.
News is `YoutubeNewsFeed`: a desktop `browse` of the News topic channel `UCYfdidRxbB8Qhf0Nx7ioOYw` (US 188, BD 143 items). Where it has no videos (CN, KP, IR, AF, NP, MM) the feed is read again for US.
Live smoke tests: `./gradlew :core:service:testDebugUnitTest -Ptubepipe.live=true` (all 8 passed 2026-10-22).
Extractor: submodule `third_party/PipePipeExtractor` = `shourovrm/pipe-extractor-patched` branch `patched` (upstream `1a09d20e`
+ our patch commits, the last one = embedded player for kids videos); the phone app pins the same commit. See `third_party/README.md`.
`StreamInfoCache`: one `StreamInfo` per video URL and client for 30 min, fetched on its own scope and shared by
concurrent callers, so a Home reload (every saved playback position re-emits history) cannot cancel and repeat it.

## Open items
- Channel tabs other than Videos (Shorts, Live, Playlists, Podcasts, Courses) are not exposed yet.
- Search returns videos only; channels and playlists in results are dropped.

## Gotchas
- `searchQHFactory.fromQuery(query)` throws "we have a problem here" without a content filter; pass the "videos" filter.
- Pass `null`, not an empty list, as the sort filter: an empty list throws `UnsupportedEncodingException: UTF_8`.
- Music trending uses YouTube Charts; its launched-country set excludes BD, so `TrendingCatalog` hides the tab there.
- A Mix is `watch?v=<id>&list=RD<id>` opened through `PlaylistInfo`.
- Search gives Shorts a length of 0 (YouTube sends none); the card shows a "Shorts" badge for them instead.
- Five concurrent full extractions starve the box ("YouTube requests timed out"); Home fetches seeds two at a time.
- The embedded player answers only when `thirdParty.embedUrl` names the page the embed HTML was fetched with as
  Referer and `encryptedHostFlags` from that page is echoed; the flags are bound to that visitor, not to the video.

## Tried / rejected
- Trending podcasts kiosk: empty page for logged-out clients (see architecture.md).
