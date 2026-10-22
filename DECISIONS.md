# Decisions index

## Topics
- `docs/decisions/architecture.md` — stack, modules, layering, what is reused from PipePipe and SmartTube.
- `docs/decisions/service.md` — extractor wrapper, repository API, kiosks, extractor quirks.
- `docs/decisions/player.md` — Media3 SABR port: spike result, what to restore, player gotchas.
- `docs/design-system.md` — androidtv skeleton plus the PipePipe-derived direction tokens (two reds, black theme).

## Next
- v0.3.0: no mini player (channel bar only), playlists that play through, mixed search, colour-card settings, parental.
- Device-untested: playlist page and play-through, Library playlists, Parental page, 18+ block, More menu, media keys.
- Time to play is ~5 s on the box (budget 2 s): prefetch on card dwell and pre-warm the WebView runtime.
- Workers: write the whole slice, then compile + own tests once; lint, full suite and TV check run once after merge.

## Gotchas (cross-cutting)
- The Mi Box is 960x540 dp; mockups are drawn at 1920 px, so 1 mockup px = 0.5 dp. Never copy px values as dp.
- Compose TV: Centre KeyUp clicks whatever gained focus on KeyDown; the first Back only clears focus.
- `focusProperties { enter }` applies only to a focus group; add `focusGroup()` after it.
- On the box, 2D focus search often finds nothing between player rows and Down never leaves a text field; wire it.
- `onKeyEvent` must come before `focusable()` in the chain, or the focused node never sees the keys.
- `adb input keyevent` is keyboard-sourced; use `input dpad keyevent` for D-pad tests. `input text` with %s broke box input.
- `~/repos/pipepipe`, `~/repos/smarttube` and `~/repos/newpipe-extractor` are read-only reference checkouts; never edit them.
- Release key: `~/.config/tubepipe/tubepipe-release.jks` via gitignored `keystore.properties`; back it up, updates need it.
- Build on the installed JDK 21; the extractor asks for a JDK 25 toolchain, so override it to 21 and keep toolchain auto-download off.
