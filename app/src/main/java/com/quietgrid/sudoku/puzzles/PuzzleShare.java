package com.quietgrid.sudoku.puzzles;

import com.quietgrid.sudoku.LocalShareLink;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Locale;

/** Compact starting definitions only; imported definitions must prove unique within a finite budget. */
public final class PuzzleShare {
    private PuzzleShare() {}

    public static String kind(Mode mode) { return mode.name().toLowerCase(Locale.ROOT); }
    public static Mode mode(String link) {
        String kind = LocalShareLink.kind(link);
        for (Mode mode : Mode.values()) if (mode != Mode.MINESWEEPER && kind(mode).equals(kind)) return mode;
        throw new IllegalArgumentException("This puzzle does not support local sharing.");
    }

    public static String link(Puzzle puzzle) {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        data.write(puzzle.size());
        if (puzzle instanceof Takuzu p) pack(data, p.clues, 1);
        else if (puzzle instanceof Nonogram p) {
            for (int[][] axis : new int[][][]{p.rows, p.columns}) for (int[] line : axis) {
                data.write(line.length); pack(data, line, 0);
            }
        } else if (puzzle instanceof Futoshiki p) {
            pack(data, p.clues, 0); pack(data, p.horizontal, 1); pack(data, p.vertical, 1);
        } else if (puzzle instanceof Numberlink p) pack(data, p.endpoints, 0);
        else if (puzzle instanceof Slitherlink p) pack(data, p.clues, 1);
        else throw new IllegalArgumentException("This puzzle does not support local sharing.");
        return LocalShareLink.encode(kind(puzzle.mode()), data.toByteArray());
    }

    public static Puzzle open(String link) {
        Mode mode = mode(link);
        Cursor in = new Cursor(LocalShareLink.decode(link, kind(mode)));
        int n = in.next();
        Generation budget = new Generation(500_000, 3000);
        Puzzle puzzle;
        int count;
        switch (mode) {
            case TAKUZU: {
                if (n != 6 && n != 8) throw invalid();
                int[] clues = in.array(n * n, -1, 1, 1);
                puzzle = new Takuzu(clues, clues);
                count = Takuzu.count(clues, 2, budget); break;
            }
            case NONOGRAM: {
                if (n != 5 && n != 8 && n != 10) throw invalid();
                int[][][] axes = new int[2][n][];
                for (int a = 0; a < 2; a++) for (int i = 0; i < n; i++) {
                    int length = in.next();
                    if (length > (n + 1) / 2) throw invalid();
                    axes[a][i] = in.array(length, 1, n, 0);
                    if (Arrays.stream(axes[a][i]).sum() + length - 1 > n) throw invalid();
                }
                puzzle = new Nonogram(axes[0], axes[1], new int[n * n]);
                count = Nonogram.count(axes[0], axes[1], 2, budget); break;
            }
            case FUTOSHIKI: {
                if (n != 4) throw invalid();
                int[] clues = in.array(16, 0, 4, 0), h = in.array(12, -1, 1, 1), v = in.array(12, -1, 1, 1);
                puzzle = new Futoshiki(clues, clues, h, v);
                count = Futoshiki.count(clues, h, v, 2, budget); break;
            }
            case NUMBERLINK: {
                if (n != 4 && n != 6) throw invalid();
                int[] endpoints = in.array(n * n, 0, 5, 0);
                puzzle = new Numberlink(endpoints, new int[2 * n * (n - 1)]);
                count = Numberlink.count(endpoints, 2, budget); break;
            }
            case SLITHERLINK: {
                if (n != 4 && n != 6 && n != 8) throw invalid();
                int[] clues = in.array(n * n, -1, 4, 1);
                puzzle = Slitherlink.starting(n, clues);
                count = Slitherlink.count(clues, 2, budget); break;
            }
            default: throw invalid();
        }
        if (!link(puzzle).equals(link)) throw invalid();
        if (count != 1 || puzzle.solved()) throw new IllegalArgumentException("A shared puzzle must have exactly one solution and unfinished starting cells.");
        return puzzle;
    }

    private static void pack(ByteArrayOutputStream out, int[] values, int offset) {
        for (int i = 0; i < values.length; i += 2) {
            int a = values[i] + offset, b = i + 1 < values.length ? values[i + 1] + offset : 0;
            if (a < 0 || a > 15 || b < 0 || b > 15) throw invalid();
            out.write(a << 4 | b);
        }
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("The puzzle link has an invalid starting definition."); }
    private static final class Cursor {
        final byte[] data;
        int position;
        Cursor(byte[] data) { this.data = data; }
        int next() { if (position >= data.length) throw invalid(); return data[position++] & 255; }
        int[] array(int length, int min, int max, int offset) {
            int[] result = new int[length];
            for (int i = 0; i < length; i += 2) {
                int packed = next();
                result[i] = (packed >>> 4) - offset;
                if (i + 1 < length) result[i + 1] = (packed & 15) - offset;
                else if ((packed & 15) != 0) throw invalid();
            }
            for (int value : result) if (value < min || value > max) throw invalid();
            return result;
        }
    }
}
