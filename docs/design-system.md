System: androidtv

Platform android-tv has one published system (`design-system/references/androidtv.md`), so the pick is forced.
Skeleton numbers (type scale, 4dp grid, 58/28dp margins, 20dp gutter, 40dp buttons, 48/64dp list items, focus scale
1.1 and 1.05, focus border 3dp and 2dp) are copied unchanged from that file and are not repeated here.

## Direction
Chosen 2026-10-22: `.design-mockups/direction-1.html`, PipePipe's black theme on TV. Every value below comes from
PipePipeClient `res/values/colors.xml` and `assets/logo.png`; nothing is invented.

Type: Roboto (device sans, no font file shipped) for body, labels and titles; Roboto Condensed 600 for display and
headline roles (`TypefaceTokens.Brand`). Sizes stay on the system scale; body 16sp, captions 14sp, card title 16sp.

Colour tokens (dark only, no light scheme):
- background `#000000` (black_background_color); surface `#0F0F0F` (black_card_item_background_color);
  surfaceVariant `#202020` (black_card_item_contrast_color); borderVariant `#262626` (black_border_color on black)
- onBackground and onSurface `#FFFFFF`; onSurfaceVariant `#C9CACB` (white_secondary)
- primary `#E53935` (youtube_primary, launcher red); onPrimary `#FFFFFF`
- primaryContainer `#6E0E3D` (logo wine, sampled); onPrimaryContainer `#FFFFFF`
- border (focus) `#FF5252` (dark_settings_accent_color); focus border on surface `#0F0F0F` is 4.4:1
- scrim `rgba(0,0,0,.67)` (duration_background_color); duration text `#EEFFFFFF`
- live badge: fill `#E53935`, text white (live_duration_background_color without alpha)
- error: `#FF5252` on `#3B0000`; the only status role. SponsorBlock segments on the seek bar: sponsor `#00D400`,
  intro `#00FFFF`, outro `#0202ED`, interaction `#CC00FF` (PipePipe defaults, user-editable)
- service tint, as PipePipe does: when the active service is not YouTube, primary and border switch to that service's
  colour: SoundCloud `#F57C00`, PeerTube `#FF6F00`, Bandcamp `#17A0C4`, BiliBili `#FB7299`, MediaCCC `#9E9E9E`

Where the red goes: focus borders, the filled primary button, the watched-progress bar on thumbnails, the seek bar and
its thumb, the live badge, the text caret, the on toggle. Nowhere else; body text and icons stay white or `#C9CACB`.
Where the wine goes: the active drawer item, the logo circle, selected chips. Never as a focus cue.

Brand mark (chosen 2026-10-22, `.design-mockups/logo/logo-1-pipe-play.html`): a red pipe (#FF5252 to #C62828)
bent into a play arrow with two white couplings, on a `#6E0E3D` circle; 108dp vector launcher icon, 320 x 180 banner
PNG with the TubePipe wordmark (Tube white, Pipe #FF5252, Roboto Condensed 700).

Radius: card and list item 8dp; wide button 12dp; text and icon buttons full. Density: 4-up rows (196dp cards),
drawer collapsed at 56dp and expanded on focus to 256dp, list items 48dp one line and 64dp two lines.

Motion: focus scale and border 150 ms, exponential ease-out; row scroll 200 ms; nothing else animates by default.
