# Decisions index

## Topics
- `docs/decisions/architecture.md` — stack, modules, layering, what is reused from PipePipe and SmartTube.
- `docs/decisions/service.md` — extractor wrapper, repository API, kiosks, extractor quirks.
- `docs/decisions/player.md` — Media3 SABR port: spike result, what to restore, player gotchas.
- `docs/design-system.md` — androidtv skeleton plus the PipePipe-derived direction tokens (two reds, black theme).

## Next
- Wave 1 (parallel): `:core:service`, `:core:player`, `:core:database`. Wave 2: screens. Then release APK on the box.

## Gotchas (cross-cutting)
- `~/repos/pipepipe`, `~/repos/smarttube` and `~/repos/newpipe-extractor` are read-only reference checkouts; never edit them.
- Release key: `~/.config/tubepipe/tubepipe-release.jks` via gitignored `keystore.properties`; back it up, updates need it.
- Build on the installed JDK 21; the extractor asks for a JDK 25 toolchain, so override it to 21 and keep toolchain auto-download off.
