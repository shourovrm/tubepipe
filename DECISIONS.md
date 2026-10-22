# Decisions index

## Topics
- `docs/decisions/architecture.md` — stack, modules, layering, what is reused from PipePipe and SmartTube.
- `docs/design-system.md` — androidtv skeleton plus the PipePipe-derived direction tokens (two reds, black theme).

## Next
- User confirms or vetoes the proposed stack in architecture.md, then the Gradle skeleton and extractor submodule are created.
- User decides the sidebar set and whether the Video screen is dropped for play-on-select (see architecture.md open items).
- CI/CD section in AGENTS.md is still `<FILL>`: enable GitHub Actions or decline.

## Gotchas (cross-cutting)
- `~/repos/pipepipe` and `~/repos/smarttube` are read-only reference checkouts; never edit them.
- Extractor toolchain is JDK 25; build with JDK 25 or the included build fails.
