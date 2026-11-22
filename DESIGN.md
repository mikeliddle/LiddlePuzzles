---
name: Liddle Puzzles
description: Quiet, puzzle-first native Android interaction
---

# Design system: Liddle Puzzles

## Overview

**Creative North Star: "Quiet, puzzle-first"**

The puzzle is the main event. Controls should be easy to find, predictable and
close to the thing they affect. Personality comes from the tiled white L,
precise geometry, readable color and purposeful feedback, not decorative chrome.
Keep the established Liddle Puzzles identity; this is a native offline app.

This is the accepted UX contract, not a claim that every existing screen already
meets it. The per-mode review below distinguishes current behavior from gaps.
Concrete palette and drawing values remain in the native source rather than a
second, speculative token library. Change this document when a user decision
changes; do not reinstate superseded behavior from old screenshots.

**Key characteristics:** puzzle dominance; grid-adjacent controls; familiar
icons; honest, non-color feedback; reversible edits; preserved progress.

See [README.md](README.md) for gameplay/build details, [PRIVACY.md](PRIVACY.md)
for local storage, and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for licenses.
AI-generated code and artwork remain disclosed in Settings and the README.

## Colors

- System, Light and Dark appearance are persistent choices, independent of the
  eight accents: Red, Orange, Yellow, Green, Blue, Pink, Purple and Brown.
- Use `MainActivity.Palette` roles for canvas, surface, ink, accent, selected tint,
  error and error fill. Domain colors may have distinct meanings: Takuzu cyan 0
  and amber 1; Daily Word correct/present/absent. Preserve their readable
  light/dark variants instead of indiscriminately recoloring them with the accent.
- **A swatch represents itself.** Its fill must not derive from the currently
  selected accent; only its selection outline changes. This prevents the former
  Orange-swatch regression.
- Target at least 4.5:1 for ordinary text and 3:1 for meaningful large text,
  state outlines and UI graphics where applicable. Check the actual foreground
  and surface pair, including dark warm accents and disabled/selected states.
- Color is never the only signal. Keep actual 0/1 digits, underlined givens,
  selected checks/outlines, Nonogram filled checks versus blank/cross, and
  Daily Word letters plus check/circle/dash and verbal scoring.

## Typography

Use native Android sans-serif and `sp` for scalable UI copy. Keep a clear
title/board/control hierarchy; large headings must not steal space from play.
Reuse existing helpers rather than inventing a new font or numeric type scale.

Normal gameplay must fit the whole puzzle and essential controls together.
Reflow first, then uniformly scale the measured composition when necessary,
including enlarged-font configurations; do not clip clues or inequality signs.
This explicitly trades away minimum target/text sizes on constrained displays,
as selected by the user. Spatial diagrams may need a dense representation; retain a
readable native alternative: Sudoku coordinates/value/notes and large number
controls, Daily Word readable guesses and letter controls, Slitherlink cell
clues and edge controls. These alternatives are optional, not required to see the
whole normal puzzle. Do not call scaled-down targets fully accessible.

## Layout

**The Cohesive Puzzle Rule.** Put related input and action buttons directly below
or beside their grid with modest, consistent spacing. The grid and controls
form one measured group. Do not pin a bank at the
screen bottom, add a weighted empty spacer, or stretch a board just to push its
keyboard down. This supersedes the earlier screen-bottom-bank preference.
Center each complete grid and its clues within its board region, including
Numberlink; centering only drawn ink inside a left-aligned native view is not
sufficient. Keep its essential controls adjacent as one visually balanced group.

Preserve Sudoku's single ordered 1-9 row and remaining counts; "below the grid"
does not authorize splitting it into a different compact keyboard. Daily Word
retains its three staggered rows. Landscape may place a board beside its control
group, with controls top-aligned rather than separated by empty height.
Gameplay titles balance the space occupied by corner actions so their center
stays on the inset-safe viewport axis. Keep the header to one measured line,
including enlarged type, rather than multiplying a whole toolbar's height by
font scale. Corner navigation uses the same inset and circular ripple across
gameplay, menus and settings; its 48dp target remains outside fitted play.
Menus begin a modest distance below their pinned heading. On wide screens the
hub's scrolling list has a centered, capped readable width.

Separate a board from its input/action group by 12dp (16dp for Futoshiki's
inequalities), and sibling actions by 8dp, before proportional fitting.
Sibling action rows share a 56dp logical height; grid cells keep their existing
geometry. Match ripple masks to the resting control shape. Align banks with
the full board/clue envelope. Nonogram row clues end toward the grid seam;
Takuzu's selection outline must not move its digit. Mines flag/count feedback
is one centered group with a stable numeric slot. Unsubmitted Word letters
are centered without an empty scoring-mark line; accepted marks stay unchanged.
Nonogram clues keep a quiet theme tint and solid accent outline while their
filled runs exactly match the public clue, even if other cells remain blank.
Feasible incomplete lines remain neutral; contradictions retain red dashed
outlines. This static state is restored without replaying decided-line motion.
Sudoku's two-line bank fits its digit and scaled remaining count together.
Word letters share one fitted text size based on the widest padded key, never
per-letter sizing. Resume/View menu actions share the same 56dp minimum height,
16dp top gap and outlined primary surface. A Word loss keeps its answer and
frozen time together in the existing fitted clock slot rather than replacing
the timer or inserting a new row.

**The Full Puzzle Rule.** The full board, every clue and all essential normal
gameplay controls must be visible together without scrolling or panning, in
portrait, landscape and enlarged text. Reduce peripheral chrome/gaps first;
use side-by-side layouts in landscape, then measured proportional fitting.
The user explicitly chose smaller controls over a required separate panel.
This supersedes earlier dense-grid panning, normal-play scrolling and universal
48dp mandates. Eight 48dp cells cannot fit a 360dp viewport: acknowledge the
physical limit, retain node/key alternatives and optional large controls, and
never claim accessibility certification from fit alone. Hub/settings/help and
separately opened alternatives may still scroll. Respect system bars/cutouts.

**The Stable Play Rule.** Ordinary selection, input, errors, Undo, timer ticks
and completion must not move or resize the board or controls. Use stable
measured slots already serving useful content, overlays, outlines or tints;
do not insert explanatory rows, reserve gratuitous empty status space, wrap
changing labels or let timer digit counts alter geometry. Optional panels open
separately instead of expanding normal play. Loading a different puzzle or
changing orientation/font configuration may recompute the layout.

The hub grows as a readable scrolling list, not a cramped collection of tiles.
Sudoku keeps concise centered difficulty plus active elapsed time. Remove routine
taglines, filled-cell counts and instructional chatter. Daily Word has no routine
help/date/instructions, solved-in-x/6 banner or midnight reminder. Meaningful
errors and loss answers remain. On-demand How to play is intentionally available
for the six less familiar games; this is not a universal ban on help.
All eight games now show concise elapsed time near the heading. The requested
timer is meaningful play information, not permission to restore removed Word
date, instructions or result-summary chatter. Keep its slot geometrically stable.

**Trust the player.** Use functional names and concise results, not obvious
instructions, slogans or explanations such as "New puzzle starts a fresh game."
Do not replace removed chatter with filler or a blank status row. Keep unfamiliar
rules in optional help; retain accessible action/state descriptions, genuine
failure notifications and explicit destructive-progress confirmations.

**Centered, icon-first controls.** Center ordinary headings, labels and status
text by default. Familiar actions use standard icons with full accessible names
and tooltips. A button has at most one visible word; numeric settings such as
8x8 or `10 mines` remain self-contained concise choices. Do not
abbreviate accessible names to meet this visual rule.
The main hub's puzzle-name buttons are a narrow title-label exception: show the
established full game name (including Daily Word) alongside its puzzle-specific
24dp vector icon. All eight use the same height, padding and rounded tonal
treatment; Sudoku is not a special primary action. Stable per-game hues reuse
the existing eight light/dark palette families independently of the selected
app accent; names and icons identify games without relying on color. Do not
shuffle hues on redraw, or carry these hub identities into gameplay colors.
Use selected fill/ink plus a border, native checked state or another non-color
cue for toggles, not changing On/Off button text. Keep geometry fixed.
Spatial puzzle clues, grid numbers, staggered QWERTY geometry and long structured
help retain their meaningful alignment. Dialog explanations may use sentences;
their action buttons still use concise single verbs. Do not replace specialized
choices with ambiguous invented icons.

**No button subtitles.** Do not add secondary descriptive text inside, beneath
or immediately beside a button, or move it outside the button as a workaround.
This applies throughout the hub, puzzle menus, settings and gameplay: no game
taglines, saved-game details, best-time captions or toggle-explanation footers.
Use a clear primary label or a genuine contextual section heading for a group,
not a per-button caption. Keep optional rules in help and concise full action,
setting and saved-state information in accessible names/tooltips where useful.
Actual screen headings, timers, puzzle clues, failure notifications and
destructive confirmations are not subtitles and must remain.

## Elevation & Depth

Custom controls are flat at rest. Use tonal surfaces, outlines, padding and native
ripple response, not gratuitous shadows or glow. Navigation icons are borderless.
Disable custom-button elevation animators where they create clipped or inconsistent
shadows. Native dialogs remain appropriate for destructive confirmation, optional
help and accessible alternatives; do not replace them with decorative custom modals.

## Shapes

Retain the near-black field and clean, separated white tiles of the L launcher
mark, including adaptive/round/monochrome compatibility. Reuse incumbent rounded
surface and outline helpers for controls; no new corner-radius system is implied.
Selected, focused, fixed, disabled and erroneous are different states, not
interchangeable uses of a colored border.

## Components

### Icons and buttons

Use original/native vector assets in the established family with consistent
optical weight. Do not use emoji or unlabeled mystery symbols. Give each icon an
action-specific accessible name and appropriate tooltip, a visible focus state,
and normally a meaningful hit region of at least 48dp. The Full Puzzle Rule
explicitly permits smaller normal-play targets when necessary; measure and
report that tradeoff. A 24dp drawing is not normally a 24dp target.
The existing Sudoku tools' 16dp padding and 56dp height are useful examples, not
an arbitrary rule for every component.

| Action | Visual convention | Semantic requirement |
| --- | --- | --- |
| Back | Arrow, borderless | Name the destination, e.g. Back to Liddle Puzzles. Preserve progress. |
| Close | X, borderless | Close this surface; do not imply deleting its data. |
| Menu / Settings | Hamburger / gear | Distinguish navigation from gameplay tools. |
| Undo | Curved-back arrow | Undo last real move; visibly and semantically disabled when empty. |
| Erase | Eraser icon | Remove input from the targeted cell/selection, not the whole board. Takuzu Erase arms repeated local erasure. |
| Clear / Reset | Explicit Clear or Reset text | Synonyms: remove all player inputs from the current board while preserving original givens/clues. Confirm destructive progress removal; do not generate a different puzzle. |
| Delete letter | Backspace icon | Delete draft input, not a submitted guess or saved day. |
| How to play | Help vector | Optional rules for the named new game, not a mandatory tutorial. |
| New puzzle / Retry / Notes / Enter | Keep explicit text when needed | Do not replace domain-dependent or destructive actions with ambiguous glyphs. |

Digits, letters, remaining counts and concise mode labels are essential input
content, not candidates for icon replacement. Icon-first does not mean icon-only
for every action.

**Clear and Reset mean the same whole-board action; Erase is local.** Use these
meanings consistently in visible labels, accessible names, tooltips and help.
The eraser requirement explicitly supersedes the earlier trash-can rule for
single-cell Erase. A local eraser must not be labeled Clear or Reset. These are
design requirements, not a claim that a whole-board Clear/Reset feature exists.

### Input, notes and Undo

**Mode menus follow Sudoku's submenu style.** Hub -> named mode menu ->
Resume/current-board view or clearly labeled new-puzzle choices. Use the same
readable scrolling column, Back, heading/settings affordance and native button
family; gameplay Back returns to that menu, then Back reaches the hub.
Replacing unfinished progress requires confirmation before generation; cancel
preserves the board/history. Menu time is not active play. Shared Settings closes
back to the invoking menu instead of stranding the user on Sudoku's hub.

Offer meaningful supported axes, not nominal Easy/Hard labels on the same board.
Current choices: Takuzu 6x6/8x8; Nonograms 5x5/8x8/10x10;
Numberlink only 6x6 with five pairs for new games (legacy 4x4 games still Resume);
Minesweeper 8x8 with ten/fourteen mines. More pairs change route constraints,
not a certified human difficulty rating; higher mine density does not promise
no guessing. Nonogram sizes are workload choices, not technique-rated difficulty;
every New puzzle is procedurally generated and exactly uniquely solvable under
the existing cancellable budget, not selected from a finite pack.
Slitherlink offers 4x4/6x6/8x8 size choices, with exact unique-loop generation
under the same budget, not certified human technique tiers. Legacy 4x4 bytes
remain compatible; larger definitions include their size, and compact checked
edge history preserves 500 Undo steps within the existing save cap.
Futoshiki retains its single certified 4x4 generation configuration.
Sudoku retains its established certified generated difficulties; new puzzles
never come from a curated library. Post-win voting and collection import/export
are removed. Existing active puzzles, including imported ones, resume unchanged;
old private library files remain untouched and unused. Daily Word stays
direct to its single daily/resumed game: no pointless selector or alternative
answer/difficulty. Persist choices for new puzzles; each saved board keeps its
own configuration independently, including legacy saves and Undo.

- **Sudoku:** digit-first input stays armed for repeated placements. Switching
  digits never overwrites the previously touched cell; the same digit or an
  outside tap cancels. Cell-first entry applies once and releases cell/house and
  armed selection. Notes changes mode without writing or dropping the armed digit.
  Exhausted digits are disabled and disarmed; Erase/Undo can make them available
  again. Remaining counts include givens, exclude notes and never go negative.

**Binary inputs use tap/hold.** Where a genuine default/secondary input pair
exists, a short press invokes the default and press-and-hold invokes the
secondary. A recognized hold consumes release: never perform both operations.
Cancel pending input on movement out of the target, multitouch, focus loss,
detach or lifecycle interruption. An already committed hold is a real edit with
the mode's normal save/Undo semantics; cancellation does not silently undo it.
Keep explicit tools and keyboard/accessibility actions so holding is never the
only route. The global Gameplay hold-delay slider uses 20-100% of Android's
touch-and-hold timeout, defaulting to 50% (500ms becomes 250ms).
The same percentage applies to longer system delays; 100% restores Android timing.
The native slider retains keyboard and accessibility range actions. This is local to these gameplay
gestures, not a device setting or tooltip change. Keep no sound/vibration and no new caption
or layout change to teach the gesture. Do not invent holds for unrelated actions.

| Mode/context | Default short press | Secondary hold | Explicit alternative |
| --- | --- | --- | --- |
| Minesweeper | Reveal (or use an explicitly armed Flag tool) | Toggle flag, never reveal on release | Flag tool; named native long-click action |
| Takuzu, no tool | Enter 0; repeat 0 to clear | Enter 1; repeat 1 to clear | 0/1/Erase tools; named native long-click action |
| Nonograms | Fill; repeat fill to clear | Cross empty; repeat cross to clear | Named native long-click action, including native keyboard long-click |
| Slitherlink | Line; repeat line to clear | Cross out; repeat cross to clear | Named virtual-edge actions; native edge selector with Line/Cross/Cycle |
| Sudoku, digit selected | Enter the selected digit, respecting the explicit Notes mode | Toggle that digit's note without changing the persistent Notes mode | Notes tool; named virtual-square long-click action and large controls |

Settings Back pops the page actually entered: global Settings -> game settings
returns to global Settings; a game/menu -> its settings returns directly to that
same game/menu. Toolbar and Android Back agree, including after recreation.
Opening settings does not disarm the Sudoku digit or discard board selection.

Mines reveal feedback interpolates only newly revealed squares' opaque fill from
covered to revealed, with a short bounded flood stagger; never fade the whole
button/text to translucency. Grid ripples stay clipped to their own squares.
Flags/loss retain local pulses. Unchanged squares and restored boards never
replay reveal motion, and interruption settles the final fill immediately.

Tapping a revealed Minesweeper number chords its eight bounded neighbors only
when the adjacent flag count equals that number. Reveal every covered unflagged
neighbor through the ordinary reveal/flood/result path; flags remain player
assertions, so incorrect flags can cause a normal loss. A hold, canceled gesture,
multitouch, mismatched flag count or terminal board never chords. Native click,
keyboard and accessibility activation use the same path, even with the Flag
tool selected (that tool applies to covered squares). Chording adds no
layout-changing feedback or idle explanatory text.

- **Takuzu:** selecting 0/1/Erase arms repeated entry/erasure.
  Switching tools only changes the tool; the same tool or an outside-grid/bank
  tap returns to default tap 0 / hold 1. A hold still enters/clears 1 with a tool
  selected. Do not leave a stale cell-first target. Fixed
  underlined givens remain immutable. Expose current mode and tool selection.
- **Futoshiki:** select a square, then enter a bank number or use Erase to remove
  that square's player entry. This local action is not Clear/Reset.
- **Numberlink:** drag from an endpoint or existing path, extending only through
  orthogonal free squares. Backtracking retracts the current route; starting
  inside an existing route preserves its endpoint prefix and replaces its tail.
  Release commits one logical Undo action; cancel, multitouch or lifecycle
  interruption discards the preview. Keep tap/native-key/node edge editing.
  Each numbered pair/path has a stable, distinct light/dark color, with actual
  endpoint numbers and spoken pair/connections retained. Dragging is not a
  universal requirement for other puzzle modes.
  The 6x6/five-pair setting has 36 cells instead of 16, no route shorter than
  four cells, no adjacent matching endpoints, at least twelve solution bends and
  ten aggregate detour steps beyond Manhattan distances. Exact uniqueness means
  these route constraints apply to its one actual solution, not a discarded
  generation sketch. Keep the 4x4 options and legacy saves intact; do not call
  geometric constraints certified human solving difficulty.
- **Notes preserve intent.** Temporary peer suppression must not destroy manual
  decisions. Local erasure, replacement and Undo restore only previously kept candidates
  that become legal again. Re-enabling Auto notes deliberately resets them;
  toggling an input mode is not permission to regenerate them.
- **Undo restores a logical action**, not a selection change, rejected move or
  same-value no-op. Sudoku keeps its existing history. Takuzu, Nonograms,
  Futoshiki, Numberlink and Slitherlink retain up to 500 isolated, atomically saved
  entry/edge snapshots, including Undo back out of completion. Legacy saves open
  without a reset. Do not generalize this into undoing Word submissions or
  Minesweeper reveals/losing information.

### Errors and feedback

**The Honest Feedback Rule.** An **error** is invalid input or a contradiction
of current visible rules; signify it in red with an unobtrusive non-color cue.
A **mistake** is legal input that differs from the hidden solution; it is not
an error. Never turn hidden-answer mismatch into automatic red rule feedback.
Explain only what the app has actually established. Accessible reasons need not
be visible prose: Nonograms and Slitherlink keep error detail in accessible descriptions, not status
messages, extra symbols or inline explanations. This does not suppress genuine
save/load/generation failure notifications or rewrite other games' feedback.

| Situation | Required feedback and behavior |
| --- | --- |
| Sudoku duplicate in a house | Reject independently of Show mistakes; brief red rejection and a localized accessible reason. No board, notes, counts, armed digit or history mutation. |
| Sudoku solution mismatch | Only Show mistakes checks the unique solution; default off. Locally legal alternatives are not rejected merely for differing from it. |
| Takuzu rule broken | Mark all involved triple, excess-count or duplicate completed-line participants with outline plus ! and coordinate/reason. Allow correction; never award an invalid full-board win. |
| Nonogram line impossible | Use red clue digits and a dashed red outline only when no legal run placement fits current fills AND explicit crosses. No visible error text, !, count or reason list. Keep the clue's coordinate/reason accessible to screen readers. Unknown stays unconstrained; legal-but-solution-wrong input is not an error. Do not consult a hidden picture or call an arbitrary cell wrong. |
| Futoshiki visible rule broken | Outline and ! mark all duplicate row/column participants and both entered ends of a broken inequality. Explain coordinates and the rule; blank squares alone are not errors. Keep selected-entry underlining distinct. |
| Slitherlink forced violation | Mark drawn branch participants, clue over-counts and closed components incompatible with other drawn lines or required clue sides. Use localized red lines/digits with subtle outline rings, not visible !, error counts or reason paragraphs. Keep reasons in Cell clues/Edge controls accessible descriptions. An unfinished path alone is not wrong; this is not a hidden-loop oracle or exhaustive dead-end detector. |
| Invalid Word submission | Explain short/unknown/repeated guess without consuming an attempt or exposing the answer. |
| Save/load/generation failure | Say what failed and how to retry; preserve previous data. Never report fake success, silently reset corruption, or substitute an unproven puzzle. |
| Completion / loss | Make the result unambiguous in that game's vocabulary; announce deliberately without duplicate redraw announcements or early answer spoilers. |
| Minesweeper loss | Keep board/cell/control geometry unchanged. Red mine cells, a red board outline and concise red Mine hit result distinguish loss; the detonated mine has a non-color *! and accessible reason. No win confetti. Do not explain how New puzzle works. |

Correction and Undo must immediately clear or restore relevant feedback.
Update polite live status only when its meaning changes; do not speak an entire
board on every redraw. Word announces each accepted row and its win/loss once.
Returning to a finished screen must not replay celebration as a new achievement.

### Daily Word keyboard lessons

Letter keys share width, height, typography and weight across all three staggered
QWERTY rows; do not independently stretch each row to fill the screen. Delete
stays at the middle row's right and Enter at the bottom row's right, with no
separate bottom action row. Hardware letters work without opening an IME or
autocorrect. Focused Enter activates its focused native control rather than
unconditionally submitting; hardware typing scopes submission back to the draft.

Score exact letter matches first, then remaining copies, so duplicates cannot
receive excess credit. Keep non-color feedback and per-letter/row descriptions.
The frozen offline schedule is based on the local calendar date: same date,
same word, not necessarily the same instant across time zones. Rollover and mode
switches preserve each date's draft/results; this does not require a visible date.

### Motion and durable state

**Active-play timers.** Daily Word and the six generated games accumulate
monotonic elapsed play time only while resumed, focused and playable. Opening,
generation, background/screen-off and unfocused modal time do not count.
Help, size/replacement dialogs and Word's large-controls dialog pause that clock;
closing them resumes the same puzzle. Win and loss freeze it. Undo out of a
completed editable game resumes the accumulated total, excluding completed idle
time. Only a successfully saved new puzzle resets its timer.

Persist elapsed with the board/history or Word date, never a running timestamp.
Date rollover starts/resumes that date's own record; revisiting a completed date
does not restart it. Legacy saves with unknown elapsed begin at zero without
inventing history or replacing progress. Update clock labels each second; checkpoint
time-only snapshots about every ten seconds on an ordered, coalescing worker and
immediately at edits/focus/lifecycle boundaries. Input, navigation, pause and
recreation never wait for disk. Reads run behind submitted writes on a worker;
retained input intents advance only after each durable acknowledgement.
Failed saves cancel dependent pending input and expose retry errors.
Abrupt termination may lose uncheckpointed or still-queued elapsed time.
Clock updates touch only the timer label/description, not the board,
keyboard, scroll or focus. No live-region announcement every second. Sudoku's
established timer and record behavior remain unchanged by this extension.

All games use an observable synced atomic commit before acknowledging an edit,
replacement or completion. Best times update only after that save; failed record
preference commits restore the previous in-memory record and expose an error.
Failed writes retain the last committed board,
stop success feedback and expose retryable errors; rollback failures are logged
explicitly with the original failure. Older timer snapshots cannot overwrite an
edit, Undo, replacement, terminal result or Word rollover. A confirmed generated
replacement keeps its requested setting across recreation until commit or explicit
cancellation. Settings-only hosts never restore/migrate/save unrelated Sudoku.

Respond immediately, then add perceptible weight and settle without blocking
input. Current accepted Sudoku tuning is a 65% -> 135% -> 100% digit settle over
about 480ms and a brighter coordinated completed-house wave over about 900ms.
These are current tunings, not immutable global durations. Animate one union of
newly completed row/column/box cells, not overlapping effects painted repeatedly.
Coordinate the final win. Rejected/no-op input, loading, Erase, Clear/Reset and Undo do not
trigger successful-entry celebration.

For Daily Word and the six newer games, reuse the short Sudoku confetti cascade
without Sudoku-specific digits. Fire once after a successful saved move changes
an active board to a valid win, over the already-visible completed board/result.
Do not wait for animation before saving or showing the result. A genuine
Undo/recompletion may celebrate again; loading, rotation, terminal redraw,
rejected/no-op input and Minesweeper/Word loss never celebrate. Cancel the overlay
on navigation, focus loss or lifecycle pause. The steady result remains visible
when motion is disabled. Do not stack another win effect over this cascade.

The other games acknowledge saved edits locally; marks settle over about 480ms
and visible-rule milestones sweep over about 900ms. These are transient paint,
not new success claims or a hidden-answer oracle:

- **Takuzu:** bounded digit/erase outlines; coordinated row/column wave only
  when the full line obeys balance, triples and uniqueness, without intersecting
  visible contradictions.
- **Nonograms:** fill/cross/erase outlines; line wave only when every square is
  explicitly decided, runs match, and intersecting clues remain feasible.
  Blank/undecided cells are not silently certified empty.
- **Futoshiki:** entry/erase outlines; full distinct-number row/column wave
  only without intersecting duplicate/entered-inequality errors.
- **Numberlink:** finger tracking stays exact, with no interpolation or delay.
  After commit, edited edges settle and a newly connected matching pair receives
  a same-color route pulse. A connected pair does not certify full coverage.
- **Slitherlink:** local line/cross/erase feedback; newly met visible clue counts
  receive a bounded clue ring only without current rule errors. Open paths are
  not celebrated as completed loops.
- **Daily Word:** typed letters settle within their tiles, deletion contracts an
  outline, and an accepted guess receives one staggered row settle. Scoring,
  keyboard state, focus and spoken result update immediately; keyboard repaint
  and rejected submission never replay it.

Minesweeper retains opaque in-cell reveal/flood fill transitions, flag outlines and a
restrained red mine-hit/reveal cascade. Text/cells never fade as a whole; pulse
only an inset paint outline, keeping the red loss state steady. No board
shake, layout shift, translated/scaled grid or delayed terminal state. Save the
real move/result and freeze its timer first. Interrupt cleanly on another move,
focus loss or lifecycle pause; reduced motion shows the same steady result.
Loading/resuming a loss never replays these effects. No sound or vibration is implied.
Covered cells retain a distinct tonal fill; revealed safe cells use the quiet
background fill. Flags, revealed numbers/dots and accessible state distinguish
them without color alone. Red loss takes precedence over either ordinary fill.

Respect Android's reduced/disabled animation preference. Interrupt cleanly and
cancel on navigation/lifecycle changes, with no stale pulse or highlight.
Judge perceptibility through actual real-time touch, not isolated nonzero pixels.
Never alter system motion settings for routine validation; cached animation-scale
behavior on older Android is not permission to change the user's preference.

Keep persistent preferences distinct from transient selection and animation.
Updates and switching games preserve saves, settings, records and per-mode history.
Confirm replacing active progress, including whole-board Clear/Reset. Confirmation
must identify that all player inputs will be removed and original givens/clues
retained; cancel leaves progress unchanged. Clear/Reset is not New puzzle and must
not imply erasing other modes, settings or records. A new size cannot change the
saved board before confirmed, successfully saved replacement. A layout or branding change must not silently change puzzle labels,
generation guarantees, frozen schedules or data formats.

### Current per-mode review

The permanent installed identity is `fyi.mllab.puzzle` (validation suffix
`.validation`). Keep visible Liddle Puzzles branding and existing save formats;
the different old `com.quietgrid.sudoku` app does not receive this as an update
or transfer its private state automatically. Do not imply automatic migration.

Navigation belongs to fixed screen chrome, never the centered menu body or the
scaled gameplay composition: Back stays at the top left and Settings at the top
right within system/cutout insets, with native 48dp targets. Settings and help
dialogs retain their platform dialog actions. Centering applies to content, not
navigation placement. Configuration choices reuse Sudoku's 14sp, 56dp-minimum
rounded surface/outline style; selected choices add fill and a stronger outline.

Settings groups global appearance/colors separately from eight named game
entries. Sudoku owns Auto notes by difficulty and Mistakes, retaining existing
active-game behavior and preferences; Auto notes uses spaced, filled native
switch rows with unrestricted accessible names/tooltips. Other mode entries
reuse the exact submenu choices and preference keys for future games only,
without replacing saves. Daily Word displays its fixed five-letter/six-guess
configuration; Futoshiki and Numberlink show their existing single size without
invented toggles. Settings ends with the disclosure, without collection transfer
controls. No subtitles or decorative explanations.

This is an implementation review, not TalkBack or Switch Access certification.
Native touch, key/focus/node actions and geometry checks complement source
inspection; full assistive-technology traversal remains unverified.

| Mode | Current conformance | Concrete gaps / limits |
| --- | --- | --- |
| Sudoku | Full-fit grid/tools/ordered bank; selected digit tap-entry/hold-note; Back/menu/Undo and actual eraser vectors; exact notes/history; distinct duplicate rejection; motion and optional large controls. | Fitting can reduce targets and effective text size below accessible recommendations; full screen-reader traversal is unverified. |
| Daily Word | Full-fit grid-adjacent equal-key keyboard; right-side actions; quiet chrome; duplicate-aware non-color scoring; focused Enter; feedback shares the timer slot without reflow. | Compact letter targets remain narrow; optional large controls retained. Effective enlarged text can be reduced to fit. |
| Takuzu | Full-fit tap 0 / hold 1 default, repeat-to-clear and explicit digit/eraser tools; adjacent bank/Undo; 6x6/8x8; underline/digits/error reasons; valid completion in stable heading. | 8x8/enlarged layouts intentionally scale down rather than pan; not a 48dp guarantee. Full nonvisual traversal is unverified. |
| Nonograms | Centered full-fit board and measured clues; submenu sizes 5x5/8x8/10x10 with procedural unique generation; distinct filled/cross/unknown; quiet red/dashed clue errors; Undo, timer, completion and focusable reasons; legacy 5x5 saves retained. | Larger boards scale targets and effective text down. Sizes are not certified technique ratings. Line errors do not identify a particular wrong cell or every global impossibility; hidden-solution mistakes are not checked. |
| Futoshiki | Number/eraser bank separated from the complete inequality grid by a fixed 16dp composition gap (below in portrait, beside in landscape), scaled with the full-fit layout; underlined selected target; immutable givens; measured scalable inequality gutters; duplicate/inequality reasons and ! cues; persistent Undo and prominent completion. | Feedback checks entered duplicates and inequalities, not every globally unsatisfiable partial square. |
| Minesweeper | Full-fit reveal/hold-flag plus adjacent explicit flag toggle/count and icon New; covered/revealed fills differ; submenu offers ten/fourteen mines; first neighborhood safe; density persists; red/non-color loss is fixed in place; paint-only reveal/flag/loss motion; no information-reversing Undo. | Later guesses may be necessary at either density. Selected fill/outline and accessible state identify Flag versus Reveal; disabled motion leaves the same steady result. |
| Numberlink | Centered full-fit 6x6 new puzzles with five pairs, real bends/detours and unique full coverage; legacy 4x4 three/four-pair saves/history still Resume; stable colors/numbers; drag preview/backtracking/cancel and one-gesture Undo; tap/key/node editing; no neighboring-square idle instruction. | User chose 6x6 only for now after bounded larger-generation experiments failed reliability; do not offer 4x4 new games, nominal 8x8/10x10 tiers or weakened uniqueness. Current route constraints are not certified human difficulty. No global dead-end oracle; colors do not replace labels/spoken connections. |
| Slitherlink | 4x4/6x6/8x8 unique procedural sizes; full-fit board/actions; numbered AND blank clues; optional clue/edge dialogs with complete size-correct coordinates; quiet localized red/outline violation cues with accessible reasons; 500-step persistent Undo and stable completion. | Early feedback covers named forced violations, not every impossible partial loop. Larger sizes are workload choices, not technique ratings. Direct edge targets may be small; optional large alternatives remain. |

Across every row, hub and submenu buttons have no subtitles; Resume/View keeps
saved details accessible only. Sudoku difficulty choices have no best-time
captions; settings toggles have no explanatory footers. Numberlink uses
`6x6` for new games, Minesweeper `10 mines`/`14 mines`, and size choices stand alone.

Review additions to every mode against the no-subtitle rule: inspect buttons
and their siblings for relocated descriptions, confirm choices remain distinct
and self-explanatory, and preserve full accessible action/state information.

All eight normal-play compositions fit without scrolling. Landscape puts
peripheral heading/actions beside the board where practical. Headings auto-fit
their stable slots rather than wrapping after an action. Large alternatives use named Close
icons and leading action vectors while retaining readable text where useful.
Proportional fitting is an explicit readability/target-size compromise, not a
claim that every cell remains 48dp at every font size.
No whole-board Clear/Reset feature is implied or introduced. Do not erase
meaningful I/O failure notifications in the name of quietness.
Ordinary text is centered; familiar actions are icons, and buttons have at most
one visible action word; hub game-title labels retain their established full
names alongside icons. No secondary button subtitles or relocated detail labels.
Notes and Flag use selected fill plus outline/state rather than changing On/Off
text. Full accessible names and tooltips remain unrestricted.
Daily Word and all six new modes now include durable active timers and
transition-only completion cascades. Their timing, terminal freeze and no-replay
behavior have focused JVM/native coverage; actual assistive-service traversal
remains unverified.

The 2026-11-18 selected native evidence is indexed in
`dist/centered-menus-20261117/final/verification.json`, with the latest passing
outcome for each of twenty focused cases across recorded runs, not an entire
historical-suite claim. It includes all eight viewport compositions and the
larger Nonogram clue bounds, single-word buttons/centered icons, stateful
toggles, real drag/cancellation, red fixed-geometry loss and timer/Undo behavior.
Private-state preservation is separately recorded for each completed run.
The tap/hold, covered/revealed fills and larger routed Numberlink follow-up has
eight passing selected native cases in
`dist/centered-menus-20261117/controls-binary2/preservation.json`, including
portrait/landscape and in-process 2x-text larger-board geometry. These remain
focused checks, not whole-suite or assistive-service certification.

Representative sources under `app/src/main/java/com/quietgrid/sudoku/`:
`MainActivity.java`, `BoardView.java`, `Game.java`, `DailyWordActivity.java`,
`DailyWordKeyboard.java`, `DailyWordBoard.java`, `LargeControlsDialog.java`,
`PuzzleActivity.java`, `NumberlinkBoard.java`, `SlitherlinkBoard.java`, and
`puzzles/Mode.java`, `puzzles/Nonogram.java`, `puzzles/PuzzleProgress.java`,
`puzzles/PuzzleStore.java`.
Icon assets live under `app/src/main/res/drawable/`.

## Do's and Don'ts

- **Do** treat current user decisions and the per-game input exceptions as binding.
- **Do** keep data-preserving failure paths visible and localized rule reasons accessible.
- **Don't** add decorative status, reset progress to demonstrate a feature, or
  equate a green screenshot with accessibility certification.
- **Don't** change system font, animation or accessibility settings for routine checks.

### Pre-merge UI checklist

- [ ] Full board, clues and normal controls fit without scrolling/panning; measure the real gap and exercise
      input in portrait, landscape and enlarged text, not just a screenshot.
- [ ] Familiar actions use named vectors; ambiguous/destructive actions retain
      clear semantics. Focus, selected, fixed, disabled and error states differ.
- [ ] Hit regions and contrast are measured. Reduce chrome/reflow, then fit
      proportionally; record smaller targets honestly and retain optional
      alternatives. No clipped clues or false accessibility claims.
- [ ] Selection/error/Undo/completion/timer changes do not move or resize play.
      No appearing status rows, wrapping mode labels or expanding normal panels.
- [ ] Complete board/clue bounds are centered within their region. Mode menus
      follow Sudoku style; Back/Resume/new-choice confirmation preserve state.
      Offered settings change real constraints, retain uniqueness/budgets where
      required and never relabel a fixed board as a different difficulty.
- [ ] Clear/Reset labels mean whole-board player-input removal with confirmation
      and preserved givens/clues; local Erase uses an eraser icon. Visible copy,
      accessible names, tooltips and help agree.
- [ ] Default, armed, cancel, rejected, no-op, Erase and Undo pathways preserve
      the documented per-game state/history rules.
- [ ] Contradiction cues use current visible constraints; optional solution
      mistakes and spoilers remain separate. Spoken feedback does not replay.
- [ ] Motion is perceptible, interruptible and respects system preferences.
- [ ] Timers exclude paused/terminal intervals, persist per puzzle/date and
      retain elapsed through Undo. Ticks do not redraw play or announce seconds.
      Genuine wins celebrate once; restored wins and losses do not.
- [ ] Resume, rotation, size/date changes, failure and compatible updates keep
      independent progress. Test fixtures use protected validation state only.
- [ ] Record remaining per-mode gaps and actual device/assistive-tech coverage;
      keep README, privacy and licensing references accurate.
