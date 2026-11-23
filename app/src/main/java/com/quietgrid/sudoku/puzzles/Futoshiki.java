package com.quietgrid.sudoku.puzzles;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

public final class Futoshiki implements UndoablePuzzle {
    public static final int N = 4;
    public final int[] clues, values, horizontal, vertical;
    Futoshiki(int[] clues, int[] values, int[] horizontal, int[] vertical) {
        this.clues = clues.clone(); this.values = values.clone();
        this.horizontal = horizontal.clone(); this.vertical = vertical.clone();
    }
    @Override public Mode mode() { return Mode.FUTOSHIKI; }
    @Override public int size() { return N; }
    @Override public int[] entries() { return values; }
    @Override public Futoshiki copy() { return new Futoshiki(clues, values, horizontal, vertical); }
    @Override public boolean solved() { return Arrays.stream(values).noneMatch(v -> v == 0) && valid(values, horizontal, vertical); }
    public void enter(int cell, int digit) { if (!solved() && clues[cell] == 0 && digit >= 0 && digit <= N) values[cell] = digit; }
    public String[] errors() {
        String[] errors = new String[values.length];
        for (int a = 0; a < values.length; a++) for (int b = a + 1; b < values.length; b++) {
            if (values[a] == 0 || values[a] != values[b]) continue;
            String house = a / N == b / N ? "row " + (a / N + 1)
                    : a % N == b % N ? "column " + (a % N + 1) : null;
            if (house != null) {
                String reason = "Repeated " + values[a] + " in " + house + ".";
                mark(errors, a, reason); mark(errors, b, reason);
            }
        }
        for (int row = 0; row < N; row++) for (int col = 0; col < N; col++) {
            int a = row * N + col;
            if (col < N - 1) checkRelation(errors, a, a + 1, horizontal[row * (N - 1) + col]);
            if (row < N - 1) checkRelation(errors, a, a + N, vertical[row * N + col]);
        }
        return errors;
    }
    private void checkRelation(String[] errors, int a, int b, int sign) {
        if (relation(values[a], values[b], sign)) return;
        String reason = "Inequality: row " + (a / N + 1) + " column " + (a % N + 1)
                + " must be " + (sign < 0 ? "smaller" : "larger") + " than row " + (b / N + 1)
                + " column " + (b % N + 1) + ".";
        mark(errors, a, reason); mark(errors, b, reason);
    }
    private static void mark(String[] errors, int cell, String reason) {
        errors[cell] = errors[cell] == null ? reason : errors[cell] + " " + reason;
    }
    public static boolean valid(int[] v, int[] h, int[] down) {
        if (v.length != 16 || h.length != 12 || down.length != 12) return false;
        for (int i = 0; i < 16; i++) {
            if (v[i] < 0 || v[i] > N) return false;
            if (v[i] == 0) continue;
            for (int j = 0; j < i; j++) if ((j / N == i / N || j % N == i % N) && v[j] == v[i]) return false;
        }
        for (int row = 0; row < N; row++) for (int col = 0; col < N - 1; col++)
            if (!relation(v[row * N + col], v[row * N + col + 1], h[row * (N - 1) + col])) return false;
        for (int row = 0; row < N - 1; row++) for (int col = 0; col < N; col++)
            if (!relation(v[row * N + col], v[(row + 1) * N + col], down[row * N + col])) return false;
        return true;
    }
    private static boolean relation(int a, int b, int sign) {
        return sign >= -1 && sign <= 1 && (sign == 0 || a == 0 || b == 0 || Integer.signum(a - b) == sign);
    }
    public static int count(int[] clues, int[] h, int[] down, int limit, Generation budget) {
        if (!valid(clues, h, down)) return 0;
        return search(clues.clone(), h, down, limit, budget, null);
    }
    private static int search(int[] v, int[] h, int[] down, int limit, Generation budget, Random random) {
        budget.tick();
        int cell = -1;
        for (int i = 0; i < v.length; i++) if (v[i] == 0) { cell = i; break; }
        if (cell < 0) return 1;
        int count = 0, offset = random == null ? 0 : random.nextInt(N);
        for (int d = 1; d <= N; d++) {
            v[cell] = (d - 1 + offset) % N + 1;
            if (valid(v, h, down)) count += search(v, h, down, limit - count, budget, random);
            if (count >= limit) { if (random == null) v[cell] = 0; return count; }
        }
        v[cell] = 0;
        return count;
    }
    public static Futoshiki generate(Random random, Generation budget) {
        int[] clues = new int[16], h = new int[12], down = new int[12];
        if (search(clues, h, down, 1, budget, random) != 1) throw new IllegalStateException("Could not create Latin square.");
        for (int row = 0; row < N; row++) for (int col = 0; col < N - 1; col++)
            h[row * (N - 1) + col] = Integer.signum(clues[row * N + col] - clues[row * N + col + 1]);
        for (int row = 0; row < N - 1; row++) for (int col = 0; col < N; col++)
            down[row * N + col] = Integer.signum(clues[row * N + col] - clues[(row + 1) * N + col]);
        for (int item : Generation.order(40, random)) {
            int[] array = item < 16 ? clues : item < 28 ? h : down;
            int index = item < 16 ? item : item < 28 ? item - 16 : item - 28;
            if (item >= 16 && Arrays.stream(h).filter(v -> v != 0).count()
                    + Arrays.stream(down).filter(v -> v != 0).count() <= 4) continue;
            int old = array[index]; array[index] = 0;
            if (count(clues, h, down, 2, budget) != 1) array[index] = old;
        }
        return new Futoshiki(clues, clues, h, down);
    }
    @Override public void write(DataOutputStream out) throws IOException {
        for (int[] a : new int[][]{clues, values, horizontal, vertical}) PuzzleStore.array(out, a);
    }
    static Futoshiki read(DataInputStream in) throws IOException {
        int[] clues = PuzzleStore.array(in, 16, 0, N), values = PuzzleStore.array(in, 16, 0, N);
        int[] h = PuzzleStore.array(in, 12, -1, 1), down = PuzzleStore.array(in, 12, -1, 1);
        if (!valid(clues, h, down)) throw new IOException("Invalid Futoshiki clues.");
        for (int i = 0; i < 16; i++) if (clues[i] != 0 && clues[i] != values[i]) throw new IOException("Changed clue.");
        return new Futoshiki(clues, values, h, down);
    }
}
