# Decisions index

## Topics
- `docs/decisions/architecture.md` — stack, modules, layering, what is reused from PipePipe and SmartTube.
- `docs/decisions/player.md` — Media3 SABR port: spike result, what to restore, player gotchas.
- `docs/design-system.md` — androidtv skeleton plus the PipePipe-derived direction tokens (two reds, black theme).

## Next
- Skeleton done; next feature slices: `:core:service` (extractor wrapper), Home rows, player from the spike port.
- User confirms play-on-select; SABR-only playback (skip `YoutubeHttpDataSource`?); BD music fallback.
- Release keystore: create one and `keystore.properties`; until then release APKs carry the debug key.
- CI/CD section in AGENTS.md is still `<FILL>`: enable GitHub Actions or decline.

## Gotchas (cross-cutting)
- `~/repos/pipepipe`, `~/repos/smarttube` and `~/repos/newpipe-extractor` are read-only reference checkouts; never edit them.
- Build on the installed JDK 21; the extractor asks for a JDK 25 toolchain, so override it to 21 and keep toolchain auto-download off.
