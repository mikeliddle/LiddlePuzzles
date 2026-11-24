# Working on Liddle Puzzles

- `README.md` is for players: keep it short, with the latest release link at
  the top. No setup, build/install instructions, wire formats, or test details.
- `DESIGN.md` owns UX contracts; [developer docs](docs/DEVELOPMENT.md) own
  implementation details and verification. Update them with related code changes.
  Distinguish published versions, branch behavior, and validation-only behavior.
- Keep the full puzzle and normal controls visible together. Do not reflow
  gameplay after a move. Preserve named native/virtual actions and available
  large alternatives. Same-target movement beyond touch slop cancels the hold,
  not the tap; leaving the target, multitouch or cancellation invalidates both.
  Intentional drag interactions are separate.
- Save the board and both history stacks atomically. Undo and Redo move a
  step between stacks, a fresh edit clears Redo, and a no-op does neither.
  Preserve prior saves, quota/record behavior and shared/normal isolation.
- Run relevant host tests, builds and lint; compile instrumentation separately
  from running it. Use [the device matrix](docs/DEVELOPMENT.md#device-matrix) for
  touch/accessibility changes; report only checks actually run and observed.
  Never mutate production device data or alter release signing/approval for testing.
