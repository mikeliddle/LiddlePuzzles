package com.quietgrid.sudoku;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public final class EngineTests {
    private static int checks;
    public static void main(String[] args) throws Exception {
        noteProvenance();
        digitSelection();
        cellFirstInput();
        exhaustedDigitsAndModes();
        heldNotes();
        duplicatePrevention();
        houseCompletion();
        armedDigitBlockedCells();
        persistence();
        importedSaveCompatibility();
        committedCopyIsolation();
        redoHistory();
        clearBoard();
        SharingTests.run();
        SaveTests.run();
        activePlayTime();
        DifficultyTests.run();
        DailyWordTests.run();
        PuzzleTests.run();
        generation();
        System.out.println("PASS: " + checks + " engine assertions");
    }

    private static void check(boolean condition, String reason) {
        checks++;
        if (!condition) throw new AssertionError(reason);
    }

    private static void clearBoard() {
        Game game = blank();
        game.setAutoNotes(true);
        game.selected = 0; game.enter(2);
        game.elapsedMillis = 12345;
        check(game.clearBoard() && game.autoNotes && game.elapsedMillis == 12345, "Clear preserves Auto notes and elapsed time");
        check(game.values[0] == 0 && game.notes[0] == Sudoku.ALL && !game.canClearBoard(), "Clear restores starting notes and values");
        check(!game.clearBoard() && game.undo() && game.values[0] == 2, "Empty Clear makes no history; one Undo restores all entries");
        check(game.redo() && game.values[0] == 0, "One Redo clears the board again");
    }
    private static void committedCopyIsolation() throws Exception {
        Game game = blank();
        game.selected = 0;
        check(game.enter(2), "Create a committed move with history");
        Game committed = game.copy();
        game.selected = 1; game.enter(3);
        check(committed.values[1] == 0 && committed.selected == 0, "Rejected edits cannot mutate committed board/selection");
        check(committed.undo() && committed.values[0] == 0, "Committed copy retains independent Undo history");
        check(game.values[0] == 2 && game.values[1] == 3, "Rollback copy Undo never changes prospective board");
        Game retry = game.copy();
        game.undo();
        check(retry.undo() && retry.values[0] == 2 && retry.values[1] == 0,
                "Copies preserve exact earlier steps even when source history changes");
    }

    private static void redoHistory() throws Exception {
        Game game = blank();
        game.selected = 0;
        check(game.enter(2), "First real move");
        game.setAutoNotes(true);
        check(game.undo() && !game.autoNotes && game.undo() && game.values[0] == 0,
                "Undo pushes cell and auto-note state in order");
        game.selected = 2;
        check(!game.clear() && game.canRedo(), "Selection and a no-op do not invalidate Redo");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        game.write(new DataOutputStream(bytes));
        Game reopened = Game.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        check(!reopened.canUndo() && reopened.redo() && reopened.values[0] == 2 && !reopened.autoNotes,
                "Redo-only history survives reopening in LIFO order");
        check(reopened.redo() && reopened.autoNotes && reopened.notes[0] == Sudoku.ALL && !reopened.canRedo(),
                "Redo restores exact auto-note state and consumes the last step");
        check(game.canRedo() && game.values[0] == 0, "Restoring from a save does not mutate the source");
        reopened.undo();
        Game beforeBranch = reopened.copy();
        reopened.selected = 1;
        check(reopened.enter(3) && !reopened.canRedo(), "A fresh edit drops the old branch");
        check(beforeBranch.redo() && beforeBranch.autoNotes, "Branching cannot modify the committed rollback copy");
        check(reopened.undo() && reopened.redo() && reopened.values[1] == 3 && !reopened.autoNotes
                && !reopened.canRedo(), "Later Undo only pushes fresh branch moves");
    }

    private static void heldNotes() throws Exception {
        Game game = blank();
        game.toggleDigit(2);
        check(game.noteSelectedDigit(0), "Holding selected digit toggles its note");
        check(game.values[0] == 0 && game.visibleNotes(0) == Sudoku.bit(2)
                && !game.noteMode && game.armedDigit == 2 && game.selected == -1,
                "Secondary input preserves value, input mode and armed digit");
        check(game.noteSelectedDigit(0) && game.visibleNotes(0) == 0, "Second hold removes just that note");
        check(game.undo() && game.visibleNotes(0) == Sudoku.bit(2), "Hold note is a real Undo unit");
        check(game.selectCell(1) && game.values[1] == 2, "Default selected-number input still places the digit");
        check(!game.noteSelectedDigit(0) && game.visibleNotes(0) == 0, "Hold respects temporary peer suppression");
        check(game.undo() && game.visibleNotes(0) == Sudoku.bit(2), "Undo of placement restores held note provenance");
        game.noteSelectedDigit(0);
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        game.write(new DataOutputStream(data));
        Game restored = Game.read(new DataInputStream(new ByteArrayInputStream(data.toByteArray())));
        check(restored.undo() && restored.visibleNotes(0) == Sudoku.bit(2), "Held note and exact history survive resume");
    }

    private static void activePlayTime() {
        long[] now = {1000};
        ActivePlayTimer timer = new ActivePlayTimer(() -> now[0]);
        timer.restore(0);
        now[0] += 5000;
        check(timer.elapsedMillis() == 0, "Opening/loading is not active play");
        timer.setRunning(true); now[0] += 1500;
        timer.setRunning(true); now[0] += 500;
        check(timer.elapsedMillis() == 2000, "Ticks checkpoint without counting the same interval twice");
        for (String pause : new String[]{"help", "settings/modal", "background", "screen off"}) {
            timer.setRunning(false); long elapsed = timer.elapsedMillis();
            now[0] += 60000; timer.setRunning(false);
            check(timer.elapsedMillis() == elapsed, pause + " time excluded, including repeated callbacks");
            timer.setRunning(true); now[0] += 250;
        }
        timer.setRunning(false);
        check(timer.elapsedMillis() == 3000, "Only active intervals accumulate");
        ActivePlayTimer restarted = new ActivePlayTimer(() -> now[0]);
        restarted.restore(timer.elapsedMillis()); now[0] += 900000;
        check(restarted.elapsedMillis() == 3000, "Process restart restores time, not its obsolete monotonic anchor");
        restarted.setRunning(true); now[0] += 700;
        restarted.setRunning(false); now[0] += 500000;
        check(restarted.elapsedMillis() == 3700, "Terminal/completed idle time is frozen");
        restarted.setRunning(true); now[0] += 300;
        restarted.setRunning(false);
        check(restarted.elapsedMillis() == 4000, "Undo completion resumes accumulated play, not completed idle time");
        restarted.restore(0);
        check(restarted.elapsedMillis() == 0, "Confirmed replacement/legacy unknown starts from zero");
        check(ActivePlayTimer.label(3601000).equals("60:01"), "Elapsed display does not wrap at an hour");
        try { restarted.restore(-1); throw new AssertionError("Negative elapsed accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
    }

    private static Game blank() {
        int[] solution = new int[81];
        for (int r = 0; r < 9; r++) for (int c = 0; c < 9; c++) solution[r * 9 + c] = (r * 3 + r / 3 + c) % 9 + 1;
        return new Game(new Sudoku.Puzzle(new int[81], solution, Sudoku.Difficulty.FOCUSED, null));
    }

    private static void armedDigitBlockedCells() {
        int[] clues = new int[81];
        clues[0] = 4;
        clues[60] = 2;
        Game g = new Game(new Sudoku.Puzzle(clues, blank().solution, Sudoku.Difficulty.FOCUSED, null));
        g.selected = 70;
        check(g.enter(3), "Fill a square outside the four's houses");
        g.notes[80] = Sudoku.bit(4);
        g.toggleDigit(4);
        // A legal player entry counts, even when it differs from the solution.
        check(g.selectCell(40), "Place a second visible four");
        boolean[] cells = new boolean[81];
        g.markArmedDigitBlockedCells(cells);
        for (int i = 0; i < cells.length; i++) {
            int row = i / 9, column = i % 9;
            boolean expected = row == 0 || column == 0 || (row < 3 && column < 3)
                    || row == 4 || column == 4 || (row / 3 == 1 && column / 3 == 1)
                    || i == 60 || i == 70;
            check(cells[i] == expected, "Occupied squares and houses containing a visible four: cell " + i);
        }
        g.setNoteMode(true);
        g.markArmedDigitBlockedCells(cells);
        check(Arrays.equals(cells, new boolean[81]), "Notes mode has no house shading");
        g.setNoteMode(false);
        check(g.undo(), "Undo number placement");
        g.markArmedDigitBlockedCells(cells);
        check(cells[0] && cells[10] && !cells[40], "Undo keeps only the given's houses");
        check(g.selectCell(40), "Replace the player four");
        g.toggleDigit(4);
        g.selectCell(40);
        g.markArmedDigitBlockedCells(cells);
        check(Arrays.equals(cells, new boolean[81]), "Cell-first selection has no armed houses");
        check(g.clear(), "Erase the player four");
        g.toggleDigit(4);
        g.markArmedDigitBlockedCells(cells);
        check(cells[0] && !cells[40], "Erased entries no longer shade houses");
        check(g.undo(), "Undo erase");
        g.markArmedDigitBlockedCells(cells);
        check(cells[40], "Undo erase restores house shading");
        g.toggleDigit(5);
        g.markArmedDigitBlockedCells(cells);
        for (int i = 0; i < cells.length; i++) {
            check(cells[i] == (i == 0 || i == 40 || i == 60 || i == 70),
                    "With no visible five, only occupied squares are shaded: cell " + i);
        }
    }

    private static void houseCompletion() {
        int target = 40;
        for (int mask = 0; mask < 8; mask++) {
            Game g = blank();
            System.arraycopy(g.solution, 0, g.values, 0, 81);
            g.values[target] = g.values[80] = 0;
            if ((mask & 1) == 0) g.values[36] = 0;
            if ((mask & 2) == 0) g.values[4] = 0;
            if ((mask & 4) == 0) g.values[30] = 0;
            int expected = ((mask & 1) != 0 ? 1 << 4 : 0)
                    | ((mask & 2) != 0 ? 1 << 13 : 0) | ((mask & 4) != 0 ? 1 << 22 : 0);
            check(g.completedHouses(target) == 0, "Empty target never completes a house");
            g.selected = target;
            check(g.enter(g.solution[target]), "House fixture accepts actual move");
            check(g.completedHouses(target) == expected, "Exact row/column/box completion combination " + mask);
            check(!g.enter(g.solution[target]), "Same-number no-op does not produce a move");
            check(g.clear() && g.completedHouses(target) == 0, "Clearing breaks completion");
            check(g.undo() && g.completedHouses(target) == expected, "Undo preserves house validity without new move");
            g.clear();
            check(g.enter(g.solution[target]) && g.completedHouses(target) == expected,
                    "A later real recompletion is detectable");
        }
        Game g = blank();
        System.arraycopy(g.solution, 0, g.values, 0, 81);
        for (int cell = 0; cell < 81; cell++) {
            int expected = (1 << cell / 9) | (1 << (9 + cell % 9))
                    | (1 << (18 + cell / 27 * 3 + cell % 9 / 3));
            check(g.completedHouses(cell) == expected, "Every house maps correctly at cell " + cell);
        }
        g.values[36] = g.values[target];
        check((g.completedHouses(target) & (1 << 4)) == 0, "Filled duplicate house is not complete");
        check(g.completedHouses(-1) == 0, "Unselected input has no completed houses");
        g = blank();
        g.selected = target;
        g.noteMode = true;
        g.enter(9);
        check(g.completedHouses(target) == 0, "Notes never complete houses");
    }

    private static void noteProvenance() {
        Game g = blank();
        check(!g.showMistakes, "Mistakes off by default");
        g.setAutoNotes(true);
        check(g.visibleNotes(0) == Sudoku.ALL, "Auto fills all candidates");
        g.selected = 0; g.noteMode = true; g.enter(3);
        int manuallyKept = Sudoku.ALL & ~Sudoku.bit(3);
        check(g.visibleNotes(0) == manuallyKept, "Manual deletion retained");
        g.noteMode = false; g.selected = 1; g.enter(2);
        check(g.visibleNotes(0) == (manuallyKept & ~Sudoku.bit(2)), "Row suppresses 2 only");
        check(g.visibleNotes(10) == (Sudoku.ALL & ~Sudoku.bit(2)), "Box suppresses 2");
        check(g.visibleNotes(28) == (Sudoku.ALL & ~Sudoku.bit(2)), "Column suppresses 2");
        check(g.visibleNotes(40) == Sudoku.ALL, "Nonpeer unaffected");
        g.clear();
        check(g.visibleNotes(0) == manuallyKept, "Clear restores 2, not manually removed 3");
        check(g.undo() && g.values[1] == 2, "Undo clear restores placement");
        check(g.visibleNotes(0) == (manuallyKept & ~Sudoku.bit(2)), "Undo clear suppresses peer note");
        g.enter(4);
        check(g.visibleNotes(0) == (manuallyKept & ~Sudoku.bit(4)), "Replacement restores old and hides new");
        g.undo();
        check(g.values[1] == 2 && g.visibleNotes(0) == (manuallyKept & ~Sudoku.bit(2)), "Undo replacement exact");
        g.selected = 27; g.enter(2);
        g.selected = 1; g.clear();
        check((g.visibleNotes(0) & Sudoku.bit(2)) == 0, "Overlapping blockers remain suppressed");
        g.selected = 27; g.clear();
        check(g.visibleNotes(0) == manuallyKept, "Last blocker removed restores exact notes");
        g.selected = 0; g.enter(5);
        check(g.visibleNotes(0) == 0, "Filled cell hides notes");
        g.clear();
        check(g.visibleNotes(0) == manuallyKept, "Clearing restores own notes");
        g.undo(); g.undo();
        check(g.visibleNotes(0) == manuallyKept, "Undo own placement preserves manual notes");
        g.setAutoNotes(false); g.setAutoNotes(true);
        check(g.visibleNotes(0) == Sudoku.ALL, "Re-enable resets manual deletions");
        g.undo();
        check(!g.autoNotes && g.visibleNotes(0) == manuallyKept, "Undo reset restores intent");
        g.noteMode = true; g.selected = 0; g.enter(3);
        check(g.visibleNotes(0) == Sudoku.ALL, "Manual addition works with auto off");
        g.noteMode = false;
        for (int i = 0; i < 81; i++) if (g.solution[i] == 1) { g.selected = i; g.enter(1); }
        check(g.remaining(1) == 0, "Excess wrong entries never produce negative remaining");
        check(!g.won(), "Full wrong board cannot win");
        Game fixed = new Game(new Sudoku.Puzzle(g.solution, g.solution, Sudoku.Difficulty.FOCUSED, null));
        fixed.selected = 0;
        check(!fixed.enter(2) && !fixed.clear(), "Fixed clues immutable");
        check(fixed.won(), "Correct full board wins");
        check(Sudoku.solutions(new int[81], 2) == 2, "Ambiguous board capped at two solutions");
        int[] invalid = new int[81]; invalid[0] = 1; invalid[1] = 1;
        check(Sudoku.solutions(invalid, 2) == 0, "Conflicting clues rejected");
    }

    private static byte[] encode(Game game) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        game.write(new DataOutputStream(bytes));
        return bytes.toByteArray();
    }

    private static void persistence() throws Exception {
        Game g = blank();
        g.setAutoNotes(true); g.selected = 7; g.noteMode = true; g.enter(4);
        g.noteMode = false; g.selected = 8; g.enter(2);
        g.showMistakes = true; g.elapsedMillis = 123456; g.toggleDigit(6);
        Game copy = Game.read(new DataInputStream(new ByteArrayInputStream(encode(g))));
        DataInputStream stored = new DataInputStream(new ByteArrayInputStream(encode(g)));
        stored.readInt();
        check(stored.readUTF().equals("FOCUSED"), "Easy retains the legacy saved difficulty identifier");
        check(copy.difficulty == Sudoku.Difficulty.FOCUSED && copy.difficulty.label.equals("Easy"),
                "Existing saved difficulty displays as Easy");
        check(Arrays.equals(g.values, copy.values), "Values survive process recreation");
        check(Arrays.equals(g.notes, copy.notes), "Underlying suppressed notes persisted");
        check(copy.autoNotes && copy.showMistakes && !copy.noteMode && copy.selected == -1
                && copy.elapsedMillis == 123456, "Settings, selection and time persist");
        check(copy.armedDigit == 6, "Armed digit survives process recreation");
        check(copy.undo() && copy.values[8] == 0, "Undo history persisted");
        check(copy.visibleNotes(7) == (Sudoku.ALL & ~Sudoku.bit(4)), "Restore peer note without resurrecting manual deletion");
        byte[] corrupt = encode(g); corrupt[0] = 0;
        boolean rejected = false;
        try { Game.read(new DataInputStream(new ByteArrayInputStream(corrupt))); }
        catch (IOException ex) { rejected = true; }
        check(rejected, "Corrupt save rejected explicitly");
        for (int i = 0; i < 600; i++) { g.selected = 80; g.enter(i % 2 == 0 ? 3 : 5); }
        copy = Game.read(new DataInputStream(new ByteArrayInputStream(encode(g))));
        int undos = 0;
        while (copy.undo()) undos++;
        check(undos == 500, "Undo bound and persistence contract");
    }

    private static void importedSaveCompatibility() throws Exception {
        int[] clues = "530070000600195000098000060800060003400803001700020006060000280000419005000080079"
                .chars().map(c -> c - '0').toArray();
        Game game = new Game(new Sudoku.Puzzle(clues, Sudoku.uniqueSolution(clues), Sudoku.Difficulty.HARD, null));
        game.setAutoNotes(true);
        game.selected = 2; game.enter(game.solution[2]);
        game.elapsedMillis = 91000;
        game.showMistakes = true;
        byte[] current = encode(game);
        Game restored = Game.read(new DataInputStream(new ByteArrayInputStream(current)));
        check(restored.difficulty == Sudoku.Difficulty.HARD && Arrays.equals(restored.clues, clues),
                "Imported active save keeps its exact givens and offered tier, without reclassification or a library");
        check(Arrays.equals(current, encode(restored)), "Imported SUD3 save round-trips byte-for-byte");
        check(restored.undo() && restored.values[2] == 0 && restored.visibleNotes(2) != 0,
                "Imported active save retains exact Undo and notes");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream legacy = new DataOutputStream(bytes);
        legacy.writeInt(0x53554432); legacy.writeUTF("HARD");
        for (int[] array : new int[][]{game.clues, game.solution, game.values, game.notes})
            for (int value : array) legacy.writeShort(value);
        legacy.writeBoolean(game.autoNotes); legacy.writeBoolean(game.showMistakes); legacy.writeBoolean(game.noteMode);
        legacy.writeInt(game.selected); legacy.writeLong(game.elapsedMillis); legacy.writeInt(0);
        restored = Game.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        check(restored.difficulty == Sudoku.Difficulty.HARD && Arrays.equals(restored.clues, clues)
                && Arrays.equals(restored.values, game.values) && Arrays.equals(restored.notes, game.notes)
                && restored.elapsedMillis == 91000 && restored.armedDigit == 0,
                "Imported SUD2 active save remains readable without catalog data");
    }

    private static void digitSelection() {
        Game g = blank();
        g.selected = 0;
        g.toggleDigit(2);
        check(g.armedDigit == 2 && g.values[0] == 0, "Arming never writes into selected cell");
        check(g.selected == -1, "Arming clears square and house selection");
        check(g.selectCell(0) && g.values[0] == 2, "Armed cell tap enters digit");
        check(g.selected == -1, "Number-first placement never selects the square");
        check(g.armedDigit == 2, "Digit stays armed for repeated entry");
        g.toggleDigit(4);
        check(g.armedDigit == 4 && g.values[0] == 2, "Different bank digit switches without writing");
        check(g.selected == -1, "Switching bank digit clears square selection");
        g.selectCell(1);
        check(g.values[1] == 4, "Switched digit enters next tapped cell");
        g.toggleDigit(4);
        check(g.armedDigit == 0, "Tapping same bank digit cancels");
        check(g.selected == -1, "Cancelling bank digit clears square selection");
        check(!g.selectCell(2) && g.values[2] == 0 && g.selected == 2, "Unarmed tap selects without entering");
        g.noteMode = true; g.toggleDigit(5);
        g.selectCell(2);
        check(g.values[2] == 0 && g.visibleNotes(2) == Sudoku.bit(5), "Armed input respects notes mode");
        g.selectCell(2);
        check(g.notes[2] == 0, "Repeated armed note tap toggles it off");
        int[] clues = new int[81]; clues[0] = g.solution[0];
        Game fixed = new Game(new Sudoku.Puzzle(clues, g.solution, Sudoku.Difficulty.FOCUSED, null));
        fixed.toggleDigit(9);
        check(!fixed.selectCell(0) && fixed.values[0] == clues[0] && fixed.armedDigit == 9
                && fixed.selected == -1, "Armed given-cell taps never select or mutate and keep digit armed");
    }

    private static void cellFirstInput() {
        Game g = blank();
        g.setAutoNotes(true);
        g.selectCell(0);
        check(g.chooseDigit(2) && g.values[0] == 2, "Cell-first bank entry fills selected square");
        check(g.selected == -1 && g.armedDigit == 0, "One-shot entry clears selection and does not arm stale input");
        g.selectCell(1);
        int[] values = g.values.clone(), notes = g.notes.clone();
        check(!g.chooseDigit(2) && Arrays.equals(values, g.values) && Arrays.equals(notes, g.notes),
                "Cell-first conflict leaves values and note provenance untouched");
        check(g.undo() && g.values[0] == 0, "Conflict adds no undo action");
        g.armedDigit = 0; g.selected = -1;
        g.chooseDigit(3); g.selectCell(0);
        check(g.values[0] == 3 && g.armedDigit == 3, "Number-first still supports repeated placement");
        check(!g.chooseDigit(4) && g.values[0] == 3 && g.armedDigit == 4 && g.selected == -1,
                "Bank switches after number-first placement without overwriting prior target");
        g.chooseDigit(4);
        g.setNoteMode(true);
        g.selectCell(1); int kept = g.notes[1];
        check(g.chooseDigit(5) && g.notes[1] == (kept ^ Sudoku.bit(5)) && g.values[1] == 0,
                "Cell-first Notes toggles only the requested note");
        g.selectCell(1); g.setNoteMode(false);
        check(g.values[1] == 0 && g.selected == 1, "Notes transition preserves explicit cell without writing");
        check(g.chooseDigit(6) && g.values[1] == 6, "Cell-first entry after mode switch uses current mode");
        g.selected = -1; g.chooseDigit(7); g.chooseDigit(7);
        check(g.armedDigit == 0, "Unselected same bank digit still cancels");
        g.chooseDigit(7); g.chooseDigit(8);
        check(g.armedDigit == 8, "Unselected different bank digit still switches");
        for (int i = 0; i < 81; i++) if (g.solution[i] == 9) g.values[i] = 9;
        g.armedDigit = 0; g.selected = 2;
        check(!g.chooseDigit(9) && g.selected == -1 && g.armedDigit == 0, "Exhausted digit never enters or arms");
        int[] clues = new int[81]; clues[0] = g.solution[0];
        Game fixed = new Game(new Sudoku.Puzzle(clues, g.solution, Sudoku.Difficulty.FOCUSED, null));
        fixed.selectCell(0); fixed.chooseDigit(5);
        check(fixed.values[0] == clues[0] && fixed.selected == -1 && fixed.armedDigit == 5,
                "Given selection never writes; bank can still arm number-first input");
        Game sequence = blank();
        sequence.chooseDigit(6);
        check(sequence.selectCell(0) && sequence.values[0] == 6 && sequence.selected == -1,
                "Exact regression: arm 6 then place A without selection");
        sequence.chooseDigit(7);
        check(sequence.armedDigit == 7 && sequence.values[0] == 6, "Exact regression: 7 switches without replacing A");
        check(sequence.selectCell(1) && sequence.values[1] == 7 && sequence.selected == -1,
                "Exact regression: B receives 7 without selection");
        check(!sequence.selectCell(2) && sequence.selected == -1 && sequence.values[2] == 0,
                "Armed rejection never selects target");
        check(sequence.undo() && sequence.selected == -1 && sequence.values[1] == 0,
                "Undo cannot resurrect an implicit number-first selection");
    }

    private static void exhaustedDigitsAndModes() throws Exception {
        Game g = blank();
        g.toggleDigit(4);
        g.selectCell(0);
        int[] before = g.values.clone();
        g.setNoteMode(true);
        check(g.armedDigit == 4 && g.noteMode && Arrays.equals(g.values, before),
                "Mode switch preserves armed digit and never writes");
        g.selectCell(40);
        check(g.values[40] == 0 && g.visibleNotes(40) == Sudoku.bit(4), "Armed note tap edits only notes");
        g.setNoteMode(false);
        check(g.armedDigit == 4 && !g.noteMode && g.values[40] == 0, "Switch back does not place stale note");
        g.selectCell(40);
        check(g.values[40] == 4 && g.visibleNotes(40) == 0, "Next cell tap explicitly places number");
        g.selected = 40; g.clear();
        check(g.visibleNotes(40) == Sudoku.bit(4), "Switching modes preserves underlying notes");
        g.setNoteMode(true);
        g.selectCell(40);
        check(g.notes[40] == 0 && g.values[40] == 0, "Returning to notes can remove exact prior note");
        g.setNoteMode(false);
        int last = -1;
        for (int i = 1; i < 81; i++) if (g.solution[i] == 1) { g.selectCell(i); last = i; }
        check(g.remaining(4) == 0 && g.armedDigit == 0, "Last digit placement automatically disarms");
        check(g.selected == -1, "Exhaustion clears square and matching-digit highlights");
        g.toggleDigit(4);
        check(g.armedDigit == 0, "Exhausted digit cannot be selected");
        g.selected = 50;
        check(!g.enter(4) && g.values[50] == 0, "Direct/physical input also rejects exhausted digit");
        g.setNoteMode(true);
        g.toggleDigit(4);
        check(g.armedDigit == 0 && !g.enter(4), "Exhaustion applies in notes mode too");
        g.setNoteMode(false); g.selected = last; g.clear();
        check(g.remaining(4) == 1, "Clear re-enables bank digit");
        g.toggleDigit(4);
        check(g.armedDigit == 4, "Cleared digit selectable again");
        g.undo();
        check(g.remaining(4) == 0 && g.armedDigit == 0, "Undo clear that re-exhausts automatically disarms");
        check(g.selected == -1, "Undo that exhausts armed digit clears selection");
        g.undo();
        check(g.remaining(4) == 1, "Undo placement re-enables bank digit");
        g.toggleDigit(4);
        Game restored = Game.read(new DataInputStream(new ByteArrayInputStream(encode(g))));
        check(restored.armedDigit == 4 && restored.remaining(4) == 1, "Available armed digit persists");
        g.values[last] = 4;
        restored = Game.read(new DataInputStream(new ByteArrayInputStream(encode(g))));
        check(restored.armedDigit == 0, "Legacy/stale exhausted selection disarmed on restore");
        Game almost = blank();
        System.arraycopy(almost.solution, 0, almost.values, 0, 81);
        almost.values[80] = 0;
        almost.toggleDigit(almost.solution[80]);
        almost.selectCell(80);
        check(almost.won() && almost.armedDigit == 0, "Final correct entry wins and clears number mode");
        almost.selected = 0;
        check(!almost.enter(2), "Completed game cannot be accidentally modified");
        check(almost.undo() && !almost.won(), "Engine can undo final entry exactly");
    }

    private static void duplicatePrevention() throws Exception {
        for (boolean mistakes : new boolean[]{false, true}) {
            for (int peer : new int[]{8, 72, 10}) {
                Game g = blank();
                g.showMistakes = mistakes;
                g.setAutoNotes(true);
                g.selected = peer;
                check(g.enter(9), "Fixture places peer blocker");
                g.toggleDigit(9);
                g.selected = 0;
                byte[] before = encode(g);
                check(!g.enter(9), "Reject row/column/box duplicate regardless of mistakes");
                check(Arrays.equals(before, encode(g)), "Rejected entry changes no values notes history counts or input state");
                check(g.enter(8), "Locally legal incorrect solution value remains allowed");
                check(!g.enter(8), "Same value is a harmless no-op");
                check(g.enter(7), "Replacement excludes target itself");
                g.setNoteMode(true);
                g.clear();
                check(!g.enter(9), "Blocked note cannot be added");
            }
        }
    }

    private static void generation() {
        Set<String> seen = new HashSet<>();
        long start = System.currentTimeMillis();
        for (Sudoku.Difficulty difficulty : Sudoku.Difficulty.values()) {
            for (int i = 0; i < 12; i++) {
                long before = System.currentTimeMillis();
                int seedTier = difficulty == Sudoku.Difficulty.HARD ? 1 : difficulty == Sudoku.Difficulty.MEDIUM ? 2 : 0;
                Sudoku.Puzzle puzzle = Sudoku.generate(difficulty, new Random(41000L + seedTier * 100 + i));
                check(seen.add(Arrays.toString(puzzle.clues)), "Fresh independent puzzles");
                check(Sudoku.valid(puzzle.solution) && Arrays.stream(puzzle.solution).noneMatch(v -> v == 0), "Valid complete solution");
                check(Sudoku.solutions(puzzle.clues, 2) == 1, "Exactly one solution");
                for (int c = 0; c < 81; c++) {
                    check(puzzle.clues[c] == 0 || puzzle.clues[c] == puzzle.solution[c], "Clues agree with solution");
                }
                Sudoku.Rating hidden = Sudoku.rate(puzzle.clues, false);
                Sudoku.Rating combined = Sudoku.rate(puzzle.clues, true);
                if (difficulty == Sudoku.Difficulty.FOCUSED) {
                    check(hidden.solved && hidden.nakedSingles == 0, "Easy solved by hidden singles alone");
                    long clues = Arrays.stream(puzzle.clues).filter(value -> value != 0).count();
                    check(clues >= 36 && clues <= 40, "Generated Easy has gentler 36-40 clue density");
                    check(DifficultyTests.openingHiddenSingles(puzzle.clues) >= 8,
                            "Generated Easy offers at least eight distinct immediate hidden-single cells");
                    DifficultyTests.verifyPuzzle(puzzle);
                } else if (difficulty == Sudoku.Difficulty.MEDIUM) {
                    check(!hidden.solved && combined.solved && combined.nakedSingles > 0,
                            "Medium requires naked singles in addition to hidden singles");
                } else {
                    check(!hidden.solved && !combined.solved && combined.remaining > 0,
                            "Unique Hard puzzle stalls at the combined fixed point");
                }
                System.out.printf("%s seed %d: %d clues, %d hidden, %d naked, %d unfilled, %d ms%n",
                        difficulty, i, Arrays.stream(puzzle.clues).filter(v -> v != 0).count(),
                        puzzle.rating.hiddenSingles, puzzle.rating.nakedSingles, puzzle.rating.remaining, System.currentTimeMillis() - before);
            }
        }
        check(System.currentTimeMillis() - start < 120000, "Batch generation stays within two minutes");
    }
}
