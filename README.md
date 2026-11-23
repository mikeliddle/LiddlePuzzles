# Liddle Puzzles

A quiet, offline puzzle collection for Android 10 and newer.

Enjoy eight games in one app: Sudoku, Daily Word, Takuzu, Nonograms,
Futoshiki, Minesweeper, Numberlink and Slitherlink. Fresh generated puzzles
and a daily word challenge offer plenty to play, with saved progress and
customizable appearance.

No accounts, ads, analytics or internet connection required.

## Local puzzle sharing prototype

Only the **validation** build enables sharing for Sudoku, Takuzu, Nonograms,
Futoshiki, Numberlink, Slitherlink and Daily Word. Minesweeper is not included
in this prototype. The native Share control opens the Android Sharesheet with
a `liddlepuzzles-local://<mode>/v1/<token>` link. A recipient needs the validation
app installed. These are local test links, not public web links; some messaging
apps will not make a custom scheme tappable.

Links contain immutable starting definitions, never solutions, moves, notes,
undo history or elapsed time. Sudoku v1 retains its original 41-byte row-major
givens encoding. The five other generated modes begin with one size byte, then
high-nibble-first values in unpadded URL-safe Base64, with zero final padding.
Takuzu clues and Slitherlink clues use a +1 offset; Futoshiki uses givens followed
by horizontal and vertical inequalities (+1 offset). Numberlink contains
endpoints, not paths. Nonograms contain row then column run lists, each preceded
by its run count. The mode/version fixes the rules, independent of menu defaults.

Daily Word v1 identifies a signed 64-bit epoch day and a SHA-256 fingerprint
of the `local-v1` schedule ID, epoch and ordered answer list, without including
the answer. Shared replay stays pinned to that date across midnight and timezone
changes. An unavailable or changed schedule is rejected rather than substituted.
Its guesses and timer stay separate from normal daily history and records.

Open a copied local link using Android's VIEW intent to test cold or warm opens.
Generated puzzle imports are validated for one solution off the UI thread
within finite search budgets. Links are capped at 512 characters, dimensions and
clue ranges are checked, and noncanonical data is rejected. Imported puzzles
have separate saved progress per starting definition and never update normal
Sudoku best times.
Reopening the same link resumes its separate progress. Back leaves it without
replacing the normal game.

On Windows, build with:

```powershell
.\gradlew.bat -PunsignedRelease=true sharingTest :app:assembleValidation :app:assembleValidationAndroidTest :app:lintValidation
```

Device checks are in `com.quietgrid.sudoku.SharingDeviceTests` (Sudoku) and
`com.quietgrid.sudoku.MultiSharingDeviceTests` (the six additions). They exercise
native sharing, VIEW routing, rotation, separate resume, warm opens and
rejection without overwriting normal saves, targeting only
`fyi.mllab.puzzle.validation`. The Android PR smoke job runs both sharing
suites alongside the existing gameplay and persistence checks.

Debug/release builds expose no sharing control or link intent filter. Production
HTTPS links and Android App Links remain disabled until a real host and its
domain association are verified. No server or network permission is added.

## Download

**[Download the latest release](https://github.com/mikeliddle/LiddlePuzzles/releases/latest)**

Download the APK and open it on your Android device to install; allow installation
from your browser or file manager only if Android asks.

## About

This app, including its code and artwork, was generated with AI under human
direction.

[Privacy](PRIVACY.md) · [License](LICENSE) · [Third-party notices](THIRD_PARTY_NOTICES.md)
