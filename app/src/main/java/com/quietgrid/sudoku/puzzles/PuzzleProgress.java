package com.quietgrid.sudoku.puzzles;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** A prospective board and its bounded history are committed together, never separately. */
public final class PuzzleProgress {
    public static final int MAX_UNDO = 500;
    public final Puzzle game;
    public final long elapsedMillis;
    private final List<int[]> history;
    private final List<int[]> redo;

    public PuzzleProgress(Puzzle game) { this(game, List.of(), List.of(), 0); }
    PuzzleProgress(Puzzle game, List<int[]> history, List<int[]> redo, long elapsedMillis) {
        this.game = game; this.history = history; this.redo = redo; this.elapsedMillis = elapsedMillis;
    }
    public PuzzleProgress withElapsed(long millis) {
        if (millis < 0) throw new IllegalArgumentException("Negative play time.");
        return millis == elapsedMillis ? this : new PuzzleProgress(game, history, redo, millis);
    }
    public boolean canUndo() { return !history.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }
    public int undoCount() { return history.size(); }
    List<int[]> history() { return history; }
    List<int[]> redoHistory() { return redo; }

    public PuzzleProgress edited(Puzzle next) {
        if (game.mode() != next.mode()) throw new IllegalArgumentException("Cannot mix puzzle modes.");
        if (!(game instanceof UndoablePuzzle)) return new PuzzleProgress(next).withElapsed(elapsedMillis);
        if (!(next instanceof UndoablePuzzle)) throw new IllegalArgumentException("Missing reversible entries.");
        int[] before = ((UndoablePuzzle) game).entries(), after = ((UndoablePuzzle) next).entries();
        if (before.length != after.length) throw new IllegalArgumentException("Cannot change puzzle size during an edit.");
        if (Arrays.equals(before, after)) return this;
        List<int[]> prior = new ArrayList<>(history);
        if (prior.size() == MAX_UNDO) prior.remove(0);
        prior.add(before.clone());
        return new PuzzleProgress(next, prior, List.of(), elapsedMillis);
    }

    public PuzzleProgress undo() { return restore(history, redo, false); }
    public PuzzleProgress redo() { return restore(redo, history, true); }

    private PuzzleProgress restore(List<int[]> from, List<int[]> to, boolean forward) {
        if (from.isEmpty()) return this;
        Puzzle restored = game.copy();
        int[] entries = ((UndoablePuzzle) restored).entries();
        List<int[]> pushed = new ArrayList<>(to);
        pushed.add(entries.clone());
        System.arraycopy(from.get(from.size() - 1), 0, entries, 0, entries.length);
        List<int[]> remaining = new ArrayList<>(from.subList(0, from.size() - 1));
        return new PuzzleProgress(restored, forward ? pushed : remaining, forward ? remaining : pushed, elapsedMillis);
    }
}
