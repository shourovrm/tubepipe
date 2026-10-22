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
