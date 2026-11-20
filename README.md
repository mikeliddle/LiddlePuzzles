# Liddle Puzzles for Android

A standalone, offline native Android puzzle app. Open this project in Android Studio.
There is no in-app account, advertising, analytics, or network permission.
New Sudoku games are generated locally in Easy, Medium and Hard tiers.

The shared [UX design principles and per-game review](DESIGN.md) document
navigation/icons, grid-adjacent controls, input, errors, motion and accessibility.

**AI disclosure:** This app, including its code and artwork, was generated with AI
under human direction. Third-party components retain their own attribution and
licenses.

## Download

Download **Liddle Puzzles** from the
[latest GitHub Release](https://github.com/mikeliddle/LiddlePuzzles/releases/latest).
From version 1.1.1 onward, the production download is named
`liddle-puzzles-<version>.apk`; already-published historical assets remain unchanged.
It is a signed sideload build for Android 10 and newer; no app store or account
is needed. Open the downloaded APK to install, allowing installation from that
specific browser/file manager only if Android requests it. Do not bypass
Play Protect or other unsafe-app warnings.

The release includes a SHA-256 checksum. APKs and signing keys are not checked
into the repository. The official release cannot update a differently signed
development installation; preserve any development saves before switching.

## Play

The launcher icon is a bold white capital L assembled from six separated puzzle
tiles on a near-black field, with a tall stem and a clear horizontal foot.
Native vectors keep the mark crisp at launcher sizes. Adaptive and round icons
share the same safely inset artwork; Android 13+ themed icons use its transparent
monochrome silhouette.

The scrollable **Liddle Puzzles** hub offers **Sudoku**, **Daily Word**, **Takuzu**,
**Nonograms**, **Futoshiki**, **Minesweeper**, **Numberlink**, and **Slitherlink**.
Settings applies appearance and accent choices to every mode. New builds use the
permanent application ID **`fyi.mllab.puzzle`**. This is a separate Android app,
not an update to the older `com.quietgrid.sudoku` installation. Old private games,
notes, preferences, best times and records do **not** transfer automatically.
Keep the old app if its progress is needed; no private-data migration is included.
Each mode has independent progress within the new app. Save formats and the
production signing certificate are unchanged. The Java namespace/source package
remains `com.quietgrid.sudoku`; manifest components are fully qualified. That
internal namespace, existing signing configuration and the Sudoku game's own
name are not the collection's display or download name. The Gradle root project
uses the machine-readable name `liddle-puzzles`.
The local signing-directory path and frozen Daily Word schedule stay unchanged.

### Six more generated puzzles

Each additional game has a compact **? / How to play** button with its exact rules
and controls. Help is optional, never an automatic tutorial. **New puzzle** asks
before replacing unfinished progress. Returning from the hub resumes each game's
own board, including finished results. Each shows an active-play timer and a
short completion cascade on a genuine win. No difficulty tiers, daily schedules,
sounds, accounts, or network features are added to these games.

| Game | Board and controls | Completion |
| --- | --- | --- |
| Takuzu | 6x6 or 8x8; new puzzles default to 8x8. Tap 0 / hold 1; repeat the same default value to clear, or select 0/1/Erase for repeated entry. Underlined clues are fixed. | Equal numbers of 0 and 1 per row/column, no three consecutive equal digits, no repeated completed rows or columns. |
| Nonograms | 5x5, 8x8 or 10x10. Row/column run clues; tap fills, hold crosses; repeat either mark to clear. | The filled squares match all runs exactly; empty-square crosses are optional notes. |
| Futoshiki | 4x4. Select a square, then 1–4 or the Erase icon. | Each row/column contains 1–4 once and follows every inequality. The pointed end faces the smaller digit. |
| Minesweeper | 8x8, ten or fourteen mines. Tap reveals; hold toggles a flag. Explicit Flag tool retained. Covered/revealed cells have different persistent fills. | Reveal all safe squares to win. A revealed mine loses. Flags are optional and limited to the mine count. |
| Numberlink | New puzzles: 6x6 with five pairs and required bends/detours. Legacy 4x4 games still Resume with history intact. Drag from an endpoint/existing path; backtrack to retract. Tap two neighboring squares remains an alternative. | Disjoint orthogonal paths join matching pairs and cover every square, with no branches or extra loops. |
| Slitherlink | 4x4/6x6/8x8 cells. Tap draws a line, hold crosses; repeat either mark to clear, or open native accessible Edge controls. | Exactly one closed loop, no branches or loose ends, matching every numbered cell's adjacent-edge count. |

All eight normal-play screens fit the full board, clues and essential controls
together without scrolling/panning. Landscape reflows them side by side;
measured proportional fitting handles constrained viewports/enlarged text.
The user explicitly chose fitting everything even when targets and effective
text must become smaller: this is not a universal 48dp/accessibility guarantee.
Optional large controls and native node/key alternatives remain.
Complete grids/clues are centered within their board region.
Numberlink uses native focusable square
buttons with spoken connections; Slitherlink supplies a native edge selector with
current state and a cycle button in addition to its direct-touch diagram.
Its **Cell clues** list exposes all sixteen coordinates, including blank cells
and their current drawn-side counts. Nonogram filled cells have a check mark
and an ink/surface contrast pair, not color alone. Futoshiki exposes the selected
entry target and sizes inequality gutters from scaled text metrics.
Input and action controls sit directly below or beside their grid instead of
being pushed to the screen bottom. Selection, errors, Undo, timer digits and
completion do not insert rows or shift this group; meaningful feedback shares
existing fixed heading/timer slots. Optional clue/edge panels open separately.
Familiar
Back, Help and Undo actions use labeled native vectors.

Each generated mode now uses Sudoku's submenu pattern: Back, named heading and
shared Settings, Resume (or View completed board), and new-puzzle choices.
Gameplay Back/New returns there; replacing active progress requires confirmation.
Takuzu offers 6x6/8x8, Nonograms 5x5/8x8/10x10, Numberlink only 6x6 with five pairs for new games, and Minesweeper ten/fourteen
mines on 8x8. These are real size/pair-count/density settings, not invented human
difficulty ratings. Slitherlink offers 4x4/6x6/8x8 size choices; Futoshiki retains
its certified 4x4 configuration. Daily Word remains direct to its one daily game. Choices persist
for future games without changing an active board. Settings closes back to the
invoking menu. Menu time never counts as active play.

The hub gives every game the same tonal button geometry with its full name and
a puzzle-specific vector icon. Eight stable light/dark color identities
distinguish the games independently of the chosen app accent; Sudoku is no
longer a special filled primary button. There are no button subtitles.

Larger Slitherlink definitions carry an explicit dimension prefix while legacy
4x4 bytes remain unchanged. Their 500-step histories store edge marks as bytes
inside a new checked history envelope, retaining the existing 262144-byte cap.
All sizes retain exact uniqueness proofs and the same cancellable generation
budget. Independent planar-region row enumeration verifies larger samples
without reusing the production edge solver.

Nonogram sizes have ongoing local procedural generation, exact uniqueness checks
and no finite pack to exhaust. Forty sampled seeds per size had forty distinct
unique clue sets, independently verified. Clue bands measure all their run text
before the whole board is fitted; no clues are omitted to make larger grids fit.
Larger sizes necessarily trade away physical target/text size on small displays.
Legacy 5x5 save bytes remain unchanged; an explicit size prefix distinguishes
larger definitions, including timed saves and Undo history.

Existing Takuzu 6x6 saves open unchanged and resume as
6x6. Zeros use cyan and ones amber with contrasting light/dark fills and actual
digits; fixed givens are underlined. All cells participating in a triple,
overfull digit count, or duplicate completed row/column receive an exclamation
mark, outline and accessible reason. Correcting a move clears stale errors.
Only a completely valid board shows the prominent completion banner. Sizes are
not difficulty ratings; there is no daily schedule or sharing feature.

Takuzu's **0 / 1 / Erase icon / Undo icon** bank sits immediately below its grid.
With no tool selected, tap enters 0 and hold enters 1; repeating the same
default value clears the square. An armed digit writes repeatedly; switching it never changes the
last square. Tap the armed tool again or outside the board/bank to return to
default tap/hold input. Holding still enters/clears 1 with a tool selected.
Erase selects a repeated local eraser without cycling through digits.
The armed tool has a strong outline and native selected state without moving the digit. No stale cell-first
entry target remains. Sudoku retains its own digit-first/cell-first behavior.
When a Sudoku digit is selected, holding a square toggles that digit's note
without changing the explicit Notes mode or also placing a number on release.
Blocked candidates remain suppressed; normal provenance and Undo apply.
Binary input follows the default tap / secondary hold policy in `DESIGN.md`;
named long-click accessibility actions and existing explicit tools remain.
Pending holds cancel on interruption, and add no sound or vibration.

The focused hold/route follow-up is recorded in
`dist/centered-menus-20261117/controls-binary2/preservation.json`: all eight
selected native cases passed, including actual short/held/canceled input,
Mines multitouch, exact note/flag persistence, legacy and larger Numberlink
drag/Undo, portrait/landscape with in-process 2x text, menus and stationary red
loss. Validation private files were restored byte-for-byte; regular player
APK/data and global settings were unchanged. Forty independently solved 6x6
seeds were distinct even after symmetry/relabeling normalization, with generation
11.1ms mean / 72.0ms maximum in the final host sample; three native samples took
103/17/41ms. These are observations, not latency or human-difficulty guarantees.
Takuzu, Nonograms, Futoshiki, Numberlink and Slitherlink each have **Undo** for
their last 500 real edits, saved atomically with the board and retained across
restarts. An edge edit is one whole step; selection, rejected and same-value
actions add no history. Undo can reopen a completed puzzle. Existing saves
start with empty history without resetting their board or changing its size.
Minesweeper retains its reveal/flag rules without Undo; Daily Word does not
permit undoing submitted guesses. Sudoku's existing Undo is unchanged.

Nonogram row/column clues turn red with a dashed outline only when no
legal placement of their runs can fit the current filled and explicitly
crossed-empty squares. Undecided squares remain unconstrained: incomplete
lines are not automatically errors, and this check never compares against a
hidden picture. A legal entry differing from the solution is a mistake, not a
red error. There is no visible error message, count, exclamation mark or reason
list. Focusable clue descriptions identify the row/column and explain
the contradiction without declaring any particular square wrong. Correction
and Undo immediately refresh these signals. Actual save/load failures still
show their necessary messages.

Futoshiki marks all involved repeated row/column digits and entered pairs that
break a visible inequality with an outline, **!** and coordinate/rule descriptions.
Blank squares alone are not errors. Slitherlink marks branches, clue over-counts
and closed loops incompatible with remaining clues or drawn lines. Its native
red lines/digits and subtle outline rings replace visible error text and ! marks.
Cell clues and Edge controls retain localized reasons in accessible descriptions.
Neither check consults a hidden solution; correction and Undo refresh the feedback. All six new games
use a prominent completion/result banner, with no repeated celebration on redraw.
Routine idle instructions live in optional help rather than occupying play space.
Obvious action explanations and slogans are omitted app-wide; functional labels,
necessary failure messages and destructive-progress confirmations remain.
Ordinary headings/labels are centered. Familiar actions are icon-first, buttons
have at most one visible action word, without subtitles or relocated descriptive
captions. Hub game-title labels retain their full established names, including
Daily Word, alongside icons. Settings use self-contained choices such as `6x6` or `10 mines`;
saved details and toggle explanations remain accessible without visible footers.
Full accessible names/tooltips are retained. Notes/Flag toggles communicate
state with selected fill, outline and native selected/checked state rather than
On/Off text; clue alignment and QWERTY geometry keep their spatial meaning.

Minesweeper loss stays in place: red mine cells and a board outline, a distinct
detonated-mine *! and accessible description, and concise red Mine hit feedback.
Board/cell/controls bounds do not change on flag/reveal/win/loss. Short reveal
fades/flood stagger, flag pulses and a mine-hit cascade affect paint opacity only;
saving, loss lockout and timer freeze are immediate. Effects stop on interruption
and respect disabled motion without replaying on resumed losses. No sound or
vibration is added.

Numberlink previews a drag without saving intermediate squares. Release commits
one logical Undo action; cancellation, multitouch and lifecycle interruption
discard it. An existing-path start keeps its endpoint prefix and replaces the
downstream tail on extension. Fast motion interpolates orthogonal cells but
never teleports diagonally or overwrites other pairs. Pair colors are stable
across redraw/resume and have distinct light/dark variants; digits and spoken
connections remain, so color is not the only signal. There is no idle
"choose a neighboring square" prompt.

The 6x6 Numberlink setting is the only new-game choice; old smaller preferences
select it without altering any saved board. Its 36 cells and five routes require at least four cells per route, no
adjacent matching endpoints, twelve total bends and ten aggregate route steps
beyond shortest endpoint distances. These are checked against the one exact
solution, not a human technique rating or renamed tiny board. Larger puzzles
use randomized backbite mixing of full-cover paths followed by a bounded exact
uniqueness search with degree/reachability pruning. Legacy 4x4 payload bytes
remain unchanged; 6x6 uses an explicit dimension prefix and retains timed
500-step Undo. The full larger board still fits without scrolling.

Navigation stays in the screen corners outside fitted play content: Back at the
top left, Settings at the top right. Settings contains global appearance/colors
and an entry for each game. Sudoku's Auto notes and Mistakes live together;
other games reuse their existing new-puzzle configuration without replacing
current saves. Fixed configurations do not acquire invented options. Puzzle
collection import/export and post-win ratings are not offered.

**Erase** removes local input and uses an eraser icon. **Clear** and **Reset**
are reserved synonyms for whole-board player-input removal with preserved
givens/clues and confirmation; no such whole-board action is added here.

Sudoku and Daily Word have an optional **Large controls** icon beside their
headings. These native scrolling dialogs provide genuinely large input targets
and reflowing 20sp descriptions: Sudoku has a coordinate/value/notes selector
and a three-column digit bank; Daily Word has a letter selector, Add/Delete/Enter
and readable guess feedback. Their compact spatial boards and original one-row
Sudoku bank / uniform QWERTY keyboard remain available; those dense controls
do not themselves promise 48dp targets or unlimited font enlargement.
The alternatives follow font scaling instead of fitting the text smaller.
Daily Word announces each accepted guess and the final result once; Enter
activates a focused native control rather than always submitting a guess.

These are original procedural generators, not a bundled level catalog or a
symmetry-only template. Takuzu starts with a randomly searched valid binary grid;
Nonograms samples pictures and derives runs; Futoshiki searches Latin squares and
removes givens/inequalities; Numberlink samples and partitions Hamiltonian paths;
Slitherlink grows connected cell regions and derives simple boundary loops.
Each clue puzzle is accepted only after an exact solver proves **one solution**.
Completion independently checks the displayed rules, never equality with a
hidden target grid. This does not claim advanced human difficulty grading.
Compact boards make proofs practical; a shared per-generation budget of
3,000,000 search nodes / 10 seconds bounds the background work. Navigation cancels
it. Exhaustion produces an explicit retry error and preserves the previous save;
there is no ambiguous or mislabeled fallback.

Minesweeper places its mines on the first reveal, excluding that square and all
its neighbors. Later guesses may be necessary: **not a no-guess guarantee**.

Each new mode uses its own versioned, checksummed atomic file under private
`files/puzzles/`. A successful edit is saved before being displayed. Failed saves
retain the previous board and report an error; unreadable saves require explicit
replacement or retry. Timed LPZ3 envelopes retain LPZ1/LPZ2 reading, exact boards
and Undo; legacy elapsed starts at zero. Sudoku and settings formats
are unchanged. `puzzleTest` runs reproducible per-mode batches with separate
row-pattern, Latin, path-cover and exhaustive planar-loop oracles, and writes
sample records plus test-only native fixtures under `dist/six-puzzles-20261117/`.

### Daily Word

Find a five-letter English word in six guesses, using the in-app letter keyboard
or a physical keyboard. The custom three-row QWERTY keyboard has staggered,
uniform-size letter keys, Delete at the right of the middle row, and Enter at
the right of the bottom row. The back arrow returns to Liddle Puzzles. There is no
help button, visible date, solved-result banner or midnight reminder; error
feedback appears only when needed, and a loss still reveals the answer.
Green/check means correct spot; gold/open circle means
in the word, different spot; gray/dash means no remaining copy of that letter.
Exact matches are counted first, then misplaced copies from left to right,
so repeated letters cannot receive excess credit. TalkBack can read each row's
letters and feedback; the keyboard retains the strongest result for each letter.
Invalid, short, or already-tried guesses show an error without consuming an attempt.
Finished boards remain visible; a loss reveals the answer. A concise active-play
timer stays near the heading; a genuine win gets the same short confetti
language as Sudoku, without hiding the result or replaying it on resume.
There is no account, network permission, server, social feature, or notification.

The immutable **local-v1** schedule uses `LocalDate`, not elapsed 24-hour periods:

```
epoch = 2026-11-16
index = floorMod(localDate.toEpochDay() - epoch.toEpochDay(), 619)
answer = answers-v1.txt[index]
```

Devices on the **same local calendar date** get the same answer, independent of
installation, locale, time zone, random state, and connectivity. Local midnight
changes the puzzle; devices on different dates can differ. The app checks on resume,
at midnight while open, and on date/time/time-zone broadcasts, including DST and
leap days. Each date's guesses and unfinished draft are saved independently in
`daily-word-v1.bin`; returning to an already played date never resets it.
The versioned QDW2 writer includes each date's elapsed time and reads QDW1
records without changing guesses/drafts/schedule; unknown legacy time is zero.
Failed saves keep the previous state and show an explicit error. Unreadable records
are not silently replaced.

The seven newly timed games count only resumed, focused, playable time, using a
monotonic clock. Background/sleep, generation and unfocused modal time (including
help and Word large controls) are excluded. Elapsed is checkpointed about every ten seconds
and at edits/lifecycle boundaries; an abrupt kill can lose the last uncheckpointed
interval. Win/loss freezes it. Undo from completion resumes accumulated elapsed,
not time spent looking at the result. Only successful New puzzle replacement
resets generated-game time; Daily Word keeps a separate total for each date.
Timer ticks update only their label, without rebuilding the board, moving focus,
scrolling play or announcing seconds. Completion motion respects Android's
disabled-animation preference, stops on focus/lifecycle loss, and never fires
for loading, invalid/full-but-wrong boards, unchanged input or losses. No new
best-time statistics were added; Sudoku's existing clock and records are unchanged.

This is a finite **619-day repeating cycle**, not unlimited distinct words. The
619 distinct answer words were selected locally from ordinary English vocabulary,
then ordered once by ascending SHA-256 of ASCII `quietgrid-local-v1:` + word.
The bundled ordering and modulus are frozen; updates must not reorder, replace,
append to, or otherwise remap this v1 schedule. Its SHA-256 is
`c74528789b30d50ecc6a9d04fb8cfa05ebe160a79fa5dce80c9b111690b7f7f6`.
The broader 12,653-entry guess vocabulary comes from the CC0 Letterpress/Words
dictionary, pinned and attributed in `THIRD_PARTY_NOTICES.md`. Guesses include
US, UK and Australian spellings and some uncommon words. No proprietary word
schedule, source, branding or artwork is used. As in any offline daily puzzle,
someone can inspect the APK or change their clock; this is not cheat-proof.

### Sudoku

The Sudoku screen opens on the difficulty-selection menu. **Resume** continues your unfinished
saved game; selecting a new difficulty asks before replacing it. **Settings**
contains Auto notes, Show mistakes, and Red, Orange, Yellow, Green, Blue, Pink,
Purple, and Brown color swatches in a compact two-row grid, with an outlined
selected swatch and accessible color names. Accents have accessible light/dark variants,
independent of System/Light/Dark appearance. Existing Forest, Ocean, and Orchid
preferences map to Green, Blue, and Purple without changing saved games or records.
Dark Red and Brown use deeper, saturated button fills with light labels.
Dark Orange uses vivid pumpkin-orange fills with dark labels. Each has coordinated
readable accents for board entries, notes, and outlines. Swatch fills keep their
own hue regardless of the selected accent; only the selection outline changes.

Select a digit in the below-board number bank to highlight matching entries and arm it.
Matching visible notes get an accent-colored circle around that individual digit,
including manually added notes; suppressed candidates stay hidden. Switching or
clearing the bank selection updates these highlights without changing notes.
Tap editable squares to enter that digit repeatedly. Alternatively, select a square
first, then tap a bank digit to enter it once (or toggle its note in Notes mode).
A number-first placement never selects the target square or its houses, so the
next bank tap switches the digit rather than overwriting that square.
Square-first one-shot entry clears both square selection and armed input. With no square
selected, another bank digit switches the armed digit, and the same digit cancels.
Tap outside the board and input controls to stop number entry.
The **Notes** button deliberately keeps the armed digit while switching modes;
switching modes never writes into the selected square. Selecting a square
highlights its row, column, and box. Fixed clues cannot be changed.
Tapping a bank digit clears the previous square/house highlight; matching-digit
highlights remain only when number-first input is armed.
Tapping outside the board clears square selection
and armed input; Notes remains an input-mode control rather than an outside tap.

The small number under each bank digit is `max(0, 9 - occurrences on the board)`,
including clues. Notes do not count as entries. A digit with zero remaining is
disabled and automatically deselected; erasing or undoing an entry makes it
available again. Number entry never opens the software keyboard.
Placing the last occurrence also clears the square, house, and matching-digit
highlights; ordinary number-first placements keep the digit armed for repeated entry.
Successful number placements give a brief accent pulse and digit-settling animation,
without blocking input. This feedback is skipped when Android disables animations.

- **Notes** changes square taps into manual pencil-note toggles.
- **Auto notes** resets all annotations to legal candidates when turned on.
  To reset manual changes, switch it off and on again. Switching it off keeps
  the current notes, so you can continue working manually.
  Its preference is saved separately for Easy, Medium and Hard; all are available in
  Settings. Existing saved games seed their own difficulty's preference without
  resetting manual notes; other difficulties inherit the previous global default.
- Peer constraints always hide impossible notes without deleting your decisions.
  Replacing, clearing, or undoing an entry restores exactly the previously kept
  candidates that become possible, including the entry square's own notes.
  Multiple simultaneous blockers must all be removed before a note reappears.
- **Show mistakes** compares entries with the unique solution. Off by default;
  no penalties. Its setting is preserved with the game.
- Duplicate numbers in a row, column, or box are always blocked, independently
  of Show mistakes. The attempted square briefly flashes red; the rejected move
  leaves numbers, notes, undo history, counts, and the armed digit unchanged.
  Locally legal values are not rejected just for differing from the solution.
- **Undo** restores placements, erasures, manual notes, and auto-note resets.

The bottom controls contain only accessible Undo/Erase icon buttons, Notes, and the nine digits with
their remaining counts; instructional and input-status text is omitted.
Undo, Erase, and Notes have consistent 16dp internal padding and 56dp-high touch
targets, with extra width for the Notes label. Erase uses an eraser icon and
removes only the selected editable cell's input; it never deletes the game.
Difficulty and elapsed time share a centered header, without a filled-cell count.
Custom buttons consistently use flat rounded surfaces with native ripple feedback;
accent outlines separate secondary actions from the surrounding canvas, with
stronger selected outlines and muted completed-digit surfaces. Native elevation
animators are disabled to avoid clipped, inconsistent shadows.

## Puzzle generation and difficulty

Every generated puzzle starts with a randomized backtracking solution.
To form a common candidate pool, one shuffled greedy pass tries removing each of the 81 givens, keeping a removal
only when a separate solution-counting search establishes exactly one solution.
This common candidate pool has no difficulty filter or clue-count target.
The requested tier is selected by rejection sampling from that pool, using these
fixed-point human-technique classification rules:

| Mode | Classification contract |
|---|---|
| Easy | Solved using hidden singles alone: for a digit, exactly one legal empty square remains in a row, column or 3x3 box, after excluding all filled peers. Repeat full number-by-number passes until solved or a pass places nothing. |
| Medium | Hidden singles alone stall. Restart from the original givens and repeatedly apply both hidden singles and naked singles (a square with only one legal candidate). These two rules together solve the puzzle. |
| Hard | Both rules together reach a fixed point with empty squares remaining. The puzzle still has exactly one solution, independently established by search. |

Candidates are recomputed from filled peers only. No locked candidates, subsets,
X-wings, guessing, arbitrary hidden-single step limits or clue-count thresholds
participate in these ratings. A pass processes digits 1-9 across all rows,
columns and boxes, placing immediately; the combined solver then scans all
cells for naked singles. Both repeat through a terminal no-change pass.
Hard no longer promises completion by a particular advanced logical solver:
it means precisely that these two simple rules do not finish.

These are operational rules, not universal human difficulty ratings.
Fresh **generated Easy** puzzles have a separate gentleness policy: after finding
a hidden-only-solvable candidate, restore randomly selected correct givens to a
random target of **36-40 clues**. Accept only if the resulting puzzle still rates
Easy and has **at least eight distinct hidden-single cells available immediately**
in its original givens, without first making any placements. A cell forced in
multiple houses counts once. Restoring correct givens preserves uniqueness; the
hidden-only fixed-point solver certifies the final puzzle again. This deliberately
adds scanning opportunities instead of serving a sparse irreducible Easy.
Medium, Hard and the unfiltered common candidate pool are unchanged.

Generation remains bounded at 180 candidates, cancellable and off the UI thread;
failure reports a retryable error rather than substituting another tier.
Legacy `FOCUSED`/`MEDIUM`/`HARD` save IDs and saved games are unchanged.
The gentleness policy only affects newly generated Easy puzzles, not the current
game. Existing imported active puzzles retain their exact givens and saved tier.

### Generated-Easy calibration

The pre-change generator stripped every removable given even from Easy puzzles:
hidden-only solvability did not ensure easy human scanning. A fixed, paired sample
of 100 requested Easy games, seeds **2026111600-2026111699**, measured:

| Metric | Before: range (mean) | Gentler generation: range (mean) |
|---|---|---|
| Starting clues | 21-27 (24.37) | 36-40 (37.75) |
| Immediate hidden-single cells | 3-11 (5.67) | 9-26 (16.19) |
| Hidden-only solver passes | 5-12 (7.56) | 2-5 (3.42) |

All 100 pairs retain the same solution and every original given. Every puzzle
has an independent uniqueness proof, independently replayed solving trace and
matching snapshot-wave fixed point. Twelve fixed-seed outputs each from the
common pool, Medium and Hard are byte-identical before/after. Opening counts
exclude cascades; solver passes include the terminal no-change pass and depend
on scan order. These are measurable scanning proxies, not a human usability
study or a natural difficulty distribution. The after run spent 2.318 seconds
generating 100 games on this computer; this is not a phone performance claim.

Run `.\gradlew.bat --no-daemon easyGenerationBatch` to reproduce the current
100-game sample in `dist\easy-generation-20261116\after`. The archived `before`
sample was captured before changing production code; `-PeasyBatchStage=before`
only changes the output label, not the algorithm. The directory includes
`puzzles.csv`, `traces.csv`, `controls.csv` and `summary.json` for each stage,
plus `comparison.json`, the original generator source in `baseline-source`,
and `verification.log` from the implementation run.

### Reproducible 100-puzzle experiment

Run `.\gradlew.bat --no-daemon classificationBatch` to write
`dist\puzzle-classification-20261111\puzzles.csv`, `traces.csv` and `summary.json`.
The fixed sample uses one `java.util.Random(seed)` per candidate, seeds
**2026111100-2026111199**, with the common removal process above: no tier
requests, quotas, duplicate replacement or tuning after viewing the split.
All 100 exact-givens SHA-256 identities are distinct. These are greedy
irreducible puzzles (no retained given can be removed alone), not uniformly
sampled Sudoku puzzles or globally minimum-clue puzzles.

| Classification | Count / percent | Starting clues | Hidden placements | Naked placements |
|---|---|---|---|---|
| Easy | 21 / 21% | 23-26 | 55-58 | 0 |
| Medium | 23 / 23% | 23-27 | 32-47 | 8-25 |
| Hard | 56 / 56% | 22-26 | 1-29 | 0-13 |

Placement ranges use the hidden-only trace for Easy and the combined trace
for Medium/Hard. Hard leaves **23-57 empty squares**; Easy and Medium leave zero.
Pass counts, including the terminal no-change pass, are respectively 5-13,
4-11 and 2-6. The initial local run took **1.307 seconds** including generation,
classification, independent verification and CSV writing; timing is
machine/JIT-dependent and not a phone performance claim. The split describes
only this sampler and seed range, not natural prevalence.

`puzzles.csv` contains index, seed, identity, all 81 original givens (`0` means
empty), category, clue count, uniqueness result, both solvers' placement/pass/
residual counts and timings. Treat givens as strings when opening CSV in a
spreadsheet so leading zeros are preserved. `traces.csv` records every placement
in both stages. Cell indices are zero-based row-major; houses 0-8 are rows,
9-17 columns, 18-26 boxes; house `-1` denotes a naked single.
Each trace is independently replayed using separate peer arithmetic, and a
snapshot-wave solver (rather than immediate placement) must reach the same
fixed point. A second independent search proves uniqueness for every puzzle.
The batch fails on a duplicate, disagreement or invalid inference rather than
quietly dropping/replacing a puzzle. Results stay outside the APK.

## Persistence and interface

The current puzzle, solution, entries, underlying note decisions, toggles, selected
cell, armed digit, play time, and last 500 undoable actions are saved atomically in
app-private storage. Rotation and activity/process recreation restore the game.
Starting a replacement game asks for confirmation; only one current game is kept.
Uninstalling or clearing app storage removes it.

Time runs only during play, pausing in the menu, settings, and background.
A solved board celebrates with animated digits and confetti, followed by elapsed
time, saved difficulty, and **Done**, which returns to the main menu.
Classification records how the puzzle felt: Easy, Medium or Hard. It never
changes the game's offered difficulty, timer or personal-best category.
The system's
reduced-motion preference skips the animation. Resume is only offered for unfinished
games; the menu offers simply **Easy**, **Medium** and **Hard**, without difficulty explanations,
help or past-result entries, alongside Settings.
Each difficulty shows its personal-best completed solve time when one exists.
Only a correctly completed puzzle can set a record, using active play time;
slower solves do not replace it. Records survive new games and settings changes.
An existing completed saved game can seed its difficulty's record.
Settings uses a borderless gear icon; gameplay returns to the menu with a
borderless three-line icon. Both retain accessible labels and touch feedback.
Settings closes with a borderless X icon; per-difficulty switches are labeled
**Auto notes: Easy**, **Auto notes: Medium** and **Auto notes: Hard**.
Legacy `FOCUSED` (Easy) and `HARD` save/preference identifiers remain unchanged.
Medium uses `MEDIUM` and inherits the old global auto-notes default on first use;
existing per-difficulty preferences, game state and best times are preserved.

Settings offers a saved **System / Light / Dark** appearance choice, independently
of the accent color. System is the default and follows device changes; explicit
Light/Dark affects only Sudoku, including dialogs. Changing appearance preserves
the current game and returns to Settings after recreation.
Portrait puts the board above the controls;
landscape puts the board alongside them, with the bank below the controls. Both
layouts account for system bars and cutouts. The board exposes 81 individually
described accessibility nodes, including row, column, value, fixed-clue status,
visible notes, and optional mistake status. A physical keyboard can navigate with
arrows and enter digits; it is not required.

## Generated-only Sudoku

Every new Sudoku uses the local generator and its verified Easy/Medium/Hard
acceptance contract. There is no curated-first selection, post-win classification
or collection import/export. Completion feedback and personal bests remain.

Current SUD2/SUD3 saves remain readable, including previously imported active
puzzles, with their exact givens, saved tier, notes and Undo history. Existing
`puzzle-library.bin` data and its recovery files are left on disk, unused; the app
does not read, write, delete or migrate them.

## Build

Requirements: Java 17, Android SDK platform 36, build-tools 36.0.0. The installed
app supports Android 10 (API 29) and newer. This project uses Java and native
Android Views, AGP 8.13.0, and a checked-in official Gradle 8.13 wrapper whose
distribution is SHA-256 pinned. It has no third-party app dependencies.
Build-time dependency artifacts are SHA-256 pinned in
`gradle\verification-metadata.xml`; Gradle verifies them in strict mode.
See `SECURITY.md` before updating dependencies or adding automation.
The verification baseline was produced on Windows. If another platform needs
an unlisted native build-tool artifact, review its upstream origin and checksum
before adding it; do not disable verification.

In PowerShell, set Java 17 and your Android SDK paths, for example:

```powershell
$env:JAVA_HOME = 'C:\Path\To\Java17'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
.\gradlew.bat --no-daemon engineTest :app:assembleDebug :app:lintDebug
```

In Android Studio, choose your installed Java 17 as the **Gradle JDK**
(Settings > Build, Execution, Deployment > Build Tools > Gradle); the Studio
bundled Java 25 is newer than this pinned Gradle version supports.
`local.properties` contains this machine's SDK path and is ignored by git.

Development APK: `app\build\outputs\apk\debug\app-debug.apk`.
This is a debug-signed development APK, not a Play Store release.
It retains the production application ID. Do not install it over an existing
phone app to test changes without explicit authorization and a save-preservation
plan; a compatible debug installation could be replaced. Building does not
install anything. Release version/signing configuration is unchanged by this feature.

### Persistence and PR verification

All game writers share an observable atomic commit boundary: synced staging and
recovery files, atomic replacement and directory sync must succeed before an edit,
replacement or win is published. Best times update only after the game save succeeds;
failed preference commits restore the previous in-memory record and report an error.
A failed game commit reports an error and
attempts to restore the last committed bytes; a failed rollback is retained in the
logged exception rather than hidden. Legacy `.bak` recovery and save formats stay
readable. Confirmed generated-mode replacement choices survive recreation until
successful commit or explicit cancellation. External Settings hosts do not open,
migrate or write unrelated Sudoku progress.
Existing saves are copied to recovery storage with an 8 KiB buffer, not loaded
into memory during replacement. Even an oversized rejected file can be explicitly
replaced while its exact prior bytes remain available for rollback.
After a successful replacement, the ignored `.new` staging path retains the
previous synced bytes until the next write. Retiring this backup by rename, rather
than deleting it before the final sync, permits rollback without allocating new
storage if that sync fails.

The six generated-mode and Daily Word clocks update their labels each second.
Time-only snapshots are checkpointed about every ten seconds on an ordered worker,
coalescing pending checkpoints. Edits, Undo, replacement, rollover and terminal
results use that same writer; lifecycle/focus boundaries checkpoint immediately,
without blocking input, pause or recreation on disk. Ordered reads load on a worker
behind submitted writes. A retained session preserves quick input intents across
configuration changes, applying each against the last durably acknowledged state;
failed saves cancel dependent pending input and expose a retry error. Closing an
activity cancels unsubmitted intents, not a write already admitted to storage.
Successful newer commits supersede
stale checkpoint errors. Abrupt process death can lose uncheckpointed elapsed time
or work still waiting on storage, never a falsely acknowledged move.

`engineTest` includes injected staging/final-sync/replacement/rollback failures
and controlled timer/transition ordering. `PersistenceDeviceTests` exercises native
writer failures/retry at replacement and final backup-retirement sync, confirmed
replacement recreation and Settings isolation;
it also deliberately stalls disk while exercising responsive input/recreation
and retained quick edits/Undo, and confirms replacement of a rejected 64 MiB save
with byte-exact rollback after an injected final-sync failure. A separate host
process replaces and restores that size of rejected save under a 32 MiB heap.
Run device cases only against the isolated validation package with a durable outer backup.
Compilation is not proof that these device cases executed.

The Android PR smoke workflow builds validation instrumentation and runs a
repeatable seeded smoke test on an isolated hosted emulator. It uses no signing
secrets, physical device or personal data. This complements, rather than replaces,
full native testing and protected release verification. A local workflow check
does not establish that hosted execution or branch protection has run.

## Public sideload releases

No app store is required: publish the release APK on a website or a repository's
release page so people can download and install it themselves. This does not add
internet access to the app. Android 10 or newer is required.

Create your private signing key once on Windows, then build:

```powershell
.\scripts\New-ReleaseSigning.ps1
.\gradlew.bat --no-daemon engineTest :app:assembleRelease :app:lintRelease
```

The signed, non-debuggable APK is
`app\build\outputs\apk\release\app-release.apk`.
Normal local release builds require the private signing properties and keystore;
they do not fall back to a debug key. A source recipient must create their own
key rather than receive the publisher's private key.

On Windows these are stored outside the project at
`%LOCALAPPDATA%\QuietGrid\Signing`. `SUDOKU_SIGNING_DIR` can override that path.
On other systems the default is `~/.quietgrid-signing`; create an equivalent
private `signing.properties` containing `storeFile`, `storePassword`, `keyAlias`,
and `keyPassword`, with the keystore in the same directory.

**Privately back up the entire signing directory and keep it secret.**
Future compatible updates require the same key, application ID, and an increased
`versionCode` in `app\build.gradle`. Losing the key prevents signing compatible
updates. A build signed by another publisher cannot update this publisher's app.
The public release uses a different certificate from the development APK and
cannot directly update an existing debug installation. Do not uninstall a
development installation just to test this release if it contains saves you need.

For a public source archive, include only source, resources, build scripts,
the Gradle wrapper, scripts, and documentation/license files. Do not ZIP the
whole working folder: `.gitignore` does not filter ZIP contents. Exclude
`.signing`, `local.properties`, `.gradle`, `.idea`, all build directories,
and private keys/passwords. Do not distribute the instrumentation test APK.

On the downloading device, open the APK and, if required, allow app installation
from that specific browser/file manager in Android's settings. Turn that
permission off afterward if desired. Do not bypass Play Protect or other
unsafe-app warnings. Publish a SHA-256 checksum alongside the APK.

### GitHub-hosted builds and owner approval

The **Android release** workflow is manually dispatched on `main`. Choose
`verify` (the default) to run JVM tests, release lint and a secret-free unsigned
release build. This mode does not request signing approval or publish anything.
It uses Windows hosted runners to match the reviewed dependency-checksum baseline.
The unsigned build command is explicitly:

```powershell
.\gradlew.bat --no-daemon --dependency-verification strict -PunsignedRelease=true engineTest :app:assembleRelease :app:lintRelease
```

Its output is `app\build\outputs\apk\release\app-release-unsigned.apk`, not a
distributable/installable release. Omitting that property still requires the
private local release key; it never falls back to debug signing.

To publish later, review/update `versionName` and increment `versionCode`, then
dispatch `release` on current protected `main` as **mikeliddle**. Inspect the exact
source commit and successful build before approving `android-release` in GitHub.
Only **mikeliddle** is a required release reviewer; admin bypass is disabled.
Self-review is deliberately allowed. The signing and publishing jobs both use
this environment; approve the later publishing job too if GitHub requests it.
No agent should approve either on your behalf.

The environment holds `ANDROID_KEYSTORE_BASE64`, `ANDROID_STORE_PASSWORD`,
`ANDROID_KEY_PASSWORD` and `ANDROID_KEY_ALIAS`. These were provisioned from the
existing private public-release key, not the debug key. Keep the protected local
backup: GitHub secrets are not a recoverable key backup. Never paste key material
or passwords into chat, issues, workflow inputs, or repository files.
The expected public signing-certificate SHA-256 is:

```text
5f5586f9864347814393fdfa2cc5f94ab9254c44a61742ab105c29760056f2e6
```

Signing uses no source checkout, project scripts or Gradle. Its Google
build-tools 36.0.0 Windows archive is pinned to SHA-256
`aa1095cb14d83e483818a748a2c06faaeb8e601561b06a356a119a1b2ca280d3`
(also checked against Google's repository metadata SHA-1
`f16ccffd34de8790dede813a6c7d8e2c11a27b50` when pinned).
Artifact ZIP digests and APK checksums are verified between jobs; the final
certificate, package/version, alignment and nondebuggable/no-permission manifest
are checked before publication. Publication creates `v<versionName>` at the exact
reviewed commit, with `liddle-puzzles-<version>.apk` and `SHA256SUMS.txt`;
it refuses existing tags. Workflow archives are named
`liddle-puzzles-unsigned-<source-sha>` and `liddle-puzzles-signed-<source-sha>`.
The signed archive contains exactly the branded APK and its matching checksum
file. Previously published filenames/tags are immutable, not renamed in place.
There is no arbitrary tag/ref input. Main changing while approvals are pending
fails closed: review and start a fresh run.

The owner can still change repository/environment protections. Main is
administrator-write-only (currently just mikeliddle), force-push/deletion is
blocked, and `v*` tags cannot be rewritten. A personal-repository API limitation
prevented a GitHub Actions bypass for tag-creation restrictions; current writer
access and the publishing environment gate provide that boundary instead.
See `SECURITY.md` for the precise trust model and action allowlist policy.

## License and privacy

Original project content is MIT-licensed; see `LICENSE`.
Third-party licensing and icon attribution are in `THIRD_PARTY_NOTICES.md` and
`licenses\APACHE-2.0.txt`. These notices, the MIT license, and `PRIVACY.md` are
also embedded in every APK under `assets/legal`.

The app collects/transmits no data and has no network permission. It keeps game
state, best times, and preferences locally. See `PRIVACY.md` for the complete
statement. A download host's privacy policy applies separately to APK downloads.

```powershell
$adb = "$env:ANDROID_HOME\platform-tools\adb.exe"
& $adb -d install -r .\app\build\outputs\apk\debug\app-debug.apk
& $adb -d shell am start -W -n fyi.mllab.puzzle/com.quietgrid.sudoku.MainActivity
```

## Verification

`engineTest` is a dependency-free JVM suite covering note provenance, overlapping
blockers, replacement/clearing/undo, manual edits and reset, immutable clues,
remaining-count clamping, exhausted-digit disarming/re-enabling, armed input and
notes-mode transitions, persistence/corrupt saves/history bounds,
and 36 reproducibly seeded unique puzzles across all three difficulty contracts.
Compatibility tests cover old SUD2/SUD3 saves and previously imported active
puzzles without depending on a library or reclassifying their saved difficulty.

Device tests exercise the real native controls, digit switching/cancelling,
outside taps, given-cell behavior, note editing, exhausted digits, mode switching,
menu/resume timing, settings/colors, win flow, both layouts, rotation, recreation,
and generation on hardware. Swatch checks cover every accent-selection transition
in both appearances, including native drawable colors and rendered pixels.
Successful number entry pops from 65% to 135% size before settling to normal over
480 ms, with a bounded expanding accent outline. A move that newly completes a
valid row, column or box adds a brighter 900 ms wave with a held crest and a
single outline around the union of those houses; overlapping cells are
highlighted only once. Input is applied immediately and never waits for motion. Notes,
rejected/no-op input, loading, clearing and undo do not celebrate. A later real
recompletion can. The final grid move yields to the existing win animation.
Motion respects Android's Remove animations setting and is cancelled by new
input, hiding/pausing the board, changing games and detachment. Native motion
fixtures cover all seven house combinations, union pixels in both appearances,
input modes, interruption and disabled motion in the isolated validation app.
The entry-feedback fixture checks the disabled branch only when Android motion
is already off; it never changes the device's animation setting. For an explicit
real-touch recording with motion already enabled, run
`DeviceTests#testRealTouchMotionRecording`. It injects normal pointer input in both
entry modes and records timestamped real screen frames (without seeking the
animator) for entry and three-house completion. Screenshots are captured at 450 px
width; frame CSVs preserve their actual capture times.
Instrumentation targets the separate `fyi.mllab.puzzle.validation` package
by default, labeled **Liddle Puzzles validation**, never the player installation. Fixtures
replace only that validation installation's game and settings.
Generated-only device fixtures cover active-puzzle compatibility, untouched old
library bytes, absence of transfer/rating controls, and generation in every tier.
Each test normally
restores them in teardown, but a process crash can prevent that cleanup; keep a
durable backup if you choose to retain anything in the validation installation.
Do not interact with it while tests are running.

Daily Word's JVM suite checks the exact immutable asset hashes, date mapping before
and after the epoch, every cycle assignment, local midnight, leap day, DST gaps and
repeated hours, time-zone agreement, all 1,024 binary-alphabet duplicate-score pairs,
invalid guesses, wins/losses, six-guess cap, draft/record persistence, date revisits
and corrupt-save rejection. `DailyWordDeviceTests` exercises real pointer taps,
hub/mode isolation, validation, restart, non-color feedback/accessibility descriptions,
light/dark rendering, landscape, enlarged-text fitting/optional-panel scrolling, physical keyboard input,
loss/win and injected-clock rollover. It never
changes the system clock, time zone or animation settings. The daily save and its
atomic recovery artifacts are restored in teardown; a durable outer backup of
all validation `files` and `shared_prefs` is still required for crash recovery.

`PuzzleDeviceTests` covers all six new games through real pointer input: hub/help
navigation, edits, exact restart/Resume, discard confirmation and cancellation,
last-move completion, Minesweeper loss, corruption recovery and mode isolation.
It checks native light/dark layouts, landscape recreation and 1.6x in-process
font configuration, without writing any global device setting. Generate its
independently solved fixtures with `puzzleTest` first; copy the resulting
`dist/six-puzzles-20261117/fixtures/` contents to the validation app's external
`files/six-fixtures-20261117/` directory, then run that test class. These fixtures
are test artifacts, never bundled production levels. Back up validation private
files durably **before installation or testing** and restore/compare them in an
outer cleanup step even if instrumentation crashes; do not use the player package.

The 2026-11-18 focused Huawei validation is indexed at
`dist/centered-menus-20261117/final/verification.json`. Twenty distinct selected
native cases have passing final outcomes across the recorded runs; this is not
a claim that the entire historical instrumentation suite was run. Coverage
includes all eight fitted layouts (portrait/landscape and 2x in-process text),
menus/settings, actual drag/cancel/Undo, quiet clue errors, red stable Mines loss,
timers/completion, corrupted-save recovery and ordinary Sudoku input.
Completed runs restored validation private files byte-for-byte and verified the
regular player APK/data and global font/motion/accessibility/timezone unchanged.
An interrupted overnight run was separately recovered from its durable backup.

Nonogram generation sampled forty seeds at each of 5x5/8x8/10x10, all distinct
and independently uniquely solved. Three native generation samples per size
took at most 4ms in the final focused run; these observations are not latency
guarantees. Numberlink sampled forty seeds per actual pair count (39 distinct
three-pair, 40 distinct four-pair boards), all independently unique. Existing
bounded cancellation/search limits remain in force. The largest Nonogram also
round-trips all 500 real Undo steps within the save-size cap.

Representative fixture screenshots and actual sampled Mines motion are in the
same `final` folder. `mines-native-samples.gif` preserves roughly 0.5-second
screenshot intervals; it is not continuous video. Enlarged/dense boards fit by
scaling down, so neither the screenshots nor node/focus tests constitute
48dp-target, TalkBack or Switch Access certification.

New UI assertions use `runTestOnUiThread` so failures propagate to instrumentation
rather than crashing the app's main thread.

The optional `testSystemThemeChangesPreserveGame` check temporarily requests system light/dark mode and restores the original
setting. On devices that lock that system setting, tests explicitly report the
limitation and validate configuration-specific board rendering and contrast instead;
they do not claim a live system-theme transition occurred. The command below
excludes that check, leaving OS settings unchanged.

```powershell
.\gradlew.bat --no-daemon :app:assembleValidation :app:assembleValidationAndroidTest
& $adb -s <authorized-serial> install -r .\app\build\outputs\apk\validation\app-validation.apk
& $adb -s <authorized-serial> install -r .\app\build\outputs\apk\androidTest\validation\app-validation-androidTest.apk
$methods = [regex]::Matches((Get-Content .\app\src\androidTest\java\com\quietgrid\sudoku\DeviceTests.java -Raw), 'public void (test\w+)\(') |
    ForEach-Object { $_.Groups[1].Value } |
    Where-Object { $_ -ne 'testSystemThemeChangesPreserveGame' }
$tests = ($methods | ForEach-Object { "com.quietgrid.sudoku.DeviceTests#$_" }) -join ','
$result = & $adb -s <authorized-serial> shell am instrument -w -r -e class $tests fyi.mllab.puzzle.validation.test/android.test.InstrumentationTestRunner
$result
if (($result -join "`n") -notmatch "OK \($($methods.Count) tests?\)") {
    throw 'Native instrumentation did not explicitly pass every requested test.'
}
```

Do not treat adb's exit code alone as success: instrumentation can exit zero
while reporting failed assertions. Require the explicit `OK (N tests)` result.
Successful device tests save `sudoku-menu.png`, `sudoku-settings.png`,
`sudoku-portrait.png`, `sudoku-landscape.png`, and `sudoku-win.png` under the app's
external files directory. No emulator or USB-driver installation is necessary
for an already authorized USB-connected device. The verified device identified
itself as STK-LX3 running Android 10.
