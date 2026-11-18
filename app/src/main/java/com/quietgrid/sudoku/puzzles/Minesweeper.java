package com.quietgrid.sudoku.puzzles;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Random;

public final class Minesweeper implements Puzzle {
    public static final int N = 8, MINES = 10;
    public final int[] mines = new int[64], cells = new int[64];
    public final int mineCount;
    private final long seed;
    public int first = -1;
    public Minesweeper(long seed) { this(seed, MINES); }
    public Minesweeper(long seed, int mineCount) {
        if (mineCount != MINES && mineCount != 14) throw new IllegalArgumentException("Choose ten or fourteen mines.");
        this.seed = seed; this.mineCount = mineCount;
    }
    @Override public Mode mode() { return Mode.MINESWEEPER; }
    @Override public int size() { return N; }
    @Override public Minesweeper copy() {
        Minesweeper copy = new Minesweeper(seed, mineCount); copy.first = first;
        System.arraycopy(mines, 0, copy.mines, 0, 64); System.arraycopy(cells, 0, copy.cells, 0, 64); return copy;
    }
    @Override public boolean lost() {
        for (int i = 0; i < 64; i++) if (mines[i] == 1 && cells[i] == 1) return true;
        return false;
    }
    @Override public boolean solved() {
        if (first < 0 || lost()) return false;
        for (int i = 0; i < 64; i++) if (mines[i] == 0 && cells[i] != 1) return false;
        return true;
    }
    public int flags() { return (int) Arrays.stream(cells).filter(v -> v == 2).count(); }
    public int adjacent(int cell) {
        int count = 0;
        for (int i = 0; i < 64; i++) if (i != cell && near(i, cell)) count += mines[i];
        return count;
    }
    private static boolean near(int a, int b) { return Math.abs(a / N - b / N) <= 1 && Math.abs(a % N - b % N) <= 1; }
    public String tap(int cell, boolean flag) {
        if (solved() || lost()) return null;
        if (cells[cell] == 1) {
            if (!flag && adjacent(cell) > 0) {
                int flags = 0;
                for (int i = 0; i < 64; i++) if (i != cell && near(i, cell) && cells[i] == 2) flags++;
                if (flags == adjacent(cell))
                    for (int i = 0; i < 64; i++) if (i != cell && near(i, cell) && cells[i] == 0) reveal(i);
            }
            return null;
        }
        if (flag) {
            if (cells[cell] == 2) cells[cell] = 0;
            else if (flags() >= mineCount) return "All " + mineCount + " flags are placed. Remove a flag first.";
            else cells[cell] = 2;
            return null;
        }
        if (cells[cell] == 2) return "Remove this flag before revealing the square.";
        if (first < 0) {
            first = cell; int count = 0;
            for (int candidate : Generation.order(64, new Random(seed))) {
                if (!near(candidate, cell)) { mines[candidate] = 1; if (++count == mineCount) break; }
            }
        }
        reveal(cell);
        return null;
    }
    private void reveal(int cell) {
        cells[cell] = 1;
        if (mines[cell] == 1) return;
        ArrayDeque<Integer> queue = new ArrayDeque<>(); queue.add(cell);
        while (!queue.isEmpty()) {
            int current = queue.remove();
            if (adjacent(current) != 0) continue;
            for (int i = 0; i < 64; i++) if (cells[i] == 0 && mines[i] == 0 && near(current, i)) {
                cells[i] = 1; queue.add(i);
            }
        }
    }
    @Override public void write(DataOutputStream out) throws IOException {
        out.writeLong(seed); out.writeInt(first); PuzzleStore.array(out, mines); PuzzleStore.array(out, cells);
        if (mineCount != MINES) { out.writeInt(1); out.writeInt(mineCount); }
    }
    static Minesweeper read(DataInputStream in) throws IOException {
        long seed = in.readLong(); int first = in.readInt();
        if (first < -1 || first >= 64) throw new IOException("Invalid first reveal.");
        int[] mines = PuzzleStore.array(in, 64, 0, 1), cells = PuzzleStore.array(in, 64, 0, 2);
        int mineCount = MINES;
        if (in.available() != 0) {
            if (in.available() != 8 || in.readInt() != 1) throw new IOException("Unknown mine settings version.");
            mineCount = in.readInt();
            if (mineCount != 14) throw new IOException("Invalid mine setting.");
        }
        Minesweeper result = new Minesweeper(seed, mineCount); result.first = first;
        System.arraycopy(mines, 0, result.mines, 0, 64); System.arraycopy(cells, 0, result.cells, 0, 64);
        if (result.flags() > mineCount || Arrays.stream(mines).sum() != (result.first < 0 ? 0 : mineCount))
            throw new IOException("Invalid mine or flag count.");
        if (result.first < 0 && Arrays.stream(cells).anyMatch(v -> v == 1)) throw new IOException("Reveal before placement.");
        if (result.first >= 0 && (mines[result.first] != 0 || result.adjacent(result.first) != 0 || cells[result.first] != 1))
            throw new IOException("First reveal is not safe.");
        return result;
    }
}
