# Android development and verification

Use Java 17, Android SDK platform 36, Build Tools 36.0.0, and the checked-in
Gradle wrapper. With a populated dependency cache, `--offline` is optional.
From the repository root on Windows:

```powershell
.\gradlew.bat --dependency-verification strict -PunsignedRelease=true engineTest :app:assembleValidation :app:assembleValidationAndroidTest :app:lintValidation :app:assembleRelease :app:lintRelease
```

The unsigned release checks shipping code without using release credentials.
The [release workflow](../.github/workflows/android-release.yml) handles
approved releases separately. Local signed builds require existing private
release signing properties and the key outside the repo (`SUDOKU_SIGNING_DIR`
or the platform default configured in `app/build.gradle`).
`scripts/New-ReleaseSigning.ps1` is for initial key creation, not replacement
of an existing signing key.

The [Android smoke workflow](../.github/workflows/android-pr.yml) covers PRs,
direct pushes to `main`, and manual dispatch, using pinned API 29 and 35 Google
APIs images. It builds and inspects both variants, but installs only
`fyi.mllab.puzzle.validation` and its test APK on isolated hosted emulators.
Instrumentation covers generated-game input/history, persistence and sharing
twice from reset app state. The [Nov 20 baseline run](https://github.com/mikeliddle/LiddlePuzzles/actions/runs/44709330573)
failed on Maven Central HTTP 429 before instrumentation; it provided no UX
test result. A compile or APK manifest check does not count as a device or
hosted smoke pass.

Smoke tests execute PR code on disposable hosted runners using `pull_request`,
not `pull_request_target`. They need only read access for checkout and do not
persist that credential, receive signing secrets, or upload artifacts for a
privileged workflow. Gradle uses an uncached per-job home with strict
dependency verification. Keep actions pinned and within the repository
allowlist; do not broaden it just to add optional tooling. Treat PR code as
untrusted even with these controls: runner network access is still available.

## Sharing routes and format

The 1.1.5 release candidate did not register a sharing route. Starting with 1.1.6,
release uses `liddlepuzzles://<mode>/v1/<token>`; validation still uses
`liddlepuzzles-local://<mode>/v1/<token>` in its separate package. Debug does not
advertise a route. Each sharing-enabled build opens the Android Sharesheet
and accepts only its own explicit scheme. On Home, **Shared puzzles** opens
a page where players can paste the link with or without its scheme. No clipboard
auto-read; leading/trailing whitespace is allowed only in manual entry.

This is the same starting-puzzle sharing feature, not a public puzzle catalog.
Custom schemes are not verified Android App Links; some SMS/chat clients leave
them as plain text and another app can claim an unverified scheme. A verified
public HTTPS route would require a separately selected host, a domain
association for the shipping package/signing certificate, and a browser fallback.
No website, server, network permission, account, or progress transfer is added.

V1 supports Sudoku, Takuzu, Nonograms, Futoshiki, Numberlink, Slitherlink and
Daily Word, not Minesweeper. The two transports use the same definitions:

- Sudoku uses 41 bytes of row-major givens, high nibble first.
- The other generated modes start with a size byte. Subsequent values are
  high-nibble-first with zero final padding. Takuzu and Slitherlink clues use
  a +1 offset; Futoshiki uses givens then horizontal and vertical inequalities
  (+1 offset for inequalities); Numberlink includes endpoints, not paths;
  Nonograms include row then column run lists, each preceded by a count.
- Daily Word uses a signed 64-bit epoch day and a SHA-256 fingerprint of the
  `local-v1` schedule ID, epoch, and ordered answers. Replay stays fixed to that
  day; an unavailable schedule is rejected. Changes to accepted guesses do not
  change that fingerprint. Explicitly retired guesses remain readable in old
  normal and shared saves without being accepted as new submissions.

Tokens use canonical unpadded URL-safe Base64. Links are capped at 512
characters; versions, dimensions, clue ranges and canonical encodings are
checked. Generated imports are checked for a unique solution off the UI thread
under finite budgets. Only the starting definition is shared, never moves,
notes, history, solve time, or answers. Recipients resume separate private
progress for the same definition; normal progress and records are unchanged.
Shared finishes can count toward the same Responsible mode limit.

## Device matrix

Run on an approved test device or isolated validation emulator, never on a
production profile. Record OS, window size, font/display scale, input method,
TalkBack version, and observed results. These are manual checks, not a
certification or a report that they have run.

| Setup | Verify |
| --- | --- |
| Android 10 and recent Android, ordinary portrait | Tap with same-square drift beyond slop; wait past the hold threshold; release for exactly one primary move. Drag across a target boundary and back, add a second pointer, cancel or lose focus: no pending edit. A committed hold never applies a tap on release. Numberlink still commits one drag at release. |
| Compact portrait (~320–360dp wide), short landscape, split window; default and largest font/display settings | Inspect 10x10 Nonograms, 8x8 Takuzu/Slitherlink, Sudoku and Daily Word. All clues, essential controls and insets remain visible; stabilize geometry through input, Undo/Redo and completion. Check the Shared puzzles field, keyboard, error, and Launch puzzle button. |
| Same windows with TalkBack, then keyboard/D-pad | Traverse clues/cells/edges and hear useful state/coordinates. Invoke click and named secondary actions; verify Undo/Redo and disabled states. Find and use Sudoku/Daily Word Large controls and Slitherlink Edge controls, and return focus/Back predictably. Open a shared link from the labeled input. |
| Settings → About in compact portrait and short landscape at large text; TalkBack or keyboard | Scroll to and activate **Privacy policy**. Verify focus reaches the named button and Android handles the external browser link; Back returns to Settings. The focused `DeviceTests` About cases intercept both external links and simulate a missing browser without visiting either site. |
| Release and validation side by side on an approved test target | Check seven mode routes, correct app resolution, cold/warm opens, paste with/without scheme, invalid input retention, and reopening shared progress without changing the normal game. Try a plain-text messaging client; do not assume tapping works. |

The full-board fit is intentional. Dense cells, edges, clue text and keys can
still be smaller than recommended when proportionally fitted. Large
alternatives and named accessibility actions help where provided; full
nonvisual traversal and every small-window/text combination remain unverified.
