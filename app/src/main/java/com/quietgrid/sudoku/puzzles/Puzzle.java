package com.quietgrid.sudoku.puzzles;

import java.io.DataOutputStream;
import java.io.IOException;

public interface Puzzle {
    Mode mode();
    int size();
    boolean solved();
    default boolean lost() { return false; }
    Puzzle copy();
    void write(DataOutputStream out) throws IOException;
}
