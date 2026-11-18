package com.quietgrid.sudoku.puzzles;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

public final class Takuzu implements UndoablePuzzle {
    public final int[] clues, values;
    private Takuzu(int[] clues, int[] values) { this.clues = clues.clone(); this.values = values.clone(); }
    @Override public Mode mode() { return Mode.TAKUZU; }
    @Override public int size() { return dimension(values.length); }
    @Override public int[] entries() { return values; }
    @Override public boolean solved() { return Arrays.stream(values).noneMatch(v -> v < 0) && valid(values); }
    @Override public Takuzu copy() { return new Takuzu(clues, values); }
    public void cycle(int cell) {
        if (!solved() && clues[cell] < 0) values[cell] = values[cell] == 1 ? -1 : values[cell] + 1;
    }
    public void enter(int cell, int digit) {
        if (digit < -1 || digit > 1) throw new IllegalArgumentException("Takuzu entry must be empty, 0 or 1.");
        if (!solved() && clues[cell] < 0) values[cell] = digit;
    }

    public static boolean valid(int[] values) {
        int N = dimension(values.length);
        if (N == 0) return false;
        for (int v : values) if (v < -1 || v > 1) return false;
        for (int axis = 0; axis < 2; axis++) for (int line = 0; line < N; line++) {
            int zero = 0, one = 0;
            boolean full = true;
            for (int i = 0; i < N; i++) {
                int v = values[axis == 0 ? line * N + i : i * N + line];
                if (v == 0) zero++; else if (v == 1) one++; else full = false;
                if (i >= 2 && v >= 0
                        && values[axis == 0 ? line * N + i - 1 : (i - 1) * N + line] == v
                        && values[axis == 0 ? line * N + i - 2 : (i - 2) * N + line] == v) return false;
            }
            if (zero > N / 2 || one > N / 2) return false;
            if (full) for (int previous = 0; previous < line; previous++) {
                boolean same = true;
                for (int i = 0; i < N; i++) if (values[axis == 0 ? line * N + i : i * N + line]
                        != values[axis == 0 ? previous * N + i : i * N + previous]) { same = false; break; }
                if (same) return false;
            }
        }
        return true;
    }

    private static int dimension(int length) { return length == 36 ? 6 : length == 64 ? 8 : 0; }

    public String[] errors() {
        int n = size(); String[] errors = new String[values.length];
        for (int axis = 0; axis < 2; axis++) for (int line = 0; line < n; line++) {
            String house = (axis == 0 ? "row " : "column ") + (line + 1);
            int[] cells = new int[n]; int[] counts = new int[2]; boolean full = true;
            for (int i = 0; i < n; i++) {
                cells[i] = axis == 0 ? line * n + i : i * n + line;
                int value = values[cells[i]];
                if (value >= 0) counts[value]++; else full = false;
                if (i >= 2 && value >= 0 && values[cells[i - 1]] == value && values[cells[i - 2]] == value)
                    for (int k = i - 2; k <= i; k++)
                        error(errors, cells[k], "Three consecutive " + value + "s in " + house + ".");
            }
            for (int value = 0; value < 2; value++) if (counts[value] > n / 2)
                for (int cell : cells) if (values[cell] == value)
                    error(errors, cell, "Too many " + value + "s in " + house + "; maximum " + (n / 2) + ".");
            if (full) for (int previous = 0; previous < line; previous++) {
                boolean same = true;
                for (int i = 0; i < n; i++) if (values[cells[i]]
                        != values[axis == 0 ? previous * n + i : i * n + previous]) { same = false; break; }
                if (same) for (int i = 0; i < n; i++) {
                    String reason = (axis == 0 ? "Rows " : "Columns ") + (previous + 1) + " and " + (line + 1) + " are identical.";
                    error(errors, cells[i], reason);
                    error(errors, axis == 0 ? previous * n + i : i * n + previous, reason);
                }
            }
        }
        return errors;
    }

    private static void error(String[] errors, int cell, String reason) {
        if (errors[cell] == null) errors[cell] = reason;
        else if (!errors[cell].contains(reason)) errors[cell] += " " + reason;
    }

    public static int count(int[] clues, int limit, Generation budget) {
        if (!valid(clues)) return 0;
        return search(clues.clone(), limit, budget, null);
    }

    private static int search(int[] v, int limit, Generation budget, Random random) {
        budget.tick();
        int cell = -1;
        for (int i = 0; i < v.length; i++) if (v[i] < 0) { cell = i; break; }
        if (cell < 0) return 1;
        int found = 0, first = random != null && random.nextBoolean() ? 1 : 0;
        for (int d = 0; d < 2; d++) {
            v[cell] = first ^ d;
            if (valid(v)) found += search(v, limit - found, budget, random);
            if (found >= limit) { if (random == null) v[cell] = -1; return found; }
        }
        v[cell] = -1;
        return found;
    }

    public static Takuzu generate(Random random, Generation budget) {
        return generate(8, random, budget);
    }

    public static Takuzu generate(int N, Random random, Generation budget) {
        if (N != 6 && N != 8) throw new IllegalArgumentException("Takuzu size must be 6 or 8.");
        int[] clues = new int[N * N]; Arrays.fill(clues, -1);
        if (search(clues, 1, budget, random) != 1) throw new IllegalStateException("Could not create Takuzu.");
        for (int cell : Generation.order(clues.length, random)) {
            int old = clues[cell]; clues[cell] = -1;
            if (count(clues, 2, budget) != 1) clues[cell] = old;
        }
        return new Takuzu(clues, clues);
    }

    @Override public void write(DataOutputStream out) throws IOException {
        if (size() == 8) out.writeInt(-8);
        PuzzleStore.array(out, clues); PuzzleStore.array(out, values);
    }
    static Takuzu read(DataInputStream in) throws IOException {
        int first = in.readInt(), length = first == -8 ? 64 : 36;
        int[] clues = new int[length];
        // Legacy 6x6 saves begin directly with their first clue; -8 is outside its value range.
        int start = first == -8 ? 0 : 1;
        if (start == 1) clues[0] = first;
        for (int i = start; i < length; i++) clues[i] = in.readInt();
        for (int clue : clues) if (clue < -1 || clue > 1) throw new IOException("Takuzu clue out of range.");
        int[] values = PuzzleStore.array(in, length, -1, 1);
        if (!valid(clues)) throw new IOException("Invalid Takuzu clues.");
        for (int i = 0; i < length; i++) if (clues[i] >= 0 && values[i] != clues[i]) throw new IOException("Changed clue.");
        return new Takuzu(clues, values);
    }
}
