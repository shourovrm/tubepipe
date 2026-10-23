# Player

## Current state
Media3 1.11.1 is the player. The 2026-10-22 spike (`~/spikes/tubepipe-media3`, local only, not in git) ported PipePipe's
SABR glue and played one 1080p avc1 stream for 60 s on the Mi Box S with 0 rebuffers and 0 dropped frames, twice.
- Media3 edits are confined to `SabrDashMediaSource`, `SabrSegmentDataSource` and `SabrLoadErrorHandlingPolicy`.
  The only API change is `MediaPeriod.continueLoading(long)` becoming `continueLoading(LoadingInfo)`; the rest is imports.
- Every other ported file has no player dependency and is copied as is: the `Sabr*` helpers, `youtube/*`
  (PoToken provider, attestation), `SharedWebViewRuntime`, `WebViewJavaScriptDecoder`, `assets/ejs/*`, `assets/sabr_po_token.js`.
- `PlaybackPreferences` (max height, codec, audio language, captions, speed) and live `setMaxQuality`/`setCaptions`/`setSpeed`;
  subtitles side-loaded from the extractor for every delivery. SABR/progressive apply a new quality cap at the next load.
- PoToken runs in the system WebView (138 on the box); QuickJS was not needed for the spike.
- Default client `visionos`: for normal videos the extractor gives progressive formats plus the HLS master playlist
  (`hlsUrl`), played adaptively; `mweb` (SABR) is the fallback and a Settings choice. Both 1080p, 0 rebuffers on the box.
- Made-for-kids videos (Masha, Grizzy): YouTube refuses VisionOS and the extractor silently keeps one muxed 360p
  (itag 18). `LowQualityOffer` spots a 360p-only result and `load` retries mweb, keeping the 360p if mweb fails.
- SABR quality menu: the player has one video track, so `qualityOptions` lists the SABR format heights and
  `setMaxQuality` rebuilds the SABR source at that cap from the current position (box: kids video offers 1080-144).
- Side-loaded captions are parsed during extraction (`SubtitleExtractor` in a `ProgressiveMediaSource`); Media3 1.11's
  text renderer rejects raw TTML/VTT samples, which crashed SABR playback when a caption was picked.

## Open items
- Untested paths: multi-language HLS original-audio switch, DASH/OTF/progressive builders; add device tests.
- Add instrumented tests for seek back 30 s, forward seek, forced quality switch, and time to first frame (2 s budget).
- Restore what the spike stubbed: segment disk cache (Media3 `SimpleCache`), audio pick by language (original first,
  DataStore prefs), SABR backoff UI, startup trace, full `DownloaderImpl` (cookies, reCaptcha, restricted mode).

## Gotchas
- Media3's `DefaultLoadControl` starts at 1,000 ms buffer and resumes at 2,000 ms (ExoPlayer 2.18.7: 2,500 / 5,000);
  set the durations explicitly.
- Default back buffer is 0 and the spike has no cache, so a backward seek re-requests SABR sequences from the server.
- The SABR period wrapper must forward `setUsesStreamPrerollFlags()` and `setEndPositionUs(long)` to its child.
- The glue uses `@UnstableApi` types; opt in for the module or Android Lint fails with `UnsafeOptInUsageError`.
- Set the track selector viewport to 1920x1080 explicitly: sized from the box display, HLS stayed at 480p.
- The `SabrDashMediaSource` constructor does network I/O; build it on `Dispatchers.IO`.

## Tried / rejected
- tv_simply, web: also 360p-only for kids videos; tv_samsung, tv_downgraded: bot check. Only mweb gives 1080p.
- ExoPlayer 2.18.7 fallback: not needed, the Media3 port is three files; 2.18.7 is end of life with no media session future.
