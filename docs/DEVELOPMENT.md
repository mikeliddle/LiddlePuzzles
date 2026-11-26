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

### Automatic release versioning

On this branch, run **Android release** on `main` with mode **release** to
publish. Only the owner can request a release, and the existing protected-main
checks and `android-release` environment approvals still apply. No release is
published just by merging this workflow change.

Before building, the workflow chooses one patch above the highest of the
`app/build.gradle` baseline and existing `vMAJOR.MINOR.PATCH` tags/releases in
that major/minor series. It also increments the highest Android `versionCode`
from the baseline and release notes. All history pages are read; drafts and
existing tags reserve their patch numbers. Releases at or above the baseline
must contain exactly one `Android versionCode: N` metadata line. Older releases
without that line are covered by the baseline. A newer major/minor series or
exhausted version range fails explicitly; update the baseline deliberately
when starting a new major/minor series.

The selected values are passed as paired Gradle properties
`-PreleaseVersionName=1.1.8 -PreleaseVersionCode=11` (example only), checked
against the unsigned APK, and carried through signing to the APK filename,
release metadata and `v1.1.8` tag. The tag points to the reviewed `main` commit;
the workflow does **not** commit a bump or bypass branch protection. Source
archives and ordinary local builds therefore keep the checked-in baseline,
not necessarily the version of the distributed APK. With published 1.1.7
(code 10) and no newer reserved versions, the next publish selects 1.1.8
(code 11).

Publication uploads a draft with checksums and generated GitHub release notes,
then makes it public. Publishing the same source again is allowed and assigns
another patch version. If uploading or publishing fails, inspect the existing
draft/tag before starting another run; retries never overwrite an existing
tag. Mode **verify** builds the baseline without calculating or reserving a
version, signing, or publishing. Version selection has credential-free checks:
`pwsh -NoProfile -File scripts/Test-ReleaseVersion.ps1`, also run by both
workflows.

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

Published 1.1.6 uses `liddlepuzzles://<mode>/v1/<token>` and cannot open HTTPS
links natively. The 1.1.7 release variant shares
`https://mllab.fyi/liddle-puzzles/share/#<mode>/v1/<token>` through the Android
Sharesheet and still opens the old release scheme. Validation continues to
advertise and send only `liddlepuzzles-local://<mode>/v1/<token>` in its separate
package. Debug has no sharing route. In either sharing-enabled build, **Shared
puzzles** accepts pasted HTTPS links, that build's custom scheme, or the bare
`<mode>/v1/<token>` form. Paste opens inside the current app without a web request
or clipboard auto-read. Only manual entry accepts surrounding whitespace.

The release manifest asks Android to verify the exact HTTPS host and the
`/liddle-puzzles/share/` path. Without an installed handler, the browser opens
the static fallback; the token is in the fragment, never in the HTTP request.
The app remains permission-free and offline. Domain verification is handled
by Android outside the app. Before shipping, verify that
`https://mllab.fyi/.well-known/assetlinks.json` returns JSON over HTTPS without
redirects, granting `delegate_permission/common.handle_all_urls` to
`fyi.mllab.puzzle` for the SHA-256 certificate of the APK actually distributed
(use the Play app-signing certificate for a Play-installed build, not the
upload certificate). Verify the fallback returns HTTP 200 without needing a
token and does not transmit the fragment.

On an isolated device/profile with the actual signed release, use
`adb shell pm verify-app-links --re-verify fyi.mllab.puzzle` on Android 12+,
then `adb shell pm get-app-links fyi.mllab.puzzle` and confirm `mllab.fyi` is
`verified`. Open a real HTTPS link from a messaging app without specifying a
package or component; also test the legacy scheme, browser fallback without
the new build, and Android 10 link handling separately. APK/host checks do
not verify installed-release association.

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

Tokens use canonical unpadded URL-safe Base64. The internal custom format is
capped at 512 characters; HTTPS swaps the fixed prefix without expanding the
puzzle payload budget. Versions, dimensions, clue ranges and canonical encodings are
checked. Generated imports are checked for a unique solution off the UI thread
under finite budgets. Only the starting definition is shared, never moves,
notes, history, solve time, or answers. Recipients resume separate private
progress for the same definition; normal progress and records are unchanged.
Shared finishes can count toward the same Responsible mode limit.

Whole-board Clear uses the ordinary durable play edit, not a replacement or
finish: it retains the active slot and elapsed time, and stores the cleared
board and Undo/Redo together where supported. Minesweeper retains the seed,
first-placement index, and mines after Clear; a placed field may have its first
square covered. Daily Word draft Clear uses the current pinned or local date
and preserves submitted rows. In-game New derives generation settings from
the active definition (including legacy Numberlink), not menu preferences.

## Device matrix

Run on an approved test device or isolated validation emulator, never on a
production profile. Record OS, window size, font/display scale, input method,
TalkBack version, and observed results. These are manual checks, not a
certification or a report that they have run.

| Setup | Verify |
| --- | --- |
| Android 10 and recent Android, ordinary portrait | Tap with same-square drift beyond slop; wait past the hold threshold; release for exactly one primary move. Drag across a target boundary and back, add a second pointer, cancel or lose focus: no pending edit. A committed hold never applies a tap on release. Numberlink still commits one drag at release. |
| Nonograms and Futoshiki, portrait and landscape | For Nonograms, tap rapidly near a cell edge and check the mark cycle, clue updates and Undo. The validation-only stalled/failing-save cases check that a pending outline appears without changing the saved mark and clears after save or rollback. For Futoshiki, try square then number, and number then multiple editable squares; verify the named armed key, givens, disarming, local Erase and Undo/Redo. |
| In-game actions, normal and shared play | Cancel and confirm Clear, check the unchanged puzzle/timer, Undo/Redo where available, and confirm the separate local Erase or Delete still works. In Daily Word, clear a draft after submitting a guess, including from Large controls. Resume a puzzle whose setting differs from the menu preference and confirm New uses the active setting. |
| Compact portrait (~320–360dp wide), short landscape, split window; default and largest font/display settings | Inspect 10x10 Nonograms, 8x8 Takuzu/Slitherlink, Sudoku and Daily Word. All clues, essential controls and insets remain visible; stabilize geometry through input, Undo/Redo and completion. Check the Shared puzzles field, keyboard, error, and Launch puzzle button. |
| Same windows with TalkBack, then keyboard/D-pad | Traverse clues/cells/edges and hear useful state/coordinates. Invoke click and named secondary actions; verify Undo/Redo and disabled states. Find and use Sudoku/Daily Word Large controls and Slitherlink Edge controls, and return focus/Back predictably. Open a shared link from the labeled input. |
| Settings → About in compact portrait and short landscape at large text; TalkBack or keyboard | Scroll to and activate **Privacy policy**. Verify focus reaches the named button and Android handles the external browser link; Back returns to Settings. The focused `DeviceTests` About cases intercept both external links and simulate a missing browser without visiting either site. |
| Release and validation side by side on an approved test target | Check seven mode routes, correct app resolution, cold/warm opens, paste with/without scheme, invalid input retention, and reopening shared progress without changing the normal game. Try a plain-text messaging client; do not assume tapping works. |

The full-board fit is intentional. Dense cells, edges, clue text and keys can
still be smaller than recommended when proportionally fitted. Large
alternatives and named accessibility actions help where provided; full
nonvisual traversal and every small-window/text combination remain unverified.
