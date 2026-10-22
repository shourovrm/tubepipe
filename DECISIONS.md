# Decisions index

## Topics
- `docs/decisions/architecture.md` — stack, modules, layering, what is reused from PipePipe and SmartTube.
- `docs/decisions/service.md` — extractor wrapper, repository API, kiosks, extractor quirks.
- `docs/decisions/player.md` — Media3 SABR port: spike result, what to restore, player gotchas.
- `docs/design-system.md` — androidtv skeleton plus the PipePipe-derived direction tokens (two reds, black theme).

## Next
- v0.1.0 release APK built and running on the Mi Box (Home, play one video). Not pushed or tagged yet.
- Device-untested: seek, Down panel, media keys, Search keyboard, Channel/Library/Settings D-pad walks.
- Time to play is ~8 s on the box (budget 2 s): prefetch on card dwell and pre-warm the WebView runtime.
- View models are activity-scoped (no nav library); clear them when a screen leaves the stack.

## Gotchas (cross-cutting)
- `~/repos/pipepipe`, `~/repos/smarttube` and `~/repos/newpipe-extractor` are read-only reference checkouts; never edit them.
- Release key: `~/.config/tubepipe/tubepipe-release.jks` via gitignored `keystore.properties`; back it up, updates need it.
- Build on the installed JDK 21; the extractor asks for a JDK 25 toolchain, so override it to 21 and keep toolchain auto-download off.
