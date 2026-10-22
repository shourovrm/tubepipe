# TV UI behaviours

## Current state
- Search field keeps its own `TextFieldValue` and pushes each text change to the view model. The view model's state
  arrives a frame late; a field whose value lags its edit restarts the input connection and the keyboard closes.

## Open items
- Search keyboard fix is device-untested: if the keyboard still closes after one letter, the next suspect is Down in
  `SearchField`, which hides the keyboard and jumps to the suggestions once they exist.

## Gotchas

## Tried / rejected
