# Decisions index

## Topics
- `docs/decisions/architecture.md` — stack, modules, layering, what is reused from PipePipe and SmartTube.
- `docs/design-system.md` — androidtv skeleton plus the PipePipe-derived direction tokens (two reds, black theme).

## Next
- User confirms or vetoes the proposed stack in architecture.md, then the Gradle skeleton and extractor submodule are created.
- User confirms play-on-select; then the Gradle skeleton, extractor submodule and theme tokens are created.
- CI/CD section in AGENTS.md is still `<FILL>`: enable GitHub Actions or decline.

## Gotchas (cross-cutting)
- `~/repos/pipepipe`, `~/repos/smarttube` and `~/repos/newpipe-extractor` are read-only reference checkouts; never edit them.
- Build on the installed JDK 21; the extractor asks for a JDK 25 toolchain, so override it to 21 and keep toolchain auto-download off.
