# third_party

## PipePipeExtractor (GPLv3)
Vendored source of the extractor, compiled by `extractor-build/` (see `settings.gradle.kts`).

- Base: upstream PipePipeExtractor `1a09d20e` (v5.5.0 tag + 1 NicoNico fix) + the patches in `patches/`,
  applied in order. The same base and patches 0001-0003 are vendored by the phone app (fyi-player);
  0004 (SABR context handshake is not a backoff) and 0005 (embedded player serves refused kids videos)
  were written here first. Fork: github.com/shourovrm/PipePipeExtractor.
- This folder is the source of truth for the build. Nothing outside the repo is needed.
- Update recipe, when upstream changes: check out the wanted upstream commit in a scratch clone,
  `git am patches/*.patch` (fix a patch by hand if it no longer applies, then `git format-patch` it again),
  `git archive HEAD | tar -x -C third_party/PipePipeExtractor` over this folder, build, re-test on the box,
  update the Base line above and `docs/decisions/service.md`.
