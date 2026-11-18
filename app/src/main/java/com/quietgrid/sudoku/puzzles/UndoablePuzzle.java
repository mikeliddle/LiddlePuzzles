package com.quietgrid.sudoku.puzzles;

/** Reversible user entries only; fixed clues and puzzle definitions are excluded. */
public interface UndoablePuzzle extends Puzzle {
    int[] entries();
}
