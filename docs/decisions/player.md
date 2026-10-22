# Player

## Current state
Media3 1.11.1 is the player. The 2026-10-22 spike (`~/spikes/tubepipe-media3`, local only, not in git) ported PipePipe's
SABR glue and played one 1080p avc1 stream for 60 s on the Mi Box S with 0 rebuffers and 0 dropped frames, twice.
- Media3 edits are confined to `SabrDashMediaSource`, `SabrSegmentDataSource` and `SabrLoadErrorHandlingPolicy`.
  The only API change is `MediaPeriod.continueLoading(long)` becoming `continueLoading(LoadingInfo)`; the rest is imports.
- Every other ported file has no player dependency and is copied as is: the `Sabr*` helpers, `youtube/*`
  (PoToken provider, attestation), `SharedWebViewRuntime`, `WebViewJavaScriptDecoder`, `assets/ejs/*`, `assets/sabr_po_token.js`.
- PoToken runs in the system WebView (138 on the box); QuickJS was not needed for the spike.
- Default client is `visionos` (HLS, as PipePipe logged out); `mweb` (SABR) is the fallback and a Settings choice.

## Open items
- Port `YoutubeHttpDataSource` to Media3 for HLS and URL streams; add `media3-exoplayer-hls`. Untested on Media3 so far.
- Add instrumented tests for seek back 30 s, forward seek, forced quality switch, and time to first frame (2 s budget).
- Restore what the spike stubbed: segment disk cache (Media3 `SimpleCache`), audio pick by language (original first,
  DataStore prefs), SABR backoff UI, startup trace, full `DownloaderImpl` (cookies, reCaptcha, restricted mode).

## Gotchas
- Media3's `DefaultLoadControl` starts at 1,000 ms buffer and resumes at 2,000 ms (ExoPlayer 2.18.7: 2,500 / 5,000);
  set the durations explicitly.
- Default back buffer is 0 and the spike has no cache, so a backward seek re-requests SABR sequences from the server.
- The SABR period wrapper must forward `setUsesStreamPrerollFlags()` and `setEndPositionUs(long)` to its child.
- The glue uses `@UnstableApi` types; opt in for the module or Android Lint fails with `UnsafeOptInUsageError`.
- Gate WebView debugging and console logs on `BuildConfig.DEBUG`; the spike hard-codes it on.
- The `SabrDashMediaSource` constructor does network I/O; build it on `Dispatchers.IO`.

## Tried / rejected
- ExoPlayer 2.18.7 fallback: not needed, the Media3 port is three files; 2.18.7 is end of life with no media session future.
