package com.quietgrid.sudoku;

import com.quietgrid.sudoku.puzzles.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Separate oracles: row-pattern search, Latin search, path exact-cover and planar region enumeration. */
public final class PuzzleTests {
    private static int checks;
    private static Path fixtures;
    private static final Set<Mode> recorded = java.util.EnumSet.noneOf(Mode.class);
    private static final Set<String> shapes = new HashSet<>();
    private PuzzleTests() {}
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception { batch(Path.of(args[0])); }
    static void run() throws Exception { batch(null); }
    private static void nonogramStrokes() throws Exception {
        NonogramStroke checkStroke = new NonogramStroke(5, .5, .5, .2, 250);
        checkStroke.sample(4.5, .5, 249);
        checkStroke.sample(.5, .5, 500);
        check(checkStroke.paint() == 1 && checkStroke.crossed.equals(java.util.BitSet.valueOf(new long[]{31})),
                "A fast drag paints the entire row once and keeps its pre-hold brush");

        NonogramStroke xStroke = new NonogramStroke(5, .5, .5, .2, 250);
        xStroke.sample(4.5, 4.5, 250);
        check(xStroke.paint() == 2 && xStroke.crossed.equals(java.util.BitSet.valueOf(new long[]{0x1041041})),
                "Hold-drag crosses only the diagonal interiors, not corner neighbors");
        xStroke.sample(4.5, -1, 500);
        check(xStroke.crossed.cardinality() == 9, "A gutter endpoint still paints the board crossed before exiting");

        NonogramStroke tap = new NonogramStroke(5, .5, .5, .2, 250);
        tap.sample(.8, .5, 100);
        tap.sample(.8, .5, 500);
        check(tap.tap() && tap.paint() == 0, "Early same-cell drift suppresses hold without stealing tap");
        NonogramStroke hold = new NonogramStroke(5, .5, .5, .2, 250);
        hold.sample(.5, .5, 500);
        check(hold.hold() && !hold.tap() && hold.paint() == 0, "A stationary hold remains a single secondary action");
        NonogramStroke leftGrid = new NonogramStroke(5, .5, .5, .2, 250);
        leftGrid.sample(-.5, .5, 100);
        leftGrid.sample(.5, .5, 500);
        check(!leftGrid.tap() && !leftGrid.hold() && leftGrid.paint() == 0, "Clue gutter drag-off is neither a tap nor paint");

        Nonogram initial = Nonogram.generate(new Random(117), new Generation());
        initial.values[1] = 2;
        initial.values[2] = 1;
        PuzzleProgress before = new PuzzleProgress(initial);
        Nonogram candidate = initial.copy();
        candidate.paint(checkStroke.crossed, checkStroke.paint());
        check(Arrays.equals(Arrays.copyOf(candidate.values, 5), new int[]{1, 2, 1, 1, 1}), "Brush passes through both existing marks");
        PuzzleProgress edited = PuzzleStore.decodeProgress(Mode.NONOGRAM, PuzzleStore.encode(before.edited(candidate)));
        check(edited.undoCount() == 1 && Arrays.equals(((Nonogram) edited.undo().game).values, initial.values)
                && Arrays.equals(((Nonogram) edited.undo().redo().game).values, candidate.values),
                "Saved stroke is one atomic undo/redo step");
        Nonogram next = candidate.copy();
        next.paint(xStroke.crossed, 2);
        PuzzleProgress undone = edited.edited(next).undo();
        Nonogram noOp = ((Nonogram) undone.game).copy();
        noOp.paint(checkStroke.crossed, 2);
        check(undone.edited(noOp) == undone && undone.canRedo(), "Dragging across only marked cells preserves Redo");
    }

    private static void batch(Path output) throws Exception {
        nonogramStrokes();
        fixtures = output == null ? null : output.resolve("fixtures");
        recorded.clear();
        if (fixtures != null) Files.createDirectories(fixtures);
        List<String> rows = new ArrayList<>();
        rows.add("mode,seed,id,solutions,generation_ms,save_base64");
        long start = System.nanoTime();
        for (Mode mode : Mode.values()) {
            Set<String> definitions = new HashSet<>();
            shapes.clear();
            long total = 0, maximum = 0;
            for (int i = 0; i < 20; i++) {
                long seed = 2026111700L + i, before = System.nanoTime();
                Puzzle game = mode.generate(new Random(seed), new Generation());
                long elapsed = System.nanoTime() - before; total += elapsed; maximum = Math.max(maximum, elapsed);
                byte[] saved = PuzzleStore.encode(game);
                if (fixtures != null && i == 0) Files.write(fixtures.resolve(mode + "-initial.bin"), saved);
                check(Arrays.equals(saved, PuzzleStore.encode(PuzzleStore.decode(mode, saved))), "Exact initial save roundtrip");
                check(Arrays.equals(saved, PuzzleStore.encode(game.copy())), "Deep copy preserves definition/state");
                String id = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(saved));
                check(definitions.add(id), "Batch has distinct original definitions, not a reused level");
                check(game.mode() == mode && !game.solved() && !game.lost(), "Fresh playable state");
                verify(game);
                for (Mode other : Mode.values()) if (other != mode) reject(other, saved);
                byte[] corrupt = saved.clone(); corrupt[corrupt.length / 2] ^= 16; reject(mode, corrupt);
                reject(mode, Arrays.copyOf(saved, saved.length - 1));
                byte[] appended = Arrays.copyOf(saved, saved.length + 1); reject(mode, appended);
                rows.add(mode + "," + seed + "," + id + "," + (mode == Mode.MINESWEEPER ? "NA" : "1")
                        + "," + elapsed / 1e6 + "," + Base64.getEncoder().encodeToString(saved));
            }

            check(shapes.size() >= 4, "Variety exceeds rotations/reflections/relabelings of a single template");
            System.out.printf("%s: 20 distinct games / %d non-symmetric shapes, generation %.1f ms mean / %.1f ms max%n",
                    mode, shapes.size(), total / 20e6, maximum / 1e6);
        }
        takuzuLegacyAndErrors();
        undoProgress();
        clearProgress();
        timedProgress();
        nonogramContradictions();
        binaryMarks();
        visibleMilestones();
        numberlinkDrags();
        puzzleSettings();
        visibleRuleErrors();
        edgesAndMines();
        boolean bounded = false;
        try { Mode.SLITHERLINK.generate(new Random(5), new Generation(0, 10000)); }
        catch (IllegalStateException ex) { bounded = true; }
        check(bounded, "Generation fails explicitly at its node limit");
        Thread.currentThread().interrupt();
        try {
            Mode.TAKUZU.generate(new Random(0), new Generation());
            throw new AssertionError("Cancellation ignored");
        } catch (java.util.concurrent.CancellationException expected) { checks++; }
        finally { Thread.interrupted(); }
        if (output != null) {
            Files.createDirectories(output);
            Files.write(output.resolve("puzzles.csv"), rows);
            Files.writeString(output.resolve("summary.json"), "{\"samplePerMode\":20,\"seedFirst\":2026111700,"
                    + "\"seedLast\":2026111719,\"cluePuzzles\":\"120 independently unique definitions, including 20 Takuzu per size\","
                    + "\"mines\":\"20 deferred random layouts; not a no-guess guarantee\","
                    + "\"elapsedSeconds\":" + (System.nanoTime() - start) / 1e9 + ",\"assertions\":" + checks + "}\n");
        }
        System.out.println("PASS: " + checks + " six-puzzle assertions");
    }

    private static void reject(Mode mode, byte[] bytes) {
        try { PuzzleStore.decode(mode, bytes); throw new AssertionError("Invalid/cross-mode save accepted"); }
        catch (IOException expected) { checks++; }
    }

    private static void clearProgress() throws Exception {
        for (Mode mode : Mode.values()) {
            Puzzle initial = mode.generate(mode.defaultSetting(), new Random(41), new Generation());
            PuzzleProgress pristine = new PuzzleProgress(initial);
            check(pristine.cleared() == pristine, "Clear is a no-op on a fresh " + mode);
            Puzzle marked = initial.copy();
            if (marked instanceof Takuzu p) {
                int target = 0; while (p.clues[target] != -1) target++;
                p.enter(target, 0);
            } else if (marked instanceof Futoshiki p) {
                int target = 0; while (p.clues[target] != 0) target++;
                p.enter(target, 1);
            }
            else if (marked instanceof Minesweeper p) p.tap(0, true);
            else ((UndoablePuzzle) marked).entries()[0] = 1;
            PuzzleProgress changed = pristine.withElapsed(54321).edited(marked);
            PuzzleProgress cleared = PuzzleStore.decodeProgress(mode, PuzzleStore.encode(changed.cleared()));
            check(cleared.elapsedMillis == 54321 && !cleared.canClear(), "Clear retains time and removes entries: " + mode);
            check(Arrays.equals(PuzzleStore.encode(initial), PuzzleStore.encode(cleared.game)), "Clear retains definition: " + mode);
            check(cleared.canUndo() == (mode != Mode.MINESWEEPER), "Clear adds Undo only in reversible modes");
            if (cleared.canUndo()) {
                PuzzleProgress restored = cleared.undo();
                check(Arrays.equals(PuzzleStore.encode(marked), PuzzleStore.encode(restored.game)), "Undo restores all cleared entries: " + mode);
                check(!restored.redo().canClear() && !restored.cleared().canRedo(), "Redo repeats Clear; fresh Clear discards Redo");
            }
        }
        Minesweeper mines = new Minesweeper(57, 14);
        mines.tap(0, false);
        Minesweeper covered = (Minesweeper) PuzzleStore.decode(Mode.MINESWEEPER,
                PuzzleStore.encode(new PuzzleProgress(mines).cleared().game));
        check(covered.first == 0 && covered.mineCount == 14 && Arrays.equals(mines.mines, covered.mines)
                && Arrays.stream(covered.cells).allMatch(v -> v == 0), "Generated minefield survives a full cover and reload");
        int mine = 0;
        while (covered.mines[mine] == 0) mine++;
        covered.tap(mine, false);
        check(covered.lost() && !new PuzzleProgress(covered).canClear(), "Clear does not re-roll placed mines or reopen a loss");
    }

    private static void visibleMilestones() {
        Takuzu binary = Takuzu.generate(6, new Random(11), new Generation());
        Arrays.fill(binary.values, -1);
        int[] row = {0, 0, 1, 0, 1, 1};
        System.arraycopy(row, 0, binary.values, 0, 6);
        check(PuzzleMilestones.satisfied(binary)[0], "Balanced distinct public-rule line earns a milestone");
        System.arraycopy(row, 0, binary.values, 6, 6);
        check(!PuzzleMilestones.satisfied(binary)[0], "Duplicate completed Takuzu rows never celebrate");
        Arrays.fill(binary.values, 6, 36, -1); binary.values[6] = binary.values[12] = 0;
        check(!PuzzleMilestones.satisfied(binary)[0], "Intersecting triple suppresses an otherwise balanced line");

        Futoshiki latin = Futoshiki.generate(new Random(190), new Generation());
        Arrays.fill(latin.values, 0); Arrays.fill(latin.horizontal, 0); Arrays.fill(latin.vertical, 0);
        for (int i = 0; i < 4; i++) latin.values[i] = i + 1;
        check(PuzzleMilestones.satisfied(latin)[0], "Distinct complete Latin line follows visible rules only");
        latin.values[4] = 1;
        check(!PuzzleMilestones.satisfied(latin)[0], "Intersecting duplicate suppresses Latin milestone");
        latin.values[4] = 4; latin.vertical[0] = 1;
        check(!PuzzleMilestones.satisfied(latin)[0], "Broken inequality suppresses Latin milestone");

        Nonogram picture = Nonogram.generate(new Random(12), new Generation());
        for (int i = 0; i < 5; i++) { picture.rows[i] = new int[]{1}; picture.columns[i] = new int[]{1}; }
        Arrays.fill(picture.values, 0); picture.values[0] = 1;
        check(!PuzzleMilestones.satisfied(picture)[0], "Matching fills with unknown empties are not a decided line");
        Arrays.fill(picture.values, 1, 5, 2);
        check(PuzzleMilestones.satisfied(picture)[0], "Explicit complete matching runs need no hidden picture");
        picture.values[5] = 1;
        check(!PuzzleMilestones.satisfied(picture)[0], "Intersecting infeasible Nonogram column suppresses wave");

        Slitherlink loop = Slitherlink.generate(new Random(119), new Generation());
        Arrays.fill(loop.clues, -1); Arrays.fill(loop.edges, 0); loop.clues[0] = 1;
        loop.edges[loop.sides[0][0]] = 1;
        check(PuzzleMilestones.satisfied(loop)[0] && !loop.solved(), "Met clue count is not a completed open loop");
        for (int edge : loop.grid.at[1]) loop.edges[edge] = 1;
        check(!PuzzleMilestones.satisfied(loop)[0], "Branch prevents clue milestone");
        Arrays.fill(loop.edges, 0); Arrays.fill(loop.clues, -1);
        for (int edge : loop.sides[0]) loop.edges[edge] = 1;
        loop.clues[0] = 4; loop.clues[15] = 1;
        check(!PuzzleMilestones.satisfied(loop)[0], "Premature closed loop never earns a clue wave");
        Arrays.fill(loop.edges, 0); Arrays.fill(loop.clues, -1); loop.clues[0] = 1;
        loop.edges[loop.sides[0][0]] = 1;
        for (int edge : loop.grid.at[18]) loop.edges[edge] = 1;
        Slitherlink corrected = loop.copy();
        corrected.edges[loop.grid.at[18][0]] = corrected.edges[loop.grid.at[18][1]] = 0;
        check(PuzzleMilestones.satisfied(corrected)[0], "Unchanged distant clue remains visibly met after branch correction");
        check(!PuzzleMilestones.newlySatisfied(loop, corrected)[0], "Error correction never replays unrelated met clues");
        check(!PuzzleMilestones.newlySatisfied(corrected, corrected.copy())[0], "No-op/redraw never repeats a milestone");

        Numberlink paths = Numberlink.generate(new Random(81), new Generation());
        Arrays.fill(paths.endpoints, 0); Arrays.fill(paths.edges, 0);
        for (int i = 0; i < 3; i++) { paths.endpoints[i * 4] = i + 1; paths.endpoints[i * 4 + 3] = i + 1; }
        for (int i = 0; i < 3; i++) paths.edges[paths.grid.between(i, i + 1)] = 1;
        check(PuzzleMilestones.satisfied(paths)[1] && !paths.solved(), "Matched connected pair need not cover the whole board");
        paths.edges[paths.grid.between(1, 5)] = 1;
        check(!PuzzleMilestones.satisfied(paths)[1], "Branched path does not celebrate");
        paths.edges[paths.grid.between(1, 5)] = 0; paths.endpoints[3] = 2;
        check(!PuzzleMilestones.satisfied(paths)[1], "Different numbered endpoints do not celebrate");
        paths.endpoints[3] = 1;
        Numberlink beforePair = paths.copy(); beforePair.edges[paths.grid.between(2, 3)] = 0;
        check(PuzzleMilestones.newlySatisfied(beforePair, paths)[1],
                "Connecting a public partial pair earns a local milestone without whole-board coverage");
        Numberlink distantBranch = paths.copy();
        for (int[] edge : new int[][]{{4, 5}, {5, 6}, {6, 7}, {5, 9}})
            distantBranch.edges[paths.grid.between(edge[0], edge[1])] = 1;
        check(PuzzleMilestones.satisfied(distantBranch)[1] && !PuzzleMilestones.satisfied(distantBranch)[2],
                "An unrelated branched component does not suppress a valid pair");
        check(!PuzzleMilestones.newlySatisfied(paths, distantBranch)[1],
                "Distant path edits never replay an unchanged satisfied pair");
        Numberlink uncovered = paths.copy();
        for (int[] route : new int[][]{{4, 5, 6, 7}, {8, 9, 10, 11}})
            for (int i = 1; i < route.length; i++) uncovered.edges[paths.grid.between(route[i - 1], route[i])] = 1;
        check(!uncovered.solved() && PuzzleMilestones.satisfied(uncovered)[1]
                        && PuzzleMilestones.satisfied(uncovered)[2] && PuzzleMilestones.satisfied(uncovered)[3],
                "Every pair can earn a public milestone while uncovered blank cells still prevent completion");
        Numberlink covered = paths.copy();
        covered.endpoints[11] = 0; covered.endpoints[12] = 3;
        for (int[] route : new int[][]{{4, 5, 6, 7}, {8, 9, 10, 11, 15, 14, 13, 12}})
            for (int i = 1; i < route.length; i++) covered.edges[paths.grid.between(route[i - 1], route[i])] = 1;
        check(covered.solved() && PuzzleMilestones.satisfied(covered)[1]
                        && PuzzleMilestones.satisfied(covered)[2] && PuzzleMilestones.satisfied(covered)[3],
                "Whole coverage and milestones agree on complete matching pair components");
        Numberlink branched = covered.copy(); branched.edges[paths.grid.between(1, 5)] = 1;
        check(!branched.solved() && !PuzzleMilestones.satisfied(branched)[1]
                        && !PuzzleMilestones.satisfied(branched)[2] && PuzzleMilestones.satisfied(branched)[3],
                "Shared component rules reject branches without suppressing an unrelated pair");
        Numberlink mismatched = covered.copy(); mismatched.endpoints[3] = 2; mismatched.endpoints[7] = 1;
        check(!mismatched.solved() && !PuzzleMilestones.satisfied(mismatched)[1]
                        && !PuzzleMilestones.satisfied(mismatched)[2] && PuzzleMilestones.satisfied(mismatched)[3],
                "Shared component rules reject mismatched ends even when endpoint counts and coverage are valid");
    }

    private static void binaryMarks() throws Exception {
        for (Mode mode : new Mode[]{Mode.NONOGRAM, Mode.SLITHERLINK}) {
            for (int size : mode.settings()) {
                Puzzle original = mode.generate(size, new Random(124), new Generation());
                int last = original instanceof Nonogram ? size * size - 1 : ((Slitherlink) original).edges.length - 1;
                for (int target : new int[]{0, last}) {
                    PuzzleProgress progress = new PuzzleProgress(original);
                    for (int mark : new int[]{1, 1, 2, 1, 2, 2}) {
                        Puzzle next = progress.game.copy();
                        int before = next instanceof Nonogram ? ((Nonogram) next).values[target] : ((Slitherlink) next).edges[target];
                        if (next instanceof Nonogram) ((Nonogram) next).mark(target, mark);
                        else ((Slitherlink) next).mark(target, mark);
                        int actual = next instanceof Nonogram ? ((Nonogram) next).values[target] : ((Slitherlink) next).edges[target];
                        check(actual == (before == mark ? 0 : mark), mode + " repeats clear; alternate marks replace directly");
                        PuzzleProgress edited = progress.edited(next);
                        check(edited.undoCount() == progress.undoCount() + 1, "One binary mark is one Undo");
                        check(Arrays.equals(PuzzleStore.encode(progress.game), PuzzleStore.encode(edited.undo().game)), "Undo restores exact previous mark");
                        progress = PuzzleStore.decodeProgress(mode, PuzzleStore.encode(edited));
                        check(Arrays.equals(PuzzleStore.encode(next), PuzzleStore.encode(progress.game)), "Binary marks/history persist");
                    }
                    check(Arrays.equals(PuzzleStore.encode(original), PuzzleStore.encode(progress.game)), "Repeat-to-clear returns original board");
                }
            }
        }
    }

    private static void timedProgress() throws Exception {
        for (Mode mode : Mode.values()) {
            Puzzle game = mode.generate(new Random(124), new Generation());
            PuzzleProgress legacy = PuzzleStore.decodeProgress(mode, PuzzleStore.encode(game));
            check(legacy.elapsedMillis == 0, "LPZ1 migration has no fabricated time: " + mode);
            PuzzleProgress timed = legacy.withElapsed(12345);
            byte[] data = PuzzleStore.encode(timed);
            PuzzleProgress restored = PuzzleStore.decodeProgress(mode, data);
            check(restored.elapsedMillis == 12345 && Arrays.equals(PuzzleStore.encode(game), PuzzleStore.encode(restored.game)),
                    "LPZ3 preserves exact board and elapsed: " + mode);
            byte[] damaged = data.clone(); damaged[16] ^= 1; reject(mode, damaged);
            if (game instanceof UndoablePuzzle) {
                Puzzle next = game.copy();
                if (next instanceof Takuzu) {
                    Takuzu p = (Takuzu) next; int cell = 0; while (p.clues[cell] >= 0) cell++; p.cycle(cell);
                } else if (next instanceof Nonogram) ((Nonogram) next).cycle(0);
                else if (next instanceof Futoshiki) {
                    Futoshiki p = (Futoshiki) next; int cell = 0; while (p.clues[cell] != 0) cell++; p.enter(cell, 1);
                } else if (next instanceof Numberlink) ((Numberlink) next).edges[0] = 1;
                else ((Slitherlink) next).cycle(0);
                PuzzleProgress history = legacy.edited(next);
                PuzzleProgress v2 = PuzzleStore.decodeProgress(mode, PuzzleStore.encode(history));
                check(v2.elapsedMillis == 0 && v2.canUndo(), "LPZ2 backward compatibility: " + mode);
                restored = PuzzleStore.decodeProgress(mode, PuzzleStore.encode(history.withElapsed(65432)));
                check(restored.elapsedMillis == 65432 && restored.undo().elapsedMillis == 65432,
                        "Timing is not reversed by persisted Undo: " + mode);
                check(Arrays.equals(PuzzleStore.encode(game), PuzzleStore.encode(restored.undo().game)), "Timed Undo exact board");
            }
        }
    }

    private static void visibleRuleErrors() throws Exception {
        Futoshiki latin = Futoshiki.generate(new Random(190), new Generation());
        Arrays.fill(latin.clues, 0); Arrays.fill(latin.values, 0);
        Arrays.fill(latin.horizontal, 0); Arrays.fill(latin.vertical, 0);
        check(Arrays.stream(latin.errors()).allMatch(e -> e == null), "Unfinished Latin square is not an error");
        latin.values[0] = latin.values[1] = latin.values[4] = 2;
        String[] errors = latin.errors();
        check(errors[0].contains("row 1") && errors[0].contains("column 1"), "A square can explain both duplicate houses");
        check(errors[1] != null && errors[4] != null && errors[5] == null, "All and only duplicate participants are marked");
        Arrays.fill(latin.values, 0); latin.horizontal[0] = -1; latin.vertical[0] = 1;
        latin.values[0] = 3; latin.values[1] = 2;
        check(latin.errors()[0].contains("smaller") && latin.errors()[1] != null, "Both entered ends of a broken inequality marked");
        check(latin.errors()[4] == null, "Blank inequality end is unconstrained");
        Futoshiki corrected = latin.copy(); corrected.enter(1, 4);
        PuzzleProgress history = new PuzzleProgress(latin).edited(corrected);
        check(Arrays.stream(corrected.errors()).allMatch(e -> e == null), "Inequality correction clears feedback");
        check(((Futoshiki) PuzzleStore.decodeProgress(Mode.FUTOSHIKI, PuzzleStore.encode(history)).undo().game).errors()[0] != null,
                "Persisted Undo restores the prior inequality reason");
        corrected.values[4] = 4;
        check(corrected.errors()[4].contains("larger"), "Vertical inequality direction follows visible sign");

        Slitherlink loop = Slitherlink.generate(new Random(119), new Generation());
        Arrays.fill(loop.clues, -1); Arrays.fill(loop.edges, 0);
        check(!loop.errors().any(), "Empty loop has no forced contradiction");
        for (int edge : Slitherlink.GRID.at[6]) loop.edges[edge] = 1;
        for (int edge : Slitherlink.GRID.at[6]) check(loop.errors().edges[edge].contains("Branch at dot row 2 column 2"),
                "Every branch participant gets a localized explanation");
        Arrays.fill(loop.edges, 0);
        loop.clues[0] = 1;
        loop.edges[Slitherlink.SIDES[0][0]] = loop.edges[Slitherlink.SIDES[0][1]] = 1;
        check(loop.errors().cells[0].contains("more than clue 1"), "Over-count uses drawn sides, not hidden solution");
        check(loop.errors().cells[1] == null, "Unrelated clues are not blamed");
        Arrays.fill(loop.edges, 0); Arrays.fill(loop.clues, -1);
        for (int edge : Slitherlink.SIDES[0]) loop.edges[edge] = 1;
        loop.clues[15] = 1;
        check(loop.errors().edges[Slitherlink.SIDES[0][0]].contains("Separate closed loop"),
                "Closed loop unable to satisfy another clue is premature");
        Slitherlink open = loop.copy(); open.edges[Slitherlink.SIDES[0][0]] = 2;
        history = new PuzzleProgress(loop).edited(open);
        check(!open.errors().any(), "Open incomplete paths and crossed notes alone are not errors");
        check(((Slitherlink) PuzzleStore.decodeProgress(Mode.SLITHERLINK, PuzzleStore.encode(history)).undo().game).errors().any(),
                "Undo restores closed-loop feedback without a stored error flag");
        loop.clues[15] = -1;
        check(loop.solved() && !loop.errors().any(), "A single valid loop is complete even with undecided outside edges");
        loop.edges[Slitherlink.SIDES[15][0]] = 1;
        check(loop.errors().edges[Slitherlink.SIDES[0][0]] != null, "Closed loop cannot absorb a separate drawn edge");
        Arrays.fill(loop.edges, 2);
        check(!loop.errors().any(), "Crosses do not count as lines or branches");
    }
    private static void undoProgress() throws Exception {
        for (Mode mode : Mode.values()) {
            Puzzle original = mode.generate(new Random(1170 + mode.ordinal()), new Generation());
            byte[] baseline = PuzzleStore.encode(original);
            PuzzleProgress progress = PuzzleStore.decodeProgress(mode, baseline);
            check(!progress.canUndo() && Arrays.equals(baseline, PuzzleStore.encode(progress)), "Legacy save opens with empty history and no migration reset");
            if (mode == Mode.MINESWEEPER) {
                Minesweeper next = ((Minesweeper) original).copy(); next.tap(27, false);
                check(!progress.edited(next).canUndo(), "Minesweeper information is not reversible");
                continue;
            }
            int target = 0;
            if (original instanceof Takuzu) while (((Takuzu) original).clues[target] >= 0) target++;
            if (original instanceof Futoshiki) while (((Futoshiki) original).clues[target] != 0) target++;
            if (original instanceof Numberlink) {
                boolean found = false;
                for (int edge = 0; edge < Numberlink.GRID.a.length; edge++) {
                    Numberlink next = ((Numberlink) original).copy();
                    if (next.toggle(Numberlink.GRID.a[edge], Numberlink.GRID.b[edge]) == null && next.edges[edge] == 1) {
                        progress = progress.edited(next); target = edge; found = true; break;
                    }
                }
                check(found, "A legal path edit is available");
            } else {
                Puzzle next = original.copy();
                if (next instanceof Takuzu) ((Takuzu) next).enter(target, 0);
                else if (next instanceof Nonogram) ((Nonogram) next).cycle(target);
                else if (next instanceof Futoshiki) ((Futoshiki) next).enter(target, 1);
                else ((Slitherlink) next).cycle(target);
                progress = progress.edited(next);
            }
            check(progress.undoCount() == 1, "One real edit creates one snapshot");
            check(progress.edited(progress.game.copy()) == progress, "No-op creates no history or save");
            byte[] once = PuzzleStore.encode(progress.game);
            Puzzle twice = progress.game.copy();
            if (twice instanceof Takuzu) ((Takuzu) twice).enter(target, 1);
            else if (twice instanceof Nonogram) ((Nonogram) twice).cycle(target);
            else if (twice instanceof Futoshiki) ((Futoshiki) twice).enter(target, 2);
            else if (twice instanceof Numberlink) ((Numberlink) twice).toggle(Numberlink.GRID.a[target], Numberlink.GRID.b[target]);
            else ((Slitherlink) twice).cycle(target);
            progress = progress.edited(twice);
            byte[] encoded = PuzzleStore.encode(progress);
            progress = PuzzleStore.decodeProgress(mode, encoded);
            check(progress.undoCount() == 2 && Arrays.equals(encoded, PuzzleStore.encode(progress)), "History survives an exact restart roundtrip");
            check(Arrays.equals(once, PuzzleStore.encode(progress.undo().game)), "Undo restores the whole previous entry/edge state");
            check(Arrays.equals(baseline, PuzzleStore.encode(progress.undo().undo().game)), "Two undos return exactly to original definition and entries");
            check(!progress.undo().undo().canUndo(), "Empty history is disabled");
            check(!progress.canRedo(), "Undo-only saves read with no Redo");
            PuzzleProgress undone = progress.undo().undo().withElapsed(1234);
            undone = PuzzleStore.decodeProgress(mode, PuzzleStore.encode(undone));
            check(undone.canRedo() && !undone.canUndo() && undone.elapsedMillis == 1234,
                    "Redo-only history and timer persist through reopen");
            check(undone.edited(undone.game.copy()) == undone, "A no-op keeps the redo branch");
            check(Arrays.equals(once, PuzzleStore.encode(undone.redo().game))
                    && Arrays.equals(PuzzleStore.encode(twice), PuzzleStore.encode(undone.redo().redo().game))
                    && !undone.redo().redo().canRedo(), "Redo restores whole states in order");
            PuzzleProgress branch = undone.redo().edited(original.copy());
            check(!branch.canRedo() && !branch.undo().redo().canRedo()
                    && Arrays.equals(baseline, PuzzleStore.encode(branch.undo().redo().game)),
                    "A new move does not resurrect the discarded future");
            byte[] damaged = encoded.clone(); damaged[damaged.length - 10] ^= 1; reject(mode, damaged);
            reject(mode, Arrays.copyOf(encoded, encoded.length - 1));
            reject(mode, Arrays.copyOf(encoded, encoded.length + 1));
            for (Mode other : Mode.values()) if (other != mode) reject(other, encoded);
        }
        Takuzu game = Takuzu.generate(8, new Random(900), new Generation());
        int target = 0; while (game.clues[target] >= 0) target++;
        PuzzleProgress progress = new PuzzleProgress(game); byte[] retained = null;
        for (int move = 0; move < 505; move++) {
            Takuzu next = ((Takuzu) progress.game).copy(); next.enter(target, move % 2);
            progress = progress.edited(next);
            if (move == 4) retained = PuzzleStore.encode(next);
        }
        check(progress.undoCount() == 500, "History is bounded consistently with Sudoku");
        byte[] encoded = PuzzleStore.encode(progress);
        check(encoded.length > 16384 && encoded.length < PuzzleStore.MAX_SAVE_BYTES, "Large valid history fits the new bounded envelope");
        progress = PuzzleStore.decodeProgress(Mode.TAKUZU, encoded);
        for (int move = 0; move < 500; move++) progress = progress.undo();
        check(!progress.canUndo() && Arrays.equals(retained, PuzzleStore.encode(progress.game)), "Only the oldest excess moves are evicted");

        Takuzu next = game.copy(); next.enter(target, 0);
        encoded = PuzzleStore.encode(new PuzzleProgress(game).edited(next));
        int currentLength = java.nio.ByteBuffer.wrap(encoded).getInt(8);
        byte[] corrupt = encoded.clone(); java.nio.ByteBuffer.wrap(corrupt).putInt(12 + currentLength, -1);
        historyChecksum(corrupt); reject(Mode.TAKUZU, corrupt);
        corrupt = encoded.clone(); java.nio.ByteBuffer.wrap(corrupt).putInt(12 + currentLength, 501);
        historyChecksum(corrupt); reject(Mode.TAKUZU, corrupt);
        corrupt = encoded.clone(); java.nio.ByteBuffer.wrap(corrupt).putInt(12, 0x4C505A32);
        historyChecksum(corrupt); reject(Mode.TAKUZU, corrupt);
        reject(Mode.TAKUZU, new byte[PuzzleStore.MAX_SAVE_BYTES + 1]);
        int fixed = 0; while (game.clues[fixed] < 0) fixed++;
        corrupt = encoded.clone(); java.nio.ByteBuffer.wrap(corrupt).putInt(16 + currentLength + fixed * 4, game.clues[fixed] ^ 1);
        historyChecksum(corrupt); reject(Mode.TAKUZU, corrupt);
        next = game.copy(); next.enter(fixed, game.clues[fixed] ^ 1);
        check(new PuzzleProgress(game).edited(next).undoCount() == 0, "Fixed-given rejection creates no history");
        next = game.copy(); next.enter(target, 0); next.enter(target, -1);
        check(new PuzzleProgress(game).edited(next).undoCount() == 0, "Clearing an already empty square is a no-op");
        Arrays.fill(game.clues, -1); Arrays.fill(game.values, -1); game.values[0] = game.values[1] = game.values[2] = 0;
        next = game.copy(); next.enter(2, 1);
        progress = new PuzzleProgress(game).edited(next);
        check(((Takuzu) progress.game).errors()[0] == null, "Correction clears errors");
        check(((Takuzu) progress.undo().game).errors()[0] != null, "Undo restores the prior current-rule errors");
    }

    private static void historyChecksum(byte[] file) {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32(); crc.update(file, 8, file.length - 16);
        java.nio.ByteBuffer.wrap(file).putLong(file.length - 8, crc.getValue());
    }

    private static void nonogramContradictions() throws Exception {
        Nonogram base = Nonogram.generate(new Random(117), new Generation());
        PuzzleProgress taps = new PuzzleProgress(base);
        for (int expected : new int[]{1, 2, 0}) {
            Nonogram next = ((Nonogram) taps.game).copy(); next.cycle(0);
            taps = PuzzleStore.decodeProgress(Mode.NONOGRAM, PuzzleStore.encode(taps.edited(next)));
            check(((Nonogram) taps.game).values[0] == expected, "Each persisted tap advances check, cross, blank");
        }
        check(taps.undoCount() == 3, "Each cycle tap has one Undo step");
        for (int expected : new int[]{2, 1, 0}) {
            taps = taps.undo();
            check(((Nonogram) taps.game).values[0] == expected, "Undo restores each preceding tap state");
        }
        for (boolean column : new boolean[]{false, true}) {
            Nonogram line = base.copy(); Arrays.fill(line.values, 0);
            (column ? line.columns : line.rows)[0] = new int[]{2};
            check(!line.lineSatisfied(0, column), "A blank nonempty clue is not satisfied");
            line.values[0] = 1;
            check(!line.contradictions()[column ? 5 : 0] && !line.lineSatisfied(0, column),
                    "Feasibility is not exact run satisfaction");
            line.values[column ? 5 : 1] = 1;
            check(line.lineSatisfied(0, column), "Exact filled runs satisfy without crossing the remaining blanks");
            check(!PuzzleMilestones.satisfied(line)[column ? 5 : 0], "Static satisfaction does not relax decided-line motion");
            Nonogram erased = line.copy(); erased.mark(column ? 5 : 1, 1);
            PuzzleProgress progress = PuzzleStore.decodeProgress(Mode.NONOGRAM,
                    PuzzleStore.encode(new PuzzleProgress(line).edited(erased)));
            check(!((Nonogram) progress.game).lineSatisfied(0, column)
                            && ((Nonogram) progress.undo().game).lineSatisfied(0, column),
                    "Restored erase and Undo derive the correct clue state");
            line.values[column ? 10 : 2] = 1;
            check(line.contradictions()[column ? 5 : 0] && !line.lineSatisfied(0, column), "Overlong runs are not satisfied");
            Arrays.fill(line.values, 0); (column ? line.columns : line.rows)[0] = new int[0];
            check(line.lineSatisfied(0, column), "An empty clue is satisfied by unfilled undecided cells");
        }
        boolean legalMistake = false;
        for (int seed = 0; seed < 30 && !legalMistake; seed++) {
            Nonogram candidate = Nonogram.generate(new Random(seed), new Generation());
            List<int[]> solutions = new ArrayList<>();
            picture(candidate, new int[25], 0, solutions);
            for (int cell = 0; cell < 25; cell++) if (solutions.get(0)[cell] == 0) {
                candidate.values[cell] = 1;
                if (!candidate.contradictions()[cell / 5] && !candidate.contradictions()[5 + cell % 5]) {
                    legalMistake = true;
                    boolean error = false;
                    for (boolean invalid : candidate.contradictions()) error |= invalid;
                    check(!error, "Legal hidden-solution mistake is not a rule error");
                    break;
                }
                candidate.values[cell] = 0;
            }
        }
        check(legalMistake, "Independent solution supplies a legal-but-wrong regression");
        pictureLine(base, new int[]{}, new int[]{0, 0, 0, 0, 0}, true);
        pictureLine(base, new int[]{}, new int[]{1, 0, 0, 0, 0}, false);
        pictureLine(base, new int[]{}, new int[]{2, 2, 2, 2, 2}, true);
        pictureLine(base, new int[]{2}, new int[]{1, 0, 0, 0, 0}, true);
        pictureLine(base, new int[]{2}, new int[]{1, 0, 1, 0, 0}, false);
        pictureLine(base, new int[]{2}, new int[]{1, 1, 1, 0, 0}, false);
        pictureLine(base, new int[]{2}, new int[]{2, 2, 2, 2, 2}, false);
        pictureLine(base, new int[]{2}, new int[]{1, 1, 0, 2, 2}, true);
        pictureLine(base, new int[]{2}, new int[]{1, 2, 2, 2, 2}, false);
        pictureLine(base, new int[]{1, 1}, new int[]{1, 0, 1, 0, 0}, true);
        pictureLine(base, new int[]{1, 1}, new int[]{1, 1, 0, 0, 0}, false);
        pictureLine(base, new int[]{1, 1}, new int[]{1, 0, 1, 0, 1}, false);
        pictureLine(base, new int[]{1, 1}, new int[]{2, 2, 0, 2, 2}, false);
        pictureLine(base, new int[]{3}, new int[]{0, 2, 0, 2, 0}, false);
        pictureLine(base, new int[]{3}, new int[]{0, 0, 1, 0, 0}, true);
        pictureLine(base, new int[]{3}, new int[]{1, 0, 0, 0, 1}, false);
        Arrays.fill(base.values, 0); base.rows[0] = new int[]{2};
        base.values[0] = base.values[1] = base.values[2] = 1;
        check(base.contradictions()[0] && !base.solved(), "An overlong partial run is a contradiction, never a win");
        Nonogram corrected = base.copy(); corrected.cycle(2);
        PuzzleProgress progress = new PuzzleProgress(base).edited(corrected);
        check(!corrected.contradictions()[0], "Crossing the excess fill restores a feasible line");
        progress = PuzzleStore.decodeProgress(Mode.NONOGRAM, PuzzleStore.encode(progress));
        check(!((Nonogram) progress.game).contradictions()[0], "Corrected marks survive restart");
        check(((Nonogram) progress.undo().game).contradictions()[0], "Undo reintroduces the actual line contradiction");
    }

    private static void pictureLine(Nonogram prototype, int[] clues, int[] marks, boolean feasible) {
        for (int axis = 0; axis < 2; axis++) {
            Nonogram game = prototype.copy(); Arrays.fill(game.values, 0);
            if (axis == 0) game.rows[0] = clues.clone(); else game.columns[0] = clues.clone();
            for (int cell = 0; cell < 5; cell++) game.values[axis == 0 ? cell : cell * 5] = marks[cell];
            check(game.contradictions()[axis * 5] != feasible,
                    "Line feasibility uses only visible fills/crosses, not an incomplete pattern or hidden answer: "
                            + Arrays.toString(clues) + " / " + Arrays.toString(marks));
        }
    }

    private static void puzzleSettings() throws Exception {
        check(Arrays.equals(Mode.NUMBERLINK.settings(), new int[]{6}) && Mode.NUMBERLINK.defaultSetting() == 6,
                "The Numberlink menu only offers 6x6");
        for (int pairs : new int[]{3, 4}) {
            Puzzle legacy = Mode.NUMBERLINK.generate(pairs, new Random(20261121 + pairs), new Generation());
            byte[] saved = PuzzleStore.encode(legacy);
            check(Mode.NUMBERLINK.settingOf(PuzzleStore.decode(Mode.NUMBERLINK, saved)) == pairs
                            && Mode.NUMBERLINK.settingLabel(pairs).contains("4 \u00d7 4")
                            && Arrays.equals(saved, PuzzleStore.encode(PuzzleStore.decode(Mode.NUMBERLINK, saved))),
                    "Legacy Numberlink settings are still playable without offering them in the menu");
        }
        for (Mode mode : Mode.values()) for (int setting : mode.settings()) {
            long total = 0, maximum = 0; Set<String> definitions = new HashSet<>(), routeShapes = new HashSet<>();
            int samples = mode == Mode.NUMBERLINK || mode == Mode.NONOGRAM || mode == Mode.SLITHERLINK ? 40 : 8;
            for (int seed = 0; seed < samples; seed++) {
                long start = System.nanoTime();
                Puzzle game = mode.generate(setting, new Random(2026111800L + seed), new Generation());
                long elapsed = System.nanoTime() - start; total += elapsed; maximum = Math.max(maximum, elapsed);
                check(mode.settingOf(game) == setting, "Generated board carries its own immutable setting");
                definitions.add(Arrays.toString(PuzzleStore.encode(game)));
                check(mode.settingOf(PuzzleStore.decode(mode, PuzzleStore.encode(game))) == setting, "Settings survive save migration");
                if (!(game instanceof Numberlink) && !(game instanceof Minesweeper) && !(game instanceof Nonogram)
                        && !(game instanceof Slitherlink)) verify(game.copy());
                if (game instanceof Nonogram) {
                    Nonogram picture = (Nonogram) game; List<int[]> solutions = independentPicture(picture);
                    check(solutions.size() == 1, "Each offered size has an independent unique line-domain solution");
                    Nonogram solved = picture.copy(); System.arraycopy(solutions.get(0), 0, solved.values, 0, solved.values.length);
                    check(solved.solved(), "Independent larger picture satisfies every actual clue");
                    for (boolean error : solved.contradictions()) check(!error, "A solved larger picture has no rule errors");
                    PuzzleProgress edited = new PuzzleProgress(picture).edited(solved).withElapsed(3456);
                    PuzzleProgress restored = PuzzleStore.decodeProgress(mode, PuzzleStore.encode(edited));
                    check(restored.game.solved() && restored.game.size() == setting && restored.elapsedMillis == 3456
                            && Arrays.equals(((Nonogram) restored.undo().game).values, picture.values),
                            "Larger picture definitions, completion, timing and exact Undo survive persistence");
                    if (setting == 10 && seed == 0) {
                        PuzzleProgress maximumHistory = new PuzzleProgress(picture);
                        for (int step = 0; step < PuzzleProgress.MAX_UNDO + 1; step++) {
                            Nonogram next = (Nonogram) maximumHistory.game.copy(); next.cycle(0);
                            maximumHistory = maximumHistory.edited(next);
                        }
                        byte[] bytes = PuzzleStore.encode(maximumHistory.withElapsed(9876));
                        check(bytes.length <= PuzzleStore.MAX_SAVE_BYTES
                                && PuzzleStore.decodeProgress(mode, bytes).undoCount() == PuzzleProgress.MAX_UNDO,
                                "Largest supported picture retains all 500 real Undo steps within the save-size limit");
                    }
                } else if (game instanceof Numberlink) {
                    List<int[]> solutions = new ArrayList<>();
                    Numberlink link = (Numberlink) game;
                    cover(pathOptions(link.endpoints), 0, 0, new int[link.edges.length], solutions);
                    check(solutions.size() == 1, "Each offered pair count is independently uniquely full-cover solvable");
                    if (link.size() == 6) {
                        routeShapes.add(shape(link));
                        Numberlink solved = withEdges(link, solutions.get(0));
                        check(solved.solved(), "Independent 6x6 full-cover solution satisfies real rules");
                        int detour = 0, bends = 0;
                        for (int pair = 1; pair <= link.pairs(); pair++) {
                            int first = -1, last = -1, length = 0;
                            for (int i = 0; i < link.endpoints.length; i++) {
                                if (link.endpoints[i] == pair) { if (first < 0) first = i; else last = i; }
                                if (solved.pairAt(i) != pair) continue;
                                length++;
                                int a = -1, b = -1;
                                for (int edge : solved.grid.at[i]) if (solved.edges[edge] == 1) {
                                    if (a < 0) a = solved.grid.other(edge, i); else b = solved.grid.other(edge, i);
                                }
                                if (b >= 0 && a / 6 != b / 6 && a % 6 != b % 6) bends++;
                            }
                            int distance = Math.abs(first / 6 - last / 6) + Math.abs(first % 6 - last % 6);
                            check(length >= 4 && distance >= 2, "Larger puzzle has no adjacent or two-square trivial pair");
                            detour += length - 1 - distance;
                        }
                        check(detour >= 10 && bends >= 12, "New setting demands routed detours and bends, not only a larger label");
                        if (fixtures != null && seed == 0) {
                            Files.write(fixtures.resolve("NUMBERLINK6-initial.bin"), PuzzleStore.encode(link));
                            Files.write(fixtures.resolve("NUMBERLINK6-complete.bin"), PuzzleStore.encode(solved));
                        }
                        if (seed == 0) {
                            PuzzleProgress history = new PuzzleProgress(link);
                            for (int step = 0; step < PuzzleProgress.MAX_UNDO + 1; step++) {
                                Numberlink next = (Numberlink) history.game.copy(); next.edges[0] ^= 1;
                                history = history.edited(next);
                            }
                            check(PuzzleStore.decodeProgress(mode, PuzzleStore.encode(history)).undoCount() == 500,
                                    "Larger edge array retains all 500 real Undo steps within the same save cap");
                        }
                    }
                    PuzzleProgress edited = new PuzzleProgress(game).edited(withEdges((Numberlink) game, solutions.get(0)));
                    PuzzleProgress restored = PuzzleStore.decodeProgress(mode, PuzzleStore.encode(edited.withElapsed(2345)));
                    check(restored.game.solved() && restored.elapsedMillis == 2345
                            && ((Numberlink) restored.undo().game).edges.length == link.edges.length,
                            "New pair-count wins retain timed persistent Undo");
                } else if (game instanceof Slitherlink) {
                    Slitherlink loop = (Slitherlink) game;
                    routeShapes.add(shape(loop));
                    List<int[]> solutions = setting == 4 ? loops(loop.clues) : independentLoops(loop.clues);
                    check(solutions.size() == 1, "Independent planar-region row oracle proves one loop at every offered size");
                    Slitherlink solved = loop.copy(); System.arraycopy(solutions.get(0), 0, solved.edges, 0, solved.edges.length);
                    check(solved.solved() && !solved.errors().any(), "Independent larger loop passes actual rules without false errors");
                    check(Slitherlink.count(loop.clues, 2, new Generation()) == 1, "Production loop count agrees with independent oracle");
                    if (fixtures != null && seed == 0) {
                        Files.write(fixtures.resolve("SLITHERLINK" + setting + "-initial.bin"), PuzzleStore.encode(loop));
                        Files.write(fixtures.resolve("SLITHERLINK" + setting + "-complete.bin"), PuzzleStore.encode(solved));
                    }
                    if (seed == 0) {
                        PuzzleProgress history = new PuzzleProgress(loop);
                        for (int step = 0; step < PuzzleProgress.MAX_UNDO; step++) {
                            Slitherlink next = (Slitherlink) history.game.copy(); next.edges[0] = (next.edges[0] + 1) % 3;
                            history = history.edited(next);
                        }
                        byte[] encoded = PuzzleStore.encode(history.withElapsed(2345));
                        check(encoded.length <= PuzzleStore.MAX_SAVE_BYTES, "500 loop Undo steps retain existing save cap");
                        PuzzleProgress restored = PuzzleStore.decodeProgress(mode, encoded);
                        check(restored.undoCount() == 500 && restored.elapsedMillis == 2345 && restored.game.size() == setting,
                                "All 500 larger-loop states and timer survive compact persistent history");
                        for (int step = 0; step < 500; step++) restored = restored.undo();
                        check(Arrays.equals(PuzzleStore.encode(loop), PuzzleStore.encode(restored.game)), "Every loop Undo restores exact initial bytes");
                        restored = PuzzleStore.decodeProgress(mode, PuzzleStore.encode(restored));
                        for (int step = 0; step < 500; step++) restored = restored.redo();
                        check(Arrays.equals(PuzzleStore.encode(history.game), PuzzleStore.encode(restored.game))
                                && restored.undoCount() == 500 && !restored.canRedo(), "Compact history persists all 500 Redo steps within the same cap");
                        Slitherlink errors = loop.copy(); Arrays.fill(errors.clues, -1);
                        for (int edge : errors.grid.at[setting + 2]) errors.edges[edge] = 1;
                        check(errors.errors().edges[errors.grid.at[setting + 2][0]].contains("dot row 2 column 2"),
                                "Larger branch feedback has correct dot coordinates");
                        Arrays.fill(errors.edges, 0); int last = errors.clues.length - 1; errors.clues[last] = 1;
                        errors.edges[errors.sides[last][0]] = errors.edges[errors.sides[last][1]] = 1;
                        check(errors.errors().cells[last].contains("Row " + setting + " column " + setting),
                                "Larger clue feedback has correct coordinates");
                    }
                } else if (game instanceof Minesweeper) {
                    Minesweeper mines = (Minesweeper) game;
                    mines.tap(seed % 64, false);
                    check(Arrays.stream(mines.mines).sum() == setting && mines.adjacent(mines.first) == 0 && !mines.lost(),
                            "Every density retains exact mine count and safe first neighborhood");
                    check(((Minesweeper) PuzzleStore.decode(mode, PuzzleStore.encode(mines))).mineCount == setting,
                            "Placed mine settings survive explicit extension reading");
                    Minesweeper flags = new Minesweeper(seed, setting);
                    for (int i = 0; i < setting; i++) check(flags.tap(i, true) == null, "Density supplies its own flag budget");
                    check(flags.tap(setting, true) != null && flags.flags() == setting, "Density flag limit is enforced");
                }
            }
            check(definitions.size() >= samples * 3 / 4, "Every offered setting remains procedural, not a fixed template");
            if (mode == Mode.NUMBERLINK && setting == 6)
                check(routeShapes.size() >= samples / 2, "Larger route definitions vary beyond reflection, rotation and pair relabeling");
            if (mode == Mode.SLITHERLINK)
                check(routeShapes.size() >= samples * 3 / 4, "Larger loops vary beyond reflected or rotated templates");
            check(maximum < 10_000_000_000L, "Offered generation remains inside the existing ten-second budget");
            System.out.printf("SETTING %s %d: %d samples, %d distinct, mean %.1f ms, max %.1f ms%n",
                    mode, setting, samples, definitions.size(), total / samples / 1e6, maximum / 1e6);
            if (!routeShapes.isEmpty()) System.out.println(mode + " non-symmetric definitions: " + routeShapes.size());
        }
    }

    private static Numberlink withEdges(Numberlink original, int[] edges) {
        Numberlink copy = original.copy(); System.arraycopy(edges, 0, copy.edges, 0, edges.length); return copy;
    }

    private static void numberlinkDrags() throws Exception {
        Numberlink board = Numberlink.generate(new Random(5), new Generation());
        Arrays.fill(board.endpoints, 0); Arrays.fill(board.edges, 0);
        for (int pair = 1; pair <= 3; pair++) {
            board.endpoints[(pair - 1) * 4] = pair;
            board.endpoints[(pair - 1) * 4 + 3] = pair;
        }
        check(Numberlink.Drag.begin(board, 1) == null, "Unconnected blank retains tap/scroll alternative");
        Numberlink.Drag drag = Numberlink.Drag.begin(board, 0);
        check(!drag.moveTo(5), "Diagonal teleport rejected");
        check(!drag.moveTo(4), "Other pair endpoint never overwritten");
        check(!drag.changed() && Arrays.stream(board.edges).sum() == 0, "Rejected drag has no board/history edit");
        check(drag.moveTo(1) && drag.moveTo(2), "Orthogonal drag draws consecutive edges");
        check(Arrays.stream(board.edges).sum() == 0, "Preview leaves durable board unchanged until release");
        check(drag.moveTo(1) && Arrays.stream(drag.preview.edges).sum() == 1, "Backtracking retracts own tail");
        check(drag.moveTo(2) && drag.moveTo(3), "Matching endpoint connects");
        check(!drag.moveTo(7), "Finished pair cannot branch through endpoint");
        PuzzleProgress history = new PuzzleProgress(board).edited(drag.preview);
        check(history.undoCount() == 1, "Entire drawn gesture is one logical Undo");
        PuzzleProgress restored = PuzzleStore.decodeProgress(Mode.NUMBERLINK, PuzzleStore.encode(history));
        check(Arrays.equals(((Numberlink) restored.undo().game).edges, board.edges), "Persistent gesture Undo restores exact pre-drag board");
        Numberlink.Drag cancel = Numberlink.Drag.begin(drag.preview, 1);
        check(cancel.moveTo(5) && cancel.preview.edges[Numberlink.GRID.between(1, 2)] == 0,
                "Editing existing path retains endpoint prefix and replaces only downstream tail");
        check(drag.preview.edges[Numberlink.GRID.between(1, 2)] == 1, "Discard/cancel leaves original route intact");
        Numberlink blocked = board.copy();
        blocked.toggle(4, 5); blocked.toggle(5, 6); blocked.toggle(6, 7);
        drag = Numberlink.Drag.begin(blocked, 0); drag.moveTo(1);
        int[] before = drag.preview.edges.clone();
        check(!drag.moveTo(5) && Arrays.equals(before, drag.preview.edges), "Other path crossing is rejected without damaging either path");
        check(drag.preview.pairAt(1) == 1 && drag.preview.pairAt(5) == 2, "Pair identities follow path connectivity, not redraw order");
        Numberlink loose = board.copy(); loose.toggle(1, 2);
        int[] looseBefore = loose.edges.clone();
        Numberlink.Drag looseDrag = Numberlink.Drag.begin(loose, 1);
        check(looseDrag != null && looseDrag.moveTo(0), "Existing disconnected tap path can be dragged to acquire its first endpoint");
        check(looseDrag.preview.pairAt(1) == 1, "An initially unnumbered path acquires the connected pair identity");
        check(looseDrag.moveTo(1) && looseDrag.preview.pairAt(1) == 0,
                "Retracting an acquired endpoint leaves the surviving loose component unnumbered");
        check(looseDrag.moveTo(5) && looseDrag.moveTo(4) && looseDrag.preview.pairAt(1) == 2,
                "One loose-path drag can redirect from a retracted pair-one endpoint to pair two");
        check(looseDrag.preview.edges[board.grid.between(0, 1)] == 0
                        && looseDrag.preview.edges[board.grid.between(1, 5)] == 1
                        && looseDrag.preview.edges[board.grid.between(5, 4)] == 1
                        && Arrays.equals(loose.edges, looseBefore),
                "Redirect retains only the surviving route and leaves the original board unchanged");
        Numberlink.Drag firstPending = Numberlink.Drag.begin(board, 0);
        firstPending.moveTo(1); firstPending.moveTo(2); firstPending.moveTo(3);
        Numberlink.Drag secondPending = Numberlink.Drag.begin(board, 4);
        secondPending.moveTo(5);
        Numberlink latest = firstPending.preview.copy(), merged = latest.copy();
        check(merged.applyChanges(board, secondPending.preview) == null
                        && merged.edges[board.grid.between(0, 1)] == 1 && merged.edges[board.grid.between(4, 5)] == 1,
                "Rapid queued drags merge only their changes rather than overwriting the prior durable route");
        PuzzleProgress mergedHistory = new PuzzleProgress(latest).edited(merged);
        check(Arrays.equals(((Numberlink) mergedHistory.undo().game).edges, latest.edges),
                "Queued drag Undo restores the actual preceding committed route");
        Numberlink.Drag conflictingPending = Numberlink.Drag.begin(board, 0);
        conflictingPending.moveTo(1); conflictingPending.moveTo(5);
        int[] committedEdges = merged.edges.clone();
        check(merged.copy().applyChanges(board, conflictingPending.preview) != null && Arrays.equals(committedEdges, merged.edges),
                "A queued drag that conflicts with a now-committed route cannot publish partial changes");
    }

    private static void takuzuLegacyAndErrors() throws Exception {
        Set<String> definitions = new HashSet<>(); long total = 0, maximum = 0;
        for (int seed = 0; seed < 20; seed++) {
            long start = System.nanoTime();
            Takuzu game = Takuzu.generate(6, new Random(2026111700L + seed), new Generation());
            long elapsed = System.nanoTime() - start; total += elapsed; maximum = Math.max(maximum, elapsed);
            byte[] legacy = PuzzleStore.encode(game);
            check(legacy.length == 312, "Legacy 6x6 retains its exact envelope and unprefixed 36-cell payload");
            Takuzu restored = (Takuzu) PuzzleStore.decode(Mode.TAKUZU, legacy);
            check(restored.size() == 6 && Arrays.equals(legacy, PuzzleStore.encode(restored)), "Existing 6x6 resumes unchanged");
            check(definitions.add(shape(game)), "6x6 sample also has non-symmetric variety");
            verify(game);
            game.values[0] ^= 1;
            if (fixtures != null && seed == 0) {
                Files.write(fixtures.resolve("TAKUZU6-initial.bin"), legacy);
                int target = 0; while (game.clues[target] >= 0) target++;
                Files.write(fixtures.resolve("TAKUZU6-complete.bin"), PuzzleStore.encode(game));
                game.values[target] = -1;
                Files.write(fixtures.resolve("TAKUZU6-ready.bin"), PuzzleStore.encode(game));
                Files.writeString(fixtures.resolve("TAKUZU6-target.txt"), "" + target);
            }
        }
        System.out.printf("TAKUZU 6x6: 20 independently unique / %d non-symmetric shapes, %.1f ms mean / %.1f ms max%n",
                definitions.size(), total / 20e6, maximum / 1e6);
        for (int n : new int[]{6, 8}) {
            Takuzu game = Takuzu.generate(n, new Random(601 + n), new Generation());
            List<int[]> solutions = new ArrayList<>(); binary(game.clues, new int[n * n], 0, solutions);
            int[] solution = solutions.get(0);
            Arrays.fill(game.clues, -1); Arrays.fill(game.values, -1);
            game.values[0] = game.values[1] = game.values[2] = 0;
            for (int cell = 0; cell < 3; cell++) check(game.errors()[cell].contains("Three consecutive 0s in row 1"), "All triple participants marked");
            game.values[2] = 1; check(Arrays.stream(game.errors()).allMatch(e -> e == null), "Correcting a triple clears stale reasons");
            Arrays.fill(game.values, -1);
            for (int row = 0; row <= n / 2; row++) game.values[row * n + 2] = 1;
            for (int row = 0; row <= n / 2; row++) check(game.errors()[row * n + 2].contains("Too many 1s in column 3"), "All overfull-column participants marked");
            Arrays.fill(game.values, -1);
            for (int col = 0; col <= n / 2; col++) game.values[col] = 0;
            for (int col = 0; col <= n / 2; col++) check(game.errors()[col].contains("Too many 0s in row 1"), "All overfull-row participants marked");
            Arrays.fill(game.values, -1);
            for (int row = 0; row < 2; row++) System.arraycopy(solution, 0, game.values, row * n, n);
            for (int cell = 0; cell < n * 2; cell++) check(game.errors()[cell].contains("Rows 1 and 2 are identical"), "Both duplicate rows marked");
            Arrays.fill(game.values, -1);
            for (int row = 0; row < n; row++) game.values[row * n] = game.values[row * n + 1] = solution[row];
            for (int row = 0; row < n; row++) for (int col = 0; col < 2; col++)
                check(game.errors()[row * n + col].contains("Columns 1 and 2 are identical"), "Both duplicate columns marked");
            Arrays.fill(game.values, 0);
            check(!game.solved(), "An invalid filled board never wins");
            check(!((Takuzu) PuzzleStore.decode(Mode.TAKUZU, PuzzleStore.encode(game))).solved(), "Correctable mistakes persist without becoming wins");
        }
    }
    private static void verify(Puzzle puzzle) throws Exception {
        int[] solution;
        if (puzzle instanceof Takuzu) {
            Takuzu game = (Takuzu) puzzle;
            List<int[]> solutions = new ArrayList<>();
            binary(game.clues, new int[game.clues.length], 0, solutions);
            check(solutions.size() == 1, "Independent balanced-row Takuzu oracle proves uniqueness");
            check(Takuzu.count(game.clues, 2, new Generation()) == 1, "Production Takuzu count agrees");
            solution = solutions.get(0);
            for (int i = 0; i < game.values.length; i++) game.values[i] = solution[i];
            check(game.solved(), "Takuzu completion follows actual constraints");
            completed(game);
            game.values[0] ^= 1; check(!game.solved(), "Wrong completed Takuzu rejected");
        } else if (puzzle instanceof Nonogram) {
            Nonogram game = (Nonogram) puzzle;
            List<int[]> solutions = new ArrayList<>();
            picture(game, new int[25], 0, solutions);
            check(solutions.size() == 1, "Independent row-pattern Nonogram uniqueness");
            check(Nonogram.count(game.rows, game.columns, 2, new Generation()) == 1, "Production picture count agrees");
            solution = solutions.get(0);
            for (int i = 0; i < 25; i++) game.values[i] = solution[i] == 1 ? 1 : 2;
            check(game.solved(), "Crossed empties accepted, actual runs checked");
            completed(game);
            game.values[0] = game.values[0] == 1 ? 0 : 1; check(!game.solved(), "Wrong picture rejected");
        } else if (puzzle instanceof Futoshiki) {
            Futoshiki game = (Futoshiki) puzzle;
            List<int[]> solutions = new ArrayList<>();
            latin(game, game.clues.clone(), 0, solutions);
            check(solutions.size() == 1, "Independent Latin/inequality oracle uniqueness");
            check(Futoshiki.count(game.clues, game.horizontal, game.vertical, 2, new Generation()) == 1, "Production Latin count agrees");
            System.arraycopy(solutions.get(0), 0, game.values, 0, 16);
            check(game.solved(), "Latin/inequality rules accept completed puzzle");
            check(Arrays.stream(game.errors()).allMatch(e -> e == null), "Valid generated Latin solutions have no error flags");
            completed(game);
            game.values[0] = game.values[1]; check(!game.solved(), "Duplicate Latin entry rejected");
        } else if (puzzle instanceof Minesweeper) {
            Minesweeper game = (Minesweeper) puzzle;
            game.tap(27, false);
            check(game.adjacent(27) == 0 && game.mines[27] == 0, "First neighborhood is safe");
            check(Arrays.stream(game.mines).sum() == 10, "Exactly ten mines");
            check(Arrays.stream(game.cells).filter(v -> v == 1).count() >= 9, "Initial blank flood opens neighborhood");
            int mine = -1;
            for (int i = 0; i < 64; i++) if (game.mines[i] == 1) { mine = i; break; }
            Minesweeper winning = game.copy();
            for (int i = 0; i < 64; i++) if (winning.mines[i] == 0) winning.tap(i, false);
            check(winning.solved() && !winning.lost(), "Reveal all and only safe squares wins");
            completed(winning);
            byte[] won = PuzzleStore.encode(winning); check(PuzzleStore.decode(Mode.MINESWEEPER, won).solved(), "Win persists");
            game.tap(mine, false);
            check(game.lost() && !game.solved(), "Revealed mine loses");
            check(PuzzleStore.decode(Mode.MINESWEEPER, PuzzleStore.encode(game)).lost(), "Loss persists");
            int[] frozen = game.cells.clone(); game.tap(0, true); check(Arrays.equals(frozen, game.cells), "Terminal state frozen");
        } else if (puzzle instanceof Numberlink) {
            Numberlink game = (Numberlink) puzzle;
            List<PathOption>[] options = pathOptions(game.endpoints);
            List<int[]> solutions = new ArrayList<>();
            cover(options, 0, 0, new int[24], solutions);
            check(solutions.size() == 1, "Independent path-mask exact cover proves unique full coverage");
            check(Numberlink.count(game.endpoints, 2, new Generation()) == 1, "Production path-extension count agrees");
            System.arraycopy(solutions.get(0), 0, game.edges, 0, 24);
            check(game.solved(), "Independent solution accepted by graph rules");
            completed(game);
            for (int i = 0; i < 24; i++) if (game.edges[i] == 1) { game.edges[i] = 0; break; }
            check(!game.solved(), "Incomplete path rejected");
        } else if (puzzle instanceof Slitherlink) {
            Slitherlink game = (Slitherlink) puzzle;
            List<int[]> solutions = loops(game.clues);
            check(solutions.size() == 1, "Independent exhaustive planar-region loop oracle proves uniqueness");
            check(Slitherlink.count(game.clues, 2, new Generation()) == 1, "Production edge-propagation count agrees");
            System.arraycopy(solutions.get(0), 0, game.edges, 0, 40);
            check(game.solved(), "Independent loop accepted");
            check(!game.errors().any(), "Valid generated loops have no false contradiction flags");
            completed(game);
            for (int i = 0; i < 40; i++) if (game.edges[i] == 1) { game.edges[i] = 2; break; }
            check(!game.solved(), "Crossed edge cannot complete a loop");
        }
    }

    private static void completed(Puzzle puzzle) throws Exception {
        check(PuzzleStore.decode(puzzle.mode(), PuzzleStore.encode(puzzle)).solved(), "Every completed mode persists");
        shapes.add(shape(puzzle));
        if (fixtures == null || !recorded.add(puzzle.mode())) return;
        Files.write(fixtures.resolve(puzzle.mode() + "-complete.bin"), PuzzleStore.encode(puzzle));
        Puzzle ready = puzzle.copy();
        int target = -1;
        if (ready instanceof Takuzu) {
            Takuzu p = (Takuzu) ready;
            for (int i = 0; i < p.clues.length; i++) if (p.clues[i] < 0) { target = i; p.values[i] = -1; break; }
        } else if (ready instanceof Nonogram) {
            Nonogram p = (Nonogram) ready;
            for (int i = 0; i < 25; i++) if (p.values[i] == 1) { target = i; p.values[i] = 0; break; }
        } else if (ready instanceof Futoshiki) {
            Futoshiki p = (Futoshiki) ready;
            for (int i = 0; i < 16; i++) if (p.clues[i] == 0) { target = i; p.values[i] = 0; break; }
        } else if (ready instanceof Minesweeper) {
            Minesweeper p = (Minesweeper) ready;
            for (int i = 0; i < 64; i++) if (p.mines[i] == 0 && i != p.first) { target = i; p.cells[i] = 0; break; }
        } else {
            int[] edges = ready instanceof Numberlink ? ((Numberlink) ready).edges : ((Slitherlink) ready).edges;
            for (int i = 0; i < edges.length; i++) if (edges[i] == 1) { target = i; edges[i] = 0; break; }
        }
        check(target >= 0 && !ready.solved(), "A real last move remains in each native test fixture");
        if (puzzle instanceof UndoablePuzzle) {
            PuzzleProgress completed = new PuzzleProgress(ready).edited(puzzle.copy());
            check(completed.canUndo() && completed.game.solved(), "Final move creates Undo across completion");
            completed = PuzzleStore.decodeProgress(puzzle.mode(), PuzzleStore.encode(completed));
            check(!completed.undo().game.solved(), "Undo reopens a completed puzzle after restart");
            check(Arrays.equals(PuzzleStore.encode(ready), PuzzleStore.encode(completed.undo().game)), "Completion Undo restores the exact prior state");
        }
        Files.write(fixtures.resolve(puzzle.mode() + "-ready.bin"), PuzzleStore.encode(ready));
        Files.writeString(fixtures.resolve(puzzle.mode() + "-target.txt"), "" + target);
    }

    private static String shape(Puzzle puzzle) {
        int n = puzzle.size(); int[] grid;
        if (puzzle instanceof Takuzu) grid = Arrays.stream(((Takuzu) puzzle).clues).map(v -> v < 0 ? 0 : 1).toArray();
        else if (puzzle instanceof Nonogram) grid = Arrays.stream(((Nonogram) puzzle).values).map(v -> v == 1 ? 1 : 0).toArray();
        else if (puzzle instanceof Minesweeper) grid = ((Minesweeper) puzzle).mines;
        else if (puzzle instanceof Numberlink) grid = ((Numberlink) puzzle).endpoints;
        else if (puzzle instanceof Slitherlink) grid = Arrays.stream(((Slitherlink) puzzle).clues).map(v -> v + 1).toArray();
        else {
            Futoshiki p = (Futoshiki) puzzle; n = 7; grid = new int[49];
            for (int i = 0; i < 16; i++) grid[i / 4 * 14 + i % 4 * 2] = p.clues[i] == 0 ? 0 : 1;
            for (int i = 0; i < 12; i++) {
                grid[i / 3 * 14 + i % 3 * 2 + 1] = p.horizontal[i] == 0 ? 0 : 2;
                grid[(i / 4 * 2 + 1) * 7 + i % 4 * 2] = p.vertical[i] == 0 ? 0 : 2;
            }
        }
        String best = null;
        for (int mirror = 0; mirror < 2; mirror++) for (int turn = 0; turn < 4; turn++) {
            int[] transformed = new int[n * n];
            for (int i = 0; i < grid.length; i++) {
                int r = i / n, c = mirror == 0 ? i % n : n - 1 - i % n;
                for (int t = 0; t < turn; t++) { int old = r; r = c; c = n - 1 - old; }
                transformed[r * n + c] = grid[i];
            }
            if (puzzle instanceof Numberlink) {
                int[] labels = new int[Numberlink.MAX_PAIRS + 1]; int next = 1;
                for (int i = 0; i < transformed.length; i++) if (transformed[i] > 0) {
                    if (labels[transformed[i]] == 0) labels[transformed[i]] = next++;
                    transformed[i] = labels[transformed[i]];
                }
            }
            String text = Arrays.toString(transformed);
            if (best == null || text.compareTo(best) < 0) best = text;
        }
        return best;
    }

    private static void binary(int[] clues, int[] board, int row, List<int[]> out) {
        int n = (int) Math.sqrt(clues.length), all = (1 << n) - 1;
        if (out.size() >= 2) return;
        if (row == n) { out.add(board.clone()); return; }
        for (int mask = 0; mask <= all; mask++) {
            if (Integer.bitCount(mask) != n / 2 || ((mask & mask >> 1 & mask >> 2) != 0)
                    || (((~mask & all) & (~mask & all) >> 1 & (~mask & all) >> 2) != 0)) continue;
            boolean okay = true;
            for (int col = 0; col < n; col++) {
                int value = mask >> col & 1; board[row * n + col] = value;
                if (clues[row * n + col] >= 0 && clues[row * n + col] != value) okay = false;
                if (row >= 2 && board[(row - 1) * n + col] == value && board[(row - 2) * n + col] == value) okay = false;
                int ones = 0; for (int r = 0; r <= row; r++) ones += board[r * n + col];
                if (ones > n / 2 || row + 1 - ones > n / 2) okay = false;
            }
            for (int previous = 0; previous < row; previous++) {
                boolean same = true;
                for (int col = 0; col < n; col++) if (board[previous * n + col] != board[row * n + col]) same = false;
                if (same) okay = false;
            }
            if (row == n - 1) for (int col = 0; col < n; col++) for (int other = 0; other < col; other++) {
                boolean same = true;
                for (int r = 0; r < n; r++) if (board[r * n + col] != board[r * n + other]) same = false;
                if (same) okay = false;
            }
            if (okay) binary(clues, board, row + 1, out);
        }
    }
    private static int[] runClues(int[] board, int index, boolean column) {
        List<Integer> runs = new ArrayList<>(); int count = 0;
        for (int i = 0; i <= 5; i++) {
            if (i < 5 && board[column ? i * 5 + index : index * 5 + i] == 1) count++;
            else if (count > 0) { runs.add(count); count = 0; }
        }
        return runs.stream().mapToInt(Integer::intValue).toArray();
    }
    private static List<int[]> independentPicture(Nonogram game) {
        int n = game.size(); int[][] domains = new int[n * 2][];
        List<int[]> clues = new ArrayList<>();
        for (int mask = 0; mask < 1 << n; mask++) {
            List<Integer> runs = new ArrayList<>(); int length = 0;
            for (int bit = 0; bit <= n; bit++) {
                if (bit < n && (mask & 1 << bit) != 0) length++;
                else if (length != 0) { runs.add(length); length = 0; }
            }
            clues.add(runs.stream().mapToInt(Integer::intValue).toArray());
        }
        for (int line = 0; line < n * 2; line++) {
            int[] target = line < n ? game.rows[line] : game.columns[line - n];
            List<Integer> masks = new ArrayList<>();
            for (int mask = 0; mask < 1 << n; mask++) if (Arrays.equals(clues.get(mask), target)) masks.add(mask);
            domains[line] = masks.stream().mapToInt(Integer::intValue).toArray();
        }
        List<int[]> solutions = new ArrayList<>(); pictureDomains(domains, n, solutions); return solutions;
    }

    private static void pictureDomains(int[][] original, int n, List<int[]> out) {
        if (out.size() >= 2) return;
        int[][] domains = original.clone(); boolean changed;
        do {
            changed = false;
            for (int row = 0; row < n; row++) for (int col = 0; col < n; col++) {
                int rowBits = 0, columnBits = 0;
                for (int mask : domains[row]) rowBits |= 1 << (mask >> col & 1);
                for (int mask : domains[n + col]) columnBits |= 1 << (mask >> row & 1);
                int allowed = rowBits & columnBits;
                if (allowed == 0) return;
                int column = col, r = row;
                int[] rows = Arrays.stream(domains[row]).filter(mask -> (allowed & 1 << (mask >> column & 1)) != 0).toArray();
                int[] columns = Arrays.stream(domains[n + col]).filter(mask -> (allowed & 1 << (mask >> r & 1)) != 0).toArray();
                changed |= rows.length != domains[row].length || columns.length != domains[n + col].length;
                domains[row] = rows; domains[n + col] = columns;
            }
        } while (changed);
        int branch = -1;
        for (int line = 0; line < n * 2; line++) if (domains[line].length > 1
                && (branch < 0 || domains[line].length < domains[branch].length)) branch = line;
        if (branch < 0) {
            int[] board = new int[n * n];
            for (int row = 0; row < n; row++) for (int col = 0; col < n; col++) board[row * n + col] = domains[row][0] >> col & 1;
            out.add(board); return;
        }
        for (int mask : domains[branch]) {
            int[][] trial = domains.clone(); trial[branch] = new int[]{mask}; pictureDomains(trial, n, out);
            if (out.size() >= 2) return;
        }
    }

    private static void picture(Nonogram game, int[] board, int row, List<int[]> out) {
        if (out.size() >= 2) return;
        if (row == 5) {
            for (int c = 0; c < 5; c++) if (!Arrays.equals(runClues(board, c, true), game.columns[c])) return;
            out.add(board.clone()); return;
        }
        for (int mask = 0; mask < 32; mask++) {
            for (int c = 0; c < 5; c++) board[row * 5 + c] = mask >> c & 1;
            if (Arrays.equals(runClues(board, row, false), game.rows[row])) picture(game, board, row + 1, out);
        }
    }
    private static void latin(Futoshiki game, int[] board, int cell, List<int[]> out) {
        if (out.size() >= 2) return;
        if (cell == 16) { out.add(board.clone()); return; }
        for (int d = 1; d <= 4; d++) {
            if (game.clues[cell] != 0 && game.clues[cell] != d) continue;
            boolean okay = true;
            for (int i = 0; i < cell; i++) if ((i / 4 == cell / 4 || i % 4 == cell % 4) && board[i] == d) okay = false;
            if (cell % 4 > 0) {
                int sign = game.horizontal[cell / 4 * 3 + cell % 4 - 1];
                if (sign != 0 && Integer.signum(board[cell - 1] - d) != sign) okay = false;
            }
            if (cell >= 4) {
                int sign = game.vertical[cell - 4];
                if (sign != 0 && Integer.signum(board[cell - 4] - d) != sign) okay = false;
            }
            board[cell] = d;
            if (okay) latin(game, board, cell + 1, out);
        }
    }
    private static final class PathOption {
        final long cells; final int[] edges;
        PathOption(long cells, int[] edges) { this.cells = cells; this.edges = edges.clone(); }
    }
    @SuppressWarnings("unchecked")
    private static List<PathOption>[] pathOptions(int[] endpoints) {
        int pairs = Arrays.stream(endpoints).max().orElse(0);
        int n = (int) Math.sqrt(endpoints.length);
        List<PathOption>[] options = (List<PathOption>[]) new List<?>[pairs];
        for (int label = 1; label <= pairs; label++) {
            options[label - 1] = new ArrayList<>();
            int start = -1, end = -1;
            for (int i = 0; i < endpoints.length; i++) if (endpoints[i] == label) { if (start < 0) start = i; else end = i; }
            enumeratePaths(endpoints, start, end, 1L << start, new int[2 * n * (n - 1)], options[label - 1]);
        }
        return options;
    }
    private static int edgeIndex(int a, int b, int n) {
        int low = Math.min(a, b), high = Math.max(a, b);
        if (high == low + 1 && high / n == low / n) return low / n * (n - 1) + low % n;
        if (high == low + n) return n * (n - 1) + low;
        return -1;
    }
    private static void enumeratePaths(int[] ends, int head, int end, long visited, int[] edges, List<PathOption> out) {
        if (head == end) { out.add(new PathOption(visited, edges)); return; }
        int n = (int) Math.sqrt(ends.length);
        for (int next = 0; next < ends.length; next++) {
            int edge = edgeIndex(head, next, n);
            if (edge < 0 || (visited & 1L << next) != 0 || (ends[next] != 0 && next != end)) continue;
            edges[edge] = 1; enumeratePaths(ends, next, end, visited | 1L << next, edges, out); edges[edge] = 0;
        }
    }
    private static void cover(List<PathOption>[] options, int pair, long mask, int[] edges, List<int[]> out) {
        if (out.size() >= 2) return;
        int n = (int) Math.round((1 + Math.sqrt(1 + 2 * edges.length)) / 2);
        if (pair == options.length) { if (mask == (1L << n * n) - 1) out.add(edges.clone()); return; }
        for (PathOption option : options[pair]) if ((mask & option.cells) == 0) {
            for (int i = 0; i < edges.length; i++) edges[i] += option.edges[i];
            cover(options, pair + 1, mask | option.cells, edges, out);
            for (int i = 0; i < edges.length; i++) edges[i] -= option.edges[i];
        }
    }
    private static List<int[]> independentLoops(int[] clues) {
        int n = (int) Math.sqrt(clues.length);
        List<int[]> result = new ArrayList<>();
        int[] rows = new int[n], nodes = {0};
        for (int first = 0; first < 1 << n && result.size() < 2; first++)
            regionRows(clues, n, rows, 0, 0, first, nodes, result);
        return result;
    }

    private static void regionRows(int[] clues, int n, int[] rows, int row, int previous, int current,
                                   int[] nodes, List<int[]> result) {
        if (++nodes[0] > 3_000_000) throw new AssertionError("Independent region oracle exceeded its node bound");
        rows[row] = current;
        int forced = 0, values = 0;
        for (int c = 0; c < n; c++) if (clues[row * n + c] >= 0) {
            int bit = current >> c & 1;
            int known = ((previous >> c & 1) ^ bit) + ((c == 0 ? 0 : current >> (c - 1) & 1) ^ bit)
                    + ((c + 1 == n ? 0 : current >> (c + 1) & 1) ^ bit);
            int bottom = clues[row * n + c] - known;
            if (bottom < 0 || bottom > 1) return;
            forced |= 1 << c; values |= (bottom ^ bit) << c;
        }
        if (row + 1 == n) {
            if (values != 0) return;
            int half = n * (n + 1); int[] edges = new int[half * 2];
            for (int r = 0; r <= n; r++) for (int c = 0; c < n; c++)
                edges[r * n + c] = (r == 0 ? 0 : rows[r - 1] >> c & 1) ^ (r == n ? 0 : rows[r] >> c & 1);
            for (int r = 0; r < n; r++) for (int c = 0; c <= n; c++)
                edges[half + r * (n + 1) + c] = (c == 0 ? 0 : rows[r] >> (c - 1) & 1) ^ (c == n ? 0 : rows[r] >> c & 1);
            int[] degrees = new int[(n + 1) * (n + 1)]; int start = -1;
            for (int e = 0; e < edges.length; e++) if (edges[e] == 1) {
                int a = e < half ? e / n * (n + 1) + e % n : e - half;
                int b = a + (e < half ? 1 : n + 1); degrees[a]++; degrees[b]++; start = a;
            }
            if (start < 0) return;
            for (int degree : degrees) if (degree != 0 && degree != 2) return;
            boolean[] seen = new boolean[degrees.length]; seen[start] = true;
            for (int pass = 0; pass < degrees.length; pass++) for (int e = 0; e < edges.length; e++) if (edges[e] == 1) {
                int a = e < half ? e / n * (n + 1) + e % n : e - half;
                int b = a + (e < half ? 1 : n + 1);
                if (seen[a] || seen[b]) seen[a] = seen[b] = true;
            }
            for (int v = 0; v < degrees.length; v++) if (degrees[v] > 0 && !seen[v]) return;
            result.add(edges); return;
        }
        for (int next = 0; next < 1 << n && result.size() < 2; next++) if ((next & forced) == values)
            regionRows(clues, n, rows, row + 1, current, next, nodes, result);
    }

    private static List<int[]> loops(int[] clues) {
        List<int[]> result = new ArrayList<>();
        for (int inside = 1; inside < 65536 && result.size() < 2; inside++) {
            int[] edges = new int[40];
            for (int r = 0; r <= 4; r++) for (int c = 0; c < 4; c++)
                edges[r * 4 + c] = (r > 0 ? inside >> ((r - 1) * 4 + c) & 1 : 0)
                        ^ (r < 4 ? inside >> (r * 4 + c) & 1 : 0);
            for (int r = 0; r < 4; r++) for (int c = 0; c <= 4; c++)
                edges[20 + r * 5 + c] = (c > 0 ? inside >> (r * 4 + c - 1) & 1 : 0)
                        ^ (c < 4 ? inside >> (r * 4 + c) & 1 : 0);
            boolean matches = true;
            for (int cell = 0; cell < 16; cell++) {
                int r = cell / 4, c = cell % 4;
                int sum = edges[r * 4 + c] + edges[(r + 1) * 4 + c] + edges[20 + r * 5 + c] + edges[20 + r * 5 + c + 1];
                if (clues[cell] >= 0 && clues[cell] != sum) { matches = false; break; }
            }
            if (!matches) continue;
            boolean[][] adjacent = new boolean[25][25]; int start = -1;
            for (int a = 0; a < 25 && matches; a++) {
                int degree = 0;
                for (int b = 0; b < 25; b++) {
                    int edge = edgeIndex(a, b, 5);
                    if (edge >= 0 && edges[edge] == 1) { adjacent[a][b] = true; degree++; }
                }
                if (degree != 0 && degree != 2) matches = false;
                if (degree == 2) start = a;
            }
            if (!matches || start < 0) continue;
            boolean[] seen = new boolean[25]; seen[start] = true;
            for (int pass = 0; pass < 25; pass++) for (int a = 0; a < 25; a++) if (seen[a])
                for (int b = 0; b < 25; b++) if (adjacent[a][b]) seen[b] = true;
            for (int a = 0; a < 25; a++) for (int b = 0; b < 25; b++) if (adjacent[a][b] && !seen[a]) matches = false;
            if (matches) result.add(edges);
        }
        return result;
    }
    private static void edgesAndMines() throws Exception {
        for (int target : new int[]{0, 7, 56, 63, 3, 24, 18}) {
            Minesweeper chord = null;
            for (int seed = 0; seed < 1000; seed++) {
                Minesweeper candidate = new Minesweeper(seed); candidate.tap(target < 32 ? 63 : 0, false);
                if (candidate.mines[target] == 0 && candidate.adjacent(target) > 0) { chord = candidate; break; }
            }
            check(chord != null, "Numbered corner/edge/interior fixture exists");
            Arrays.fill(chord.cells, 0); chord.cells[chord.first] = chord.cells[target] = 1;
            byte[] unchanged = PuzzleStore.encode(chord);
            chord.tap(target, false);
            check(Arrays.equals(unchanged, PuzzleStore.encode(chord)), "Too few flags cannot chord");
            int safe = -1, mine = -1;
            for (int i = 0; i < 64; i++) if (i != target
                    && Math.abs(i / 8 - target / 8) <= 1 && Math.abs(i % 8 - target % 8) <= 1) {
                if (chord.mines[i] == 1) { chord.cells[i] = 2; mine = i; }
                else safe = i;
            }
            check(safe >= 0 && mine >= 0, "Chord has safe and mined neighbors");
            Minesweeper excess = chord.copy(); excess.cells[safe] = 2;
            unchanged = PuzzleStore.encode(excess); excess.tap(target, false);
            check(Arrays.equals(unchanged, PuzzleStore.encode(excess)), "Too many flags cannot chord");
            unchanged = PuzzleStore.encode(chord); chord.tap(target, true);
            check(Arrays.equals(unchanged, PuzzleStore.encode(chord)), "Holding a revealed number cannot chord");
            Minesweeper expected = chord.copy(), wrong = chord.copy(), win = chord.copy();
            for (int i = 0; i < 64; i++) if (i != target && Math.abs(i / 8 - target / 8) <= 1
                    && Math.abs(i % 8 - target % 8) <= 1 && expected.cells[i] == 0) expected.tap(i, false);
            chord.tap(target, false);
            check(Arrays.equals(expected.cells, chord.cells) && !chord.lost(),
                    "Exact flags reveal all eight bounded neighbors through ordinary flood behavior");
            check(PuzzleStore.decode(Mode.MINESWEEPER, PuzzleStore.encode(chord)).solved() == chord.solved(),
                    "Chord result persists");
            wrong.cells[mine] = 0; wrong.cells[safe] = 2; wrong.tap(target, false);
            check(wrong.lost() && wrong.cells[safe] == 2 && wrong.cells[mine] == 1,
                    "Incorrect flags stay untouched and can expose a mine");
            unchanged = PuzzleStore.encode(wrong); wrong.tap(target, false); wrong.tap(safe, true);
            check(Arrays.equals(unchanged, PuzzleStore.encode(wrong)), "Lost board cannot chord or flag");
            for (int i = 0; i < 64; i++) if (win.mines[i] == 0) win.cells[i] = 1;
            win.cells[safe] = 0; win.tap(target, false);
            check(win.solved() && PuzzleStore.decode(Mode.MINESWEEPER, PuzzleStore.encode(win)).solved(),
                    "Final safe chord completes and persists");
        }
        Minesweeper flags = new Minesweeper(1);
        for (int cell = 0; cell < 10; cell++) flags.tap(cell, true);
        check(flags.flags() == 10 && flags.tap(10, true) != null && flags.cells[10] == 0, "Flag cap explicit");
        check(flags.tap(0, false) != null && flags.first < 0, "Flagged square cannot reveal/start");
        flags.tap(0, true); flags.tap(0, false); check(flags.first == 0, "Removing flag permits safe first reveal");
        byte[] before = PuzzleStore.encode(flags);
        Minesweeper copy = flags.copy(); copy.tap(63, true);
        check(Arrays.equals(before, PuzzleStore.encode(flags)), "Copy edits never mutate current saved state");
        Numberlink link = Numberlink.generate(new Random(44), new Generation());
        check(link.toggle(0, 15) != null, "Nonadjacent path edit rejected");
        Numberlink editing = link.copy();
        Arrays.fill(editing.endpoints, 0); Arrays.fill(editing.edges, 0);
        for (int i : new int[]{0, 5}) editing.endpoints[i] = 1;
        for (int i : new int[]{1, 6}) editing.endpoints[i] = 2;
        for (int i : new int[]{10, 15}) editing.endpoints[i] = 3;
        check(editing.toggle(0, 1) != null && Arrays.stream(editing.edges).sum() == 0, "Different endpoint pairs cannot merge");
        editing.edges[Numberlink.GRID.between(4, 0)] = editing.edges[Numberlink.GRID.between(4, 5)] = 1;
        check(editing.toggle(4, 8) != null && editing.edges[Numberlink.GRID.between(4, 8)] == 0, "Crossings/branches exceed internal degree");
        check(editing.toggle(0, 1) != null, "Fixed endpoint cannot acquire a second connection");
        Arrays.fill(editing.edges, 0);
        for (int[] pair : new int[][]{{8, 9}, {9, 13}, {13, 12}}) editing.edges[Numberlink.GRID.between(pair[0], pair[1])] = 1;
        check(editing.toggle(12, 8) != null, "Premature closed path loops rejected");
        check(editing.toggle(8, 9) == null && editing.edges[Numberlink.GRID.between(8, 9)] == 0, "Existing edges can be removed to repair a path");
        List<int[]> solved = new ArrayList<>(); cover(pathOptions(link.endpoints), 0, 0, new int[24], solved);
        for (int i = 0; i < 24; i++) if (solved.get(0)[i] == 1) {
            check(link.toggle(Numberlink.GRID.a[i], Numberlink.GRID.b[i]) == null, "Solution can be entered in any edge order");
        }
        check(link.solved(), "Real edge editing completes Numberlink");
        int[] noClues = new int[16]; Arrays.fill(noClues, -1);
        int[] twoLoops = new int[40];
        for (int cell : new int[]{0, 15}) for (int edge : Slitherlink.SIDES[cell]) twoLoops[edge] = 1;
        check(!Slitherlink.valid(noClues, twoLoops), "Disjoint valid-degree subtours rejected");
        twoLoops[Slitherlink.SIDES[15][0]] = 0;
        check(!Slitherlink.valid(noClues, twoLoops), "Loose end rejected");
        Takuzu binary = Takuzu.generate(new Random(7), new Generation());
        int fixed = 0; while (binary.clues[fixed] < 0) fixed++;
        int fixedValue = binary.values[fixed]; binary.cycle(fixed);
        check(binary.values[fixed] == fixedValue, "Takuzu clues cannot be cycled");
        int empty = 0; while (binary.clues[empty] >= 0) empty++;
        binary.cycle(empty); check(binary.values[empty] == 0, "Binary empty-to-zero input");
        binary.cycle(empty); check(binary.values[empty] == 1, "Binary zero-to-one input");
        binary.cycle(empty); check(binary.values[empty] == -1, "Binary one-to-empty input");
        Nonogram picture = Nonogram.generate(new Random(7), new Generation());
        picture.values[0] = 3;
        reject(Mode.NONOGRAM, PuzzleStore.encode(picture));
    }
}
