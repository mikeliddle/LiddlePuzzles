# Google Play listing assets

[English (US) listing copy](listing.en-US.md) · [Play app icon](play-icon.png) ·
[Feature graphic](feature-graphic.png) · [Play Console checklist](play-console.md)

Upload these phone screenshots in the order shown. The first three lead with
in-progress play; the final image shows the eight games together.

| Order | Phone screenshot | Suggested alt text |
| --- | --- | --- |
| 1 | [Sudoku](phone/01-sudoku.png) | Medium Sudoku in progress with entered numbers, a pencil note, and the number keypad. |
| 2 | [Daily Word](phone/02-daily-word.png) | Two five-letter guesses with letter feedback and four blank rows. The answer is not shown. |
| 3 | [Nonograms](phone/03-nonograms.png) | An unfinished eight-by-eight Nonogram with row and column clues, filled squares, and crossed-out squares. |
| 4 | [Numberlink](phone/04-numberlink.png) | A Numberlink puzzle in progress with two completed connections and a third path started. |
| 5 | [Game library](phone/05-game-library.png) | The Liddle Puzzles home screen lists all eight games, from Sudoku to Slitherlink. |

Feature graphic alt text: “A little time to puzzle” above three boards in
progress: Daily Word, Sudoku, and Nonograms.

Upload `play-icon.png` as the **App icon**, not as a screenshot or feature
graphic. It renders the current [launcher foreground](../../app/src/main/res/drawable/ic_liddle_puzzles.xml)
over the [launcher background](../../app/src/main/res/values/colors.xml).
The export uses the center 72 x 72 units of the 108-unit adaptive icon viewport
to match its launcher framing, leaving Play to apply its own mask and shadow.
The launcher resources are unchanged.

## Capture provenance

- Captured November 24, 2026 on a Huawei STK-LX3 running Android 10, using
  `adb exec-out screencap -p` at the phone's native 1080 x 2340 resolution.
- Built from canonical `main` at `063003b021e73744afaac6920ef0c2858cafdcb8`,
  version `1.1.6` (version code `9`). An external Gradle init script gave
  the release variant a temporary application ID suffix and its own link
  scheme. The scratch APK was signed with a local debug key for installation
  only; no release credentials or validation APK were used.
- Each phone asset uses one intact app capture with Android system chrome
  and unused bottom space cropped away, scaled proportionally beneath a
  headline. The feature graphic uses cropped gameplay from the same
  captures. No board, guess, or in-app control was drawn or changed in
  post-production.
- The temporary install was removed after capture. Existing installs and
  their private data were not used.

## Upload specs

The five phone files are 1080 x 1920 (9:16) RGB PNGs without alpha, each
under 8 MB. The feature graphic is a 1024 x 500 RGB PNG without alpha.
The Play app icon is a full-square 512 x 512 sRGB 32-bit PNG under 1024 KB
with no baked-in mask or shadow, per the
[Play icon specs](https://developer.android.com/distribute/google-play/resources/icon-design-specifications).
The [copy limits](https://support.google.com/googleplay/android-developer/answer/9859152)
are 30 characters for the title, 80 for the short description, and 4,000
for the full description. See Google's
[preview asset requirements](https://support.google.com/googleplay/android-developer/answer/9866151)
for upload guidance. Tablet assets and a preview video are not included.
This package has not been submitted to Google Play.
