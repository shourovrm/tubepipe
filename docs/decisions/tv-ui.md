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


## Open items
- Country picker (Settings > Region): Down from its search box does not reach the results; only Tab does. Same cause
  as the old Search bug: the text field keeps Down as a cursor move. Give it the Search field's Down handler.
- Back from a single video lands on Home with nothing focused; the first Down opens the drawer. That Back path is
  `openDestination(Home)`, which does not bump `backCount`.
- Box-checked 2026-10-23 on v0.2.1: search keyboard on a new query, clear X, Pause flash, Back focus from a playlist
  page, New playlist from the player all pass. Stray key: none with a 120 ms press; adb's 6 ms press still types one.

## Gotchas
- Search moves focus to results once per `submissionCount`; keying the effect on "results showing" stole the
  keyboard whenever a new query fell back to the old results before its suggestions arrived.
- After Back pops a screen the shell focuses the content group (`backCount`); Back that switches to Home does not.
- Bar Back handling is a key preview plus a BackHandler: with focus cleared only the BackHandler fires.

## Tried / rejected
