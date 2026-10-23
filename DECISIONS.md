# Decisions index

## Topics
- `docs/decisions/architecture.md` — stack, modules, layering, what is reused from PipePipe and SmartTube.
- `docs/decisions/service.md` — extractor wrapper, repository API, kiosks, extractor quirks.
- `docs/decisions/player.md` — Media3 SABR port: spike result, what to restore, player gotchas.
- `docs/decisions/tv-ui.md` — search field, player overlay, mini bar, sidebar behaviours.
- `docs/design-system.md` — androidtv skeleton plus the PipePipe-derived direction tokens (two reds, black theme).

## Next
- v0.2.1 pre-release still ships prefetch; main has it removed and both box focus bugs fixed (box-checked). Next release: 0.2.2.
- Device-untested: Parental page, 18+ block, More menu, media keys. Box-tested 2026-10-22: playlist/channel bars, search, drawer, kids 1080p.
- Time to play on box is ~4 s with no prefetch; the user accepts a few seconds (2026-10-23), so prefetch was removed.
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
