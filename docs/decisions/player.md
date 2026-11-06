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
- Made-for-kids videos (Masha, Grizzy, Booba): YouTube refuses VisionOS; the vendored extractor (patch 0004) then asks
  the embedded player (`WEB_EMBEDDED_PLAYER`, as yt-dlp's `web_embedded`) in the same pass and gets 1080p HTTPS
  formats, played as DASH/progressive, no SABR, no PO token. Its last resort is still the Android reel's muxed 360p,
  and `LowQualityOffer` then retries mweb as before.
- Start path: `PlaybackRuntime.initialize` + decoder prewarm run 1 s after the Activity is up; `StreamInfoCache`
  (core/service) shares one extraction per video between detail page, Home seeds and player, so the player extracts
  nothing when the detail page already did. Box, 2026-11-06: press-to-playing 6.4 s cold (fresh extraction), ~3 s on a
  cache hit, was 13.6 s warm / 17 s+ cold. Remaining cost is the extraction itself (0.9 s network + 1.7 s "related"
  parse on the box CPU) and the 2.5 s start buffer.
- Playback speed below 1x "stutters" by design of the display: the box outputs 50 Hz and YouTube cartoons are 25 fps,
  so 1x is exactly 2 refreshes per frame while 0.9x (22.5 fps) cannot divide evenly and holds every ~4th frame one
  refresh longer. Only 0.5x and 1x are even on 50 Hz for 25 fps content; the box offers no 45 Hz mode. Not a player bug.
- SABR quality menu: the player has one video track, so `qualityOptions` lists the SABR format heights and
  `setMaxQuality` rebuilds the SABR source at that cap from the current position (box: kids video offers 1080-144).
- Side-loaded captions are parsed during extraction (`SubtitleExtractor` in a `ProgressiveMediaSource`); Media3 1.11's
  text renderer rejects raw TTML/VTT samples, which crashed SABR playback when a caption was picked.

## Open items
- Untested paths: multi-language HLS original-audio switch, DASH/OTF/progressive builders; add device tests.
- Add instrumented tests for seek back 30 s, forward seek, forced quality switch, and time to first frame (2 s budget).
- Restore what the spike stubbed: segment disk cache (Media3 `SimpleCache`), audio pick by language (original first,
  DataStore prefs), SABR backoff UI, startup trace, full `DownloaderImpl` (cookies, reCaptcha, restricted mode).
- One Home seed extraction can still time out at launch while the WebView warms up (box, 2026-11-06); it is fetched
  again at the first play. Consider starting the prewarm after the Recommended row has loaded.

## Gotchas
- Media3's `DefaultLoadControl` starts at 1,000 ms buffer and resumes at 2,000 ms (ExoPlayer 2.18.7: 2,500 / 5,000);
  set the durations explicitly.
- Default back buffer is 0 and the spike has no cache, so a backward seek re-requests SABR sequences from the server.
- The SABR period wrapper must forward `setUsesStreamPrerollFlags()` and `setEndPositionUs(long)` to its child.
- The glue uses `@UnstableApi` types; opt in for the module or Android Lint fails with `UnsafeOptInUsageError`.
- Set the track selector viewport to 1920x1080 explicitly: sized from the box display, HLS stayed at 480p.
- The `SabrDashMediaSource` constructor does network I/O; build it on `Dispatchers.IO`.
- SABR's first response carries no media, only a context update and a 4800 ms backoff; the server re-issues the
  remaining wait if asked earlier, so every SABR start pays 4.8 s. Kids videos avoid SABR now; nothing else uses it.
- `screencap` shows black over the video (sideband overlay) and SurfaceFlinger has no frame times for it; measure
  start with `dumpsys media_session` state changes and the extractor's `YT_PERF` log lines.

## Tried / rejected
- tv_simply, web: also 360p-only for kids videos; tv_samsung, tv_downgraded: bot check. Only mweb gives 1080p.
- ExoPlayer 2.18.7 fallback: not needed, the Media3 port is three files; 2.18.7 is end of life with no media session future.
- Skipping the SABR first-response backoff (2026-11-06): the immediate resend only gets the remaining wait back.
