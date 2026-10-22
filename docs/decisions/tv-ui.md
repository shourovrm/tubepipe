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
- Device-untested fixes (box offline 2026-10-23): search keyboard on a new query, clear X, Centre stray key, focus
  after Back pops a screen, Pause flash on chip Up. Check each on the box; the worker's notes are in the commits.

## Gotchas
- Search moves focus to results once per `submissionCount`; keying the effect on "results showing" stole the
  keyboard whenever a new query fell back to the old results before its suggestions arrived.
- After Back pops a screen the shell focuses the content group (`backCount`); Back that switches to Home does not.
- Bar Back handling is a key preview plus a BackHandler: with focus cleared only the BackHandler fires.

## Tried / rejected
