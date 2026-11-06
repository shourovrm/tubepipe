# TV UI behaviours

## Current state
- Search field keeps its own `TextFieldValue` and pushes each text change to the view model. The view model's state
  arrives a frame late; a field whose value lags its edit restarts the input connection and the keyboard closes.
- Player overlay focus path is buttons, seek bar, channel chip; Up on the chip hides the overlay (`onHideControls`).
  Any later key shows it again through the root's existing handler.
- One `PlayerBar` serves two pages: the channel page opened from a video, and the playlist page a queue video was
  started from. Layout: close X, picture (centre), title, Play/Pause, full screen. Left from the centre reaches X;
  X stops the video and removes the bar (`closeBar`, which also drops the player entry under a channel page).
- Back from a playlist video pops to the playlist page and the video keeps playing there (`minimizedQueue`). On a
  page with a bar, the first Back moves focus to the bar centre (`sendBackToBar`), the next one leaves the page.
  Centre on the picture reopens the queue video full screen. A video that ends in the playlist bar starts the next.
- Player Back closes one layer per press (list, panel, controls, then the page), handled on KeyDown in the key
  preview; the BackHandler alone needed two presses because the first Back only clears focus.
- Parental > Blocked words (`BlockedWordsPage`, a sub-page kept in `ParentalPage` state): `VideoFilters.kt` folds case,
  accents, spaces and punctuation, so one entry covers the Latin variants and another script needs its own entry.
  `FilteringContentRepository` drops matches from every list, Home drops them from Continue Watching, and the player
  shows the blocked view naming the word instead of loading; such a video is not written to history. Search also
  drops channels and playlists whose name matches. Home applies the words to its assembled rows, because its
  per-seed source cache and the remembered rows outlive a settings change.


## Open items
- Stray key on keyboard open: none with a 120 ms press; adb's 6 ms press still types one. Confirm on the real remote.

## Gotchas
- YouTube repeats a channel across search result pages; the grid keys results by url, so `SearchViewModel` keeps
  the first occurrence or Compose throws "Key ... was already used" (box crash, 2026-11-06).
- Search moves focus to results once per `submissionCount`; keying the effect on "results showing" stole the
  keyboard whenever a new query fell back to the old results before its suggestions arrived.
- After Back pops a screen, or leaves a single video for Home, the shell focuses the content group (`backCount`);
  Back from a top-level page to Home does not, because focus is then in the drawer.
- Text fields keep Down as a cursor move: a `focusProperties` down rule never fires, handle Down in the key preview.
- Bar Back handling is a key preview plus a BackHandler: with focus cleared only the BackHandler fires.

## Tried / rejected
