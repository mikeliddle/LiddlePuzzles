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

    public PuzzleProgress(Puzzle game) { this(game, new ArrayList<>()); }
    PuzzleProgress(Puzzle game, List<int[]> history) { this(game, history, 0); }
    private PuzzleProgress(Puzzle game, List<int[]> history, long elapsedMillis) {
        this.game = game; this.history = history; this.elapsedMillis = elapsedMillis;
    }
    public PuzzleProgress withElapsed(long millis) {
        if (millis < 0) throw new IllegalArgumentException("Negative play time.");
        return millis == elapsedMillis ? this : new PuzzleProgress(game, history, millis);
    }
    public boolean canUndo() { return !history.isEmpty(); }
    public int undoCount() { return history.size(); }
    List<int[]> history() { return history; }

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
        return new PuzzleProgress(next, prior, elapsedMillis);
    }

    public PuzzleProgress undo() {
        if (!canUndo()) return this;
        Puzzle restored = game.copy();
        int[] entries = ((UndoablePuzzle) restored).entries();
        System.arraycopy(history.get(history.size() - 1), 0, entries, 0, entries.length);
        return new PuzzleProgress(restored, new ArrayList<>(history.subList(0, history.size() - 1)), elapsedMillis);
    }
}
