# Decisions index

## Topics
- `docs/decisions/architecture.md` — stack, modules, layering, what is reused from PipePipe and SmartTube.
- `docs/design-system.md` — design system pick (androidtv) and the pending direction choice.

## Next
- User confirms or vetoes the proposed stack in architecture.md, then the Gradle skeleton and extractor submodule are created.
- User picks a direction from `.design-mockups/`; record its tokens in `docs/design-system.md`.
- CI/CD section in AGENTS.md is still `<FILL>`: enable GitHub Actions or decline.

## Gotchas (cross-cutting)
- `~/repos/pipepipe` and `~/repos/smarttube` are read-only reference checkouts; never edit them.
- Extractor toolchain is JDK 25; build with JDK 25 or the included build fails.
