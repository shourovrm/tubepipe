# Tubepipe

Android TV client in the style of SmartTube (D-pad only, 10-foot UI, no ads, no account required), based on PipePipe. Licence: GPL-3.0, inherited from NewPipe and PipePipe; every file we ship is GPL-3.0 and the source stays public.

Reference checkout, read-only: `~/repos/pipepipe`. Read it with grep and ranges; never edit it.

## Commands
Stack, modules and commands are decided in the first session and recorded in `docs/decisions/architecture.md`; fill these lines then.
- Setup: `git submodule update --init extractor` then `./gradlew help` (JDK 21; the extractor's JDK 25 toolchain request is overridden to 21, never downloaded)
- Build: `./gradlew :app:assembleRelease` (R8; signed with `keystore.properties`, else the debug key)
- Test: `./gradlew test`; instrumented: `./gradlew :app:connectedDebugAndroidTest` (needs the TV emulator or the TV box)
- Lint / format: `./gradlew ktlintCheck lint`; fix formatting with `./gradlew ktlintFormat`
- Run locally: start the Android TV 1080p AVD (or `adb connect <tv-ip>`), then `./gradlew :app:installRelease && adb shell monkey -p org.tubepipe.tv -c android.intent.category.LEANBACK_LAUNCHER 1`. Test every screen with the D-pad only: `adb shell input keyevent DPAD_DOWN|DPAD_CENTER|BACK`.
- Release, in order: 1 bump `versionCode`/`versionName` in `app/build.gradle.kts`; 2 `./gradlew :app:assembleRelease` with `keystore.properties` (gitignored); 3 `git tag vN`; 4 `gh release create vN app/build/outputs/apk/release/*.apk --generate-notes`; 5 sideload notes in the release body, F-Droid later.
- Exclusive resources (one session at a time): the TV emulator, the ADB-connected TV device.

## CI/CD — <FILL: enabled | declined YYYY-MM-DD>
Delete this section if declined. Pushing, opening a PR or tagging needs the user's approval in the current session, every time; committing does not.

Setup (GitHub Actions):
- `ci.yml` on every PR and push to `main`: `<FILL: toolchain setup, dependency cache>`, format check, lint, unit tests, build. `timeout-minutes: 30`. On failure, upload `**/build/test-results/**` and `**/build/reports/**` with `actions/upload-artifact`.
- `release.yml` on tags `v*`: `<FILL: build artifact>`, SHA-256 checksums, `gh release create --generate-notes`.
- Dependency updates: `<FILL: dependabot.yml weekly | none>`.
- Security: minimal `permissions:` per workflow, actions pinned to commit SHAs, no `pull_request_target`, secrets never reach fork PRs.
- Branching: `<FILL: GitHub Flow: branch <issue>-<slug>, PR to main, squash merge | trunk>`. Branch protection on `main`: CI required, 0 required reviews while solo.

Rules:
- Verify before a full push, in order: 1 format check; 2 lint; 3 unit tests; 4 build; 5 install and D-pad walk on the TV emulator or device. A quick push runs 1 and 2 only and is never used for a tag or release.
- A red `main` is the next task, before any feature work. Read the uploaded report; do not re-run and hope.
- A flaky test is a bug, usually a test that leaks a coroutine, thread, server or file into the next one. Fix the teardown so it waits for cleanup. No automatic retries, no `@Ignore` without an issue number. The fix is proven by running the test alone and inside the full suite.

## Memory — MANDATORY
`DECISIONS.md` is an index. Knowledge lives in `docs/decisions/`. Git history holds events.

Files and hard caps:
- `DECISIONS.md` — at most 40 lines, no line over 200 characters. Sections: **Topics** (one line per topic file), **Next** (in-flight handoff, at most 5 bullets), **Gotchas (cross-cutting)** (only what every session needs).
- `docs/decisions/<topic>.md` — one per area, at most 80 lines. Sections: **Current state**, **Open items** (at most 5), **Gotchas**, **Tried / rejected** (one line: what + why dead; never re-attempt).
- `docs/decisions/ops.md` — data fixes, config changes, manual production steps: date, exact command, why. Never in the log.
- `docs/decisions/LOG.md` — append at the end, one line per decision under 160 characters: `YYYY-MM-DD | decision | why`. Read recent entries with `tail -20`; otherwise grep.

Rules:
- Current state is rewritten, not extended. When a fact changes, delete the old sentence. A dated paragraph is history and belongs in LOG.md.
- Shipped work leaves Current state and Next in the same commit as the release. Releases, deploys and version bumps are never logged; the tag records them.
- Before any work: read `DECISIONS.md`, then only the topic files the task touches. Before trying a new approach: `grep -ri <keyword> docs/decisions/`.
- Update in the same commit as the change it describes. New topic = new file + one index line.
- Pruning trigger: if any file is over its cap, pruning is the first task of the next session. Check with `wc -l DECISIONS.md docs/decisions/*.md; awk 'length > 200 {print FILENAME": "FNR}' DECISIONS.md`
- If the layout is missing, create `DECISIONS.md`, `docs/decisions/LOG.md` and `docs/decisions/ops.md`.

## Local-only files
`AGENTS.md`, `CLAUDE.md`, `DECISIONS.md` and `docs/decisions/` are committed (public GPL repo, future contributors). `.claude/` and `sessions/` are local-only and gitignored.

## Non-negotiables
- GPL-3.0: keep `LICENSE`, credit NewPipe and PipePipe in README and About, ship source for every release. No proprietary YouTube API, no ads, no accounts required.
- TV first: every element reachable by D-pad with a visible focus state; no touch-only gesture; text readable at 3 m (body 24 sp or larger); safe margins 5 percent. Test each screen with keyevents before saying "done".
- Commits: one line, Conventional Commits prefix, imperative, under 50 characters, no body (`git commit -m "<subject>"` with a single `-m`). The reason goes in `docs/decisions/`, not the commit.
- Before saying "done": format, lint and tests run locally with real output shown; anything that could not be run is stated first.
- Change only what the task requires; say what was left alone and why. Full names, obvious code over clever code, comments explain why.

## Delegation
Every Agent call names its model; a call without one runs on the main session's model, which is a bug. Tiers: haiku reads and searches, never writes production code; sonnet implements one slice and runs its tests; opus plans, writes tests and reviews. Fable is chosen by the user for the main session only, never a subagent.
- Before spawning more than one worker, propose the split in at most five lines (slices, files each owns, model, worktree yes/no) and wait for a yes. Procedure: the `fan-out` skill.
- One worker per slice of disjoint files, each in its own worktree. Concurrency equals the number of disjoint slices, usually 3 or 4.
- Tests for a slice are written and committed by the main session before its worker starts. The worker runs them plus the format check before reporting, with real output.
- Serialize true conflicts: same-file edits and the exclusive resources under Commands. Build-tool and git locks self-queue; workers retry once on lock errors.
- Workers never claim a build passed; the main session builds and verifies the merged tree.
- Delegate work that touches three or more files or needs research; do small edits in the main session.

## Token economy
Terse replies, no filler; YAGNI; stdlib before dependencies; shortest working diff. Files already in context are never read again; search first, then read the matching range; build and test output goes through `grep` or `tail`.
