package com.quietgrid.sudoku.puzzles;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public final class Nonogram implements UndoablePuzzle {
    public static final int N = 5;
    public final int[][] rows, columns;
    public final int[] values;
    Nonogram(int[][] rows, int[][] columns, int[] values) {
        this.rows = copy(rows); this.columns = copy(columns); this.values = values.clone();
    }
    private static int[][] copy(int[][] a) { return Arrays.stream(a).map(int[]::clone).toArray(int[][]::new); }
    @Override public Mode mode() { return Mode.NONOGRAM; }
    @Override public int size() { return rows.length; }
    @Override public int[] entries() { return values; }
    @Override public Nonogram copy() { return new Nonogram(rows, columns, values); }
    public void cycle(int cell) { if (!solved()) values[cell] = (values[cell] + 1) % 3; }
    public void mark(int cell, int mark) {
        if (mark != 1 && mark != 2) throw new IllegalArgumentException("Expected fill or cross.");
        if (!solved()) values[cell] = values[cell] == mark ? 0 : mark;
    }
    public void paint(java.util.BitSet cells, int mark) {
        if (mark != 1 && mark != 2) throw new IllegalArgumentException("Expected fill or cross.");
        if (solved()) return;
        for (int cell = cells.nextSetBit(0); cell >= 0; cell = cells.nextSetBit(cell + 1))
            if (values[cell] == 0) values[cell] = mark;
    }
    @Override public boolean solved() {
        int n = size();
        for (int i = 0; i < n; i++) {
            if (!lineSatisfied(i, false) || !lineSatisfied(i, true)) return false;
        }
        return true;
    }

    /** Filled runs match the public clue; unfilled squares need not be crossed. */
    public boolean lineSatisfied(int index, boolean column) {
        return Arrays.equals((column ? columns : rows)[index], runs(line(values, size(), index, column), size()));
    }

    /** Rows first, then columns; undecided squares constrain neither fill nor empty. */
    public boolean[] contradictions() {
        int n = size();
        boolean[] errors = new boolean[n * 2];
        for (int axis = 0; axis < 2; axis++) {
            boolean column = axis == 1;
            int[][] options = patterns(column ? columns : rows);
            for (int index = 0; index < n; index++) {
                int filled = line(values, n, index, column), crossed = 0;
                for (int i = 0; i < n; i++) if (values[column ? i * n + index : index * n + i] == 2) crossed |= 1 << i;
                boolean possible = false;
                for (int candidate : options[index]) {
                    if ((candidate & filled) == filled && (candidate & crossed) == 0) { possible = true; break; }
                }
                errors[axis * n + index] = !possible;
            }
        }
        return errors;
    }

    public static int[] runs(int mask) {
        return runs(mask, N);
    }
    public static int[] runs(int mask, int size) {
        List<Integer> result = new ArrayList<>(); int length = 0;
        for (int i = 0; i <= size; i++) {
            if (i < size && (mask & 1 << i) != 0) length++;
            else if (length > 0) { result.add(length); length = 0; }
        }
        return result.stream().mapToInt(Integer::intValue).toArray();
    }
    private static int line(int[] values, int size, int index, boolean column) {
        int mask = 0;
        for (int i = 0; i < size; i++) if (values[column ? i * size + index : index * size + i] == 1) mask |= 1 << i;
        return mask;
    }
    private static int[][] patterns(int[][] clues) {
        int[][] result = new int[clues.length][];
        for (int i = 0; i < clues.length; i++) {
            List<Integer> list = new ArrayList<>();
            placements(clues[i], 0, 0, 0, clues.length, list);
            result[i] = list.stream().mapToInt(Integer::intValue).toArray();
        }
        return result;
    }
    private static void placements(int[] clues, int run, int start, int mask, int size, List<Integer> result) {
        if (run == clues.length) { result.add(mask); return; }
        int needed = clues.length - run - 1;
        for (int i = run; i < clues.length; i++) needed += clues[i];
        for (int position = start; position + needed <= size; position++)
            placements(clues, run + 1, position + clues[run] + 1,
                    mask | ((1 << clues[run]) - 1) << position, size, result);
    }
    public static int count(int[][] rows, int[][] columns, int limit, Generation budget) {
        return search(patterns(rows), patterns(columns), new int[rows.length], 0, limit, budget);
    }
    private static int search(int[][] rows, int[][] columns, int[] selected, int row, int limit, Generation budget) {
        budget.tick();
        if (row == rows.length) return 1;
        int count = 0;
        for (int mask : rows[row]) {
            budget.tick();
            selected[row] = mask; boolean possible = true;
            for (int col = 0; col < columns.length && possible; col++) {
                boolean match = false;
                for (int candidate : columns[col]) {
                    boolean prefix = true;
                    for (int r = 0; r <= row; r++) if ((selected[r] >> col & 1) != (candidate >> r & 1)) { prefix = false; break; }
                    if (prefix) { match = true; break; }
                }
                possible = match;
            }
            if (possible) count += search(rows, columns, selected, row + 1, limit - count, budget);
            if (count >= limit) break;
        }
        return count;
    }
    public static Nonogram generate(Random random, Generation budget) {
        return generate(N, random, budget);
    }
    public static Nonogram generate(int size, Random random, Generation budget) {
        if (size != 5 && size != 8 && size != 10) throw new IllegalArgumentException("Choose a 5x5, 8x8 or 10x10 Nonogram.");
        for (int attempt = 0; attempt < 160; attempt++) {
            budget.tick();
            int[] picture = new int[size * size]; int filled = 0;
            for (int i = 0; i < picture.length; i++) { picture[i] = random.nextBoolean() ? 1 : 0; filled += picture[i]; }
            if (filled < picture.length * .28 || filled > picture.length * .76) continue;
            int[][] rows = new int[size][], columns = new int[size][];
            for (int i = 0; i < size; i++) {
                rows[i] = runs(line(picture, size, i, false), size);
                columns[i] = runs(line(picture, size, i, true), size);
            }
            if (count(rows, columns, 2, budget) == 1) return new Nonogram(rows, columns, new int[size * size]);
        }
        throw new IllegalStateException("Could not certify a unique Nonogram. Please retry.");
    }
    @Override public void write(DataOutputStream out) throws IOException {
        if (size() != N) { out.writeInt(-1); out.writeInt(size()); }
        for (int[][] axis : new int[][][]{rows, columns}) for (int[] clue : axis) {
            out.writeInt(clue.length); PuzzleStore.array(out, clue);
        }
        PuzzleStore.array(out, values);
    }
    static Nonogram read(DataInputStream in) throws IOException {
        int first = in.readInt(), size = N;
        if (first == -1) {
            size = in.readInt();
            if (size != 8 && size != 10) throw new IOException("Unknown Nonogram size.");
            first = in.readInt();
        }
        int[][][] axes = new int[2][size][];
        for (int axis = 0; axis < 2; axis++) for (int i = 0; i < size; i++) {
            int count = axis == 0 && i == 0 ? first : in.readInt();
            if (count < 0 || count > (size + 1) / 2) throw new IOException("Invalid run count.");
            axes[axis][i] = PuzzleStore.array(in, count, 1, size);
            if (Arrays.stream(axes[axis][i]).sum() + count - 1 > size) throw new IOException("Run clues do not fit.");
        }
        return new Nonogram(axes[0], axes[1], PuzzleStore.array(in, size * size, 0, 2));
    }
}
