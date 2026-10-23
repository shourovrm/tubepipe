# Decision log

2026-10-22 | Compose for TV + Media3 + PipePipeExtractor submodule, five Gradle modules, manual DI | Leanback is in maintenance; SmartTube code is YouTube-only and Java 8
2026-10-22 | Design system androidtv, direction pending from three mockups | only published TV system; expression left to the direction step
2026-10-22 | Direction 1: PipePipe black theme, red #E53935 accents, wine #6E0E3D containers | user wants PipePipe identity kept
2026-10-22 | Speed budget: 1.5 s cold start, cache-first Home, prefetch on dwell | user wants it as quick as PipePipe
2026-10-22 | YouTube only in v1, drawer Home/Trending/Subscriptions/Search/Library/Settings + 1 pinned saved search, no Live tab | user decision
2026-10-22 | Port 4 upstream NewPipeExtractor kiosks (music, movies, gaming, podcasts) into :core:service | PipePipeExtractor lacks them; NewPipe 0.29.1 proves they work
2026-10-22 | Channel page = header + tab strip + grid; Home = reorderable sections | fewest D-pad presses to reach each tab content
2026-10-22 | Build on JDK 21, override extractor's JDK 25 toolchain | user choice; no toolchain download; extractor compiles on 21
2026-10-22 | Skip the trending podcasts kiosk | YouTube returns an empty page logged-out; upstream has the same bug
2026-10-22 | Media3 1.11.1 over ExoPlayer 2.18.7 | spike: 60 s 1080p, 0 rebuffers on Mi Box; Media3 edits in 3 files
2026-10-22 | PoToken via system WebView, not QuickJS | the WebView path worked in the spike on WebView 138
2026-10-22 | Compile extractor sources with our build files on JDK 21 | includeBuild keeps its JDK 25 toolchain; settings hooks can't reach it
2026-10-22 | Release builds fall back to the debug key | user wants release APKs only; no release keystore exists yet
2026-10-22 | Pin extractor submodule to commit 1a09d20e | upstream publishes no git tags; same commit the spike tested
2026-10-22 | Default YouTube client visionos (HLS), mweb SABR as fallback | matches PipePipe logged out; no accounts in TubePipe
2026-10-22 | Hide the Music tab where YouTube Charts has no data (BD) | user choice over a US chart fallback
2026-10-22 | Content country is a Settings choice, default device locale | user choice
2026-10-22 | Own release key outside the repo, no Play Store | sideload and GitHub releases need one stable signing key
2026-10-22 | CI declined for now | user will revisit later
2026-10-22 | minSdk 24 instead of 23 | DataStore 1.3 requires 24; Android 6 TV boxes are rare
2026-10-22 | PlaybackRuntime starts on first playback, not in Application | WebView setup must not delay cold start to Home
2026-10-22 | Display name TubePipe (package stays org.tubepipe.tv) | user choice; changing the package would break updates
2026-10-22 | Workers build once per slice; lint and full suite only after merge | parallel cold builds made workers slow
2026-10-22 | Settings is a 9-panel hub with one page per panel | the single list was too long; mockups in .design-mockups/round2
2026-10-22 | Mini player reached by drawer "Now playing" and first Back | Mi Box remote has no menu or media keys
2026-10-22 | Loop lives on PlaybackController, carries to the next video, off on stop | one state for bar and mini window
2026-10-22 | Home publishes each section as it loads | one slow Mix lookup held every row back on cold start
2026-10-22 | AppNavigator lives on AppContainer; MainActivity is singleTask | TV launcher stacked a second activity at Home
2026-10-22 | Mini player removed; Back from a video stops it and goes Home, except playlist videos | user found the corner window unusable
2026-10-22 | Channel opened from a video shows the video in a bar; Back returns to it full screen | user request
2026-10-22 | Search uses the ALL filter (videos, channels, playlists); 0-second durations hide the badge | Shorts shelf items have no duration
2026-10-22 | Parental: YouTube Restricted Mode cookie (PipePipe's) and an 18+ playback block | user chose these two over a PIN
2026-10-22 | Settings hub is variant D colour cards, no explanatory text | user choice from four mockups in .design-mockups/round3
2026-10-22 | Retry mweb when VisionOS offers only 360p | kids videos are refused to VisionOS; NewPipe fails them, PipePipe logged in uses mweb
2026-10-22 | Search field owns its TextFieldValue, view model gets each change | async state made the field restart input and close the keyboard
2026-10-22 | Up on the player's channel chip hides the control overlay | user request
2026-10-22 | Playlist video Back keeps playing in the shared PlayerBar; bar has X left of centre, Back focuses centre | user request
2026-10-22 | Drawer opens a tab when focus lands on it (250 ms delay), no Centre needed | user request
2026-10-22 | No login for now | mweb retry gives 1080p logged out; revisit only for bot checks, 18+ or own history (phone code sign-in)
2026-10-23 | News falls back to the US feed when the country's feed is empty | YouTube returns 200 with no videos where News is not offered
2026-10-23 | Prefetch dwell 1.15 s, cap 6 a minute, one at a time | user asked for 1.15 s; limits keep traffic near YouTube's own hover previews
2026-10-23 | Release key tubepipe-release.jks (gitignored), first used for v0.2.1 | a debug-signed public build would force a reinstall when the key changes
2026-10-23 | Public repo github.com/shourovrm/tubepipe; v0.2.1 is a pre-release | box offline, device check skipped by user choice
2026-10-23 | Box tests use adb keyevents; sendevent on aml_keypad (IR) is not the BT remote and misses list clicks | found while checking the stray key
