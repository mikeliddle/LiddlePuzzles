package com.quietgrid.sudoku.puzzles;

import java.util.Arrays;

/** Local, visible-rule satisfaction only; never a search or a hidden-answer comparison. */
public final class PuzzleMilestones {
    private PuzzleMilestones() {}

    public static boolean[] newlySatisfied(UndoablePuzzle before, UndoablePuzzle after) {
        boolean[] previous = satisfied(before), result = satisfied(after), touched = new boolean[result.length];
        int n = after.size();
        for (int entry = 0; entry < after.entries().length; entry++) if (before.entries()[entry] != after.entries()[entry]) {
            if (after instanceof Slitherlink) {
                Slitherlink loop = (Slitherlink) after;
                for (int clue = 0; clue < loop.clues.length; clue++)
                    for (int side : loop.sides[clue]) if (side == entry) touched[clue] = true;
            } else if (after instanceof Numberlink) {
                Numberlink paths = (Numberlink) after;
                int pair = paths.pairAt(paths.grid.a[entry]);
                if (pair > 0) touched[pair] = true;
            } else {
                touched[entry / n] = true; touched[n + entry % n] = true;
            }
        }
        for (int i = 0; i < result.length; i++) result[i] &= !previous[i] && touched[i];
        return result;
    }

    public static boolean[] satisfied(UndoablePuzzle puzzle) {
        int n = puzzle.size();
        if (puzzle instanceof Slitherlink) {
            Slitherlink loop = (Slitherlink) puzzle;
            boolean[] result = new boolean[loop.clues.length];
            if (loop.errors().any()) return result;
            for (int cell = 0; cell < result.length; cell++) {
                int count = 0;
                for (int edge : loop.sides[cell]) if (loop.edges[edge] == 1) count++;
                result[cell] = loop.clues[cell] >= 0 && count == loop.clues[cell];
            }
            return result;
        }
        if (puzzle instanceof Numberlink) {
            Numberlink paths = (Numberlink) puzzle;
            boolean[] result = new boolean[paths.pairs() + 1];
            for (int cell = 0; cell < paths.endpoints.length; cell++) if (paths.endpoints[cell] > 0) {
                boolean[] part = paths.grid.component(paths.edges, cell);
                result[paths.endpoints[cell]] |= Numberlink.validPairComponent(paths.grid, paths.endpoints, paths.edges, part);
            }
            return result;
        }
        boolean[] result = new boolean[n * 2];
        String[] errors = puzzle instanceof Takuzu ? ((Takuzu) puzzle).errors()
                : puzzle instanceof Futoshiki ? ((Futoshiki) puzzle).errors() : null;
        boolean[] contradictions = puzzle instanceof Nonogram ? ((Nonogram) puzzle).contradictions() : null;
        for (int axis = 0; axis < 2; axis++) for (int line = 0; line < n; line++) {
            boolean valid = true; int mask = 0;
            for (int i = 0; i < n; i++) {
                int cell = axis == 0 ? line * n + i : i * n + line;
                int value = puzzle.entries()[cell];
                if (puzzle instanceof Nonogram) {
                    valid &= value != 0 && !contradictions[cell / n] && !contradictions[n + cell % n];
                    if (value == 1) mask |= 1 << i;
                } else valid &= errors != null && errors[cell] == null
                        && value >= (puzzle instanceof Takuzu ? 0 : 1);
            }
            if (puzzle instanceof Nonogram) {
                Nonogram picture = (Nonogram) puzzle;
                valid &= Arrays.equals(Nonogram.runs(mask, n), axis == 0 ? picture.rows[line] : picture.columns[line]);
            }
            result[axis * n + line] = valid;
        }
        return result;
    }
}
