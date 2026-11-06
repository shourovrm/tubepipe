# third_party

## PipePipeExtractor (GPLv3)
Git submodule `PipePipeExtractor` = github.com/shourovrm/pipe-extractor-patched, branch `patched`: upstream
PipePipeExtractor (commit `1a09d20e`, v5.5.0 + 1 NicoNico fix) plus our patch commits on top. The phone app
(fyi-player) uses the same submodule at the same commit. Compiled by `extractor-build/` (see `settings.gradle.kts`).

- Setup after clone: `git submodule update --init`.
- Change recipe: commit in the submodule checkout (`~/repos/pipe-extractor-patched` or this path), push the
  `patched` branch, then update the pinned commit here and in fyi-player (`git add third_party/PipePipeExtractor`).
- Upstream bump: in the patched repo, `git fetch upstream` and rebase `patched` onto the new upstream commit,
  push, then pin both apps.
