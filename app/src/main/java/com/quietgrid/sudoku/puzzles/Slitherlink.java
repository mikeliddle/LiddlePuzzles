package com.quietgrid.sudoku.puzzles;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

public final class Slitherlink implements UndoablePuzzle {
    public static final int N = 4;
    private static final class Layout {
        final int n;
        final GridEdges grid;
        final int[][] sides;
        Layout(int n) {
            this.n = n; grid = new GridEdges(n + 1); sides = new int[n * n][4];
            for (int r = 0; r < n; r++) for (int c = 0; c < n; c++) {
                int v = r * (n + 1) + c;
                sides[r * n + c] = new int[]{grid.between(v, v + 1), grid.between(v, v + n + 1),
                        grid.between(v + 1, v + n + 2), grid.between(v + n + 1, v + n + 2)};
            }
        }
    }
    private static final Layout SMALL = new Layout(4), MEDIUM = new Layout(6), LARGE = new Layout(8);
    public static final GridEdges GRID = SMALL.grid;
    public static final int[][] SIDES = SMALL.sides;
    private final Layout layout;
    public final GridEdges grid;
    public final int[][] sides;
    public final int[] clues, edges;
    private Slitherlink(Layout layout, int[] clues, int[] edges) {
        this.layout = layout; grid = layout.grid; sides = layout.sides;
        this.clues = clues.clone(); this.edges = edges.clone();
    }
    private static Layout layout(int n) {
        switch (n) {
            case 4: return SMALL;
            case 6: return MEDIUM;
            case 8: return LARGE;
            default: throw new IllegalArgumentException("Choose a 4x4, 6x6 or 8x8 loop.");
        }
    }
    @Override public Mode mode() { return Mode.SLITHERLINK; }
    @Override public int size() { return layout.n; }
    @Override public int[] entries() { return edges; }
    @Override public Slitherlink copy() { return new Slitherlink(layout, clues, edges); }
    @Override public boolean solved() { return valid(layout, clues, edges); }
    public void cycle(int edge) { if (!solved()) edges[edge] = (edges[edge] + 1) % 3; }
    public static final class Errors {
        public final String[] edges, cells;
        private Errors(int edgeCount, int cellCount) { edges = new String[edgeCount]; cells = new String[cellCount]; }
        public boolean any() {
            for (String error : edges) if (error != null) return true;
            for (String error : cells) if (error != null) return true;
            return false;
        }
    }
    /** Only forced contradictions in the current marks, never a hidden-loop comparison. */
    public Errors errors() {
        int n = size();
        Errors errors = new Errors(edges.length, clues.length);
        for (int cell = 0; cell < clues.length; cell++) if (clues[cell] >= 0) {
            int drawn = 0;
            for (int edge : sides[cell]) if (edges[edge] == 1) drawn++;
            if (drawn <= clues[cell]) continue;
            String reason = "Row " + (cell / n + 1) + " column " + (cell % n + 1)
                    + " has " + drawn + " drawn sides, more than clue " + clues[cell] + ".";
            errors.cells[cell] = reason;
            for (int edge : sides[cell]) if (edges[edge] == 1) mark(errors.edges, edge, reason);
        }
        boolean[] visited = new boolean[grid.at.length];
        for (int vertex = 0; vertex < grid.at.length; vertex++) {
            int degree = grid.degree(edges, vertex);
            if (degree > 2) {
                String reason = "Branch at dot row " + (vertex / (n + 1) + 1)
                        + " column " + (vertex % (n + 1) + 1) + ": more than two lines meet.";
                for (int edge : grid.at[vertex]) if (edges[edge] == 1) mark(errors.edges, edge, reason);
            }
            if (degree == 0 || visited[vertex]) continue;
            boolean[] component = grid.component(edges, vertex);
            boolean closed = true, outside = false;
            for (int v = 0; v < component.length; v++) if (component[v]) {
                visited[v] = true;
                if (grid.degree(edges, v) != 2) closed = false;
            }
            if (!closed) continue;
            for (int edge = 0; edge < edges.length; edge++)
                if (edges[edge] == 1 && !component[grid.a[edge]]) outside = true;
            boolean matches = true;
            for (int cell = 0; cell < clues.length; cell++) if (clues[cell] >= 0) {
                int count = 0;
                for (int edge : sides[cell]) if (edges[edge] == 1 && component[grid.a[edge]]) count++;
                if (count != clues[cell]) matches = false;
            }
            if (outside || !matches) {
                String reason = "Separate closed loop: it cannot include "
                        + (outside ? "the other drawn lines" : "the remaining required clue sides") + ".";
                for (int edge = 0; edge < edges.length; edge++)
                    if (edges[edge] == 1 && component[grid.a[edge]]) mark(errors.edges, edge, reason);
            }
        }
        return errors;
    }
    private static void mark(String[] errors, int index, String reason) {
        errors[index] = errors[index] == null ? reason : errors[index] + " " + reason;
    }
    public static boolean valid(int[] clues, int[] edges) {
        int n = (int) Math.sqrt(clues.length);
        if (n != 4 && n != 6 && n != 8 || n * n != clues.length) return false;
        return valid(layout(n), clues, edges);
    }
    private static boolean valid(Layout layout, int[] clues, int[] edges) {
        GridEdges grid = layout.grid;
        if (clues.length != layout.n * layout.n || edges.length != grid.a.length) return false;
        int first = -1;
        for (int cell = 0; cell < clues.length; cell++) {
            if (clues[cell] < -1 || clues[cell] > 4) return false;
            int count = 0; for (int edge : layout.sides[cell]) if (edges[edge] == 1) count++;
            if (clues[cell] >= 0 && count != clues[cell]) return false;
        }
        for (int value : edges) if (value < 0 || value > 2) return false;
        for (int vertex = 0; vertex < grid.at.length; vertex++) {
            int degree = grid.degree(edges, vertex);
            if (degree != 0 && degree != 2) return false;
            if (degree == 2) first = vertex;
        }
        if (first < 0) return false;
        boolean[] seen = grid.component(edges, first);
        for (int edge = 0; edge < edges.length; edge++) if (edges[edge] == 1 && !seen[grid.a[edge]]) return false;
        return true;
    }
    public static int count(int[] clues, int limit, Generation budget) {
        Layout layout = layout((int) Math.sqrt(clues.length));
        if (clues.length != layout.n * layout.n) throw new IllegalArgumentException("Invalid loop dimensions.");
        int[] edges = new int[layout.grid.a.length]; Arrays.fill(edges, -1);
        return search(layout, clues, edges, limit, budget);
    }
    private static boolean constrain(int[] edges, int[] group, int target) {
        int selected = 0, unknown = 0;
        for (int edge : group) { if (edges[edge] == 1) selected++; else if (edges[edge] < 0) unknown++; }
        if (selected > target || selected + unknown < target) return false;
        if (selected == target || selected + unknown == target) {
            for (int edge : group) if (edges[edge] < 0) edges[edge] = selected == target ? 0 : 1;
        }
        return true;
    }
    private static int search(Layout layout, int[] clues, int[] edges, int limit, Generation budget) {
        budget.tick();
        GridEdges grid = layout.grid;
        int unknown;
        do {
            unknown = (int) Arrays.stream(edges).filter(v -> v < 0).count();
            for (int cell = 0; cell < clues.length; cell++) if (clues[cell] >= 0 && !constrain(edges, layout.sides[cell], clues[cell])) return 0;
            for (int[] vertex : grid.at) {
                int selected = 0, open = 0;
                for (int e : vertex) { if (edges[e] == 1) selected++; else if (edges[e] < 0) open++; }
                if (selected > 2 || (selected == 1 && open == 0)) return 0;
                if (selected > 0 && !constrain(edges, vertex, 2)) return 0;
                if (selected == 0 && open == 1 && !constrain(edges, vertex, 0)) return 0;
            }
        } while (unknown != Arrays.stream(edges).filter(v -> v < 0).count());
        int best = -1, score = -1;
        for (int edge = 0; edge < edges.length; edge++) if (edges[edge] < 0) {
            int weight = 0;
            for (int cell = 0; cell < clues.length; cell++) if (clues[cell] >= 0) {
                for (int side : layout.sides[cell]) if (side == edge) weight += 8;
            }
            weight += grid.degree(edges, grid.a[edge]) + grid.degree(edges, grid.b[edge]);
            if (weight > score) { score = weight; best = edge; }
        }
        if (best < 0) return valid(layout, clues, edges) ? 1 : 0;
        int[] without = edges.clone(); without[best] = 0;
        int found = search(layout, clues, without, limit, budget);
        if (found >= limit) return found;
        int[] with = edges.clone(); with[best] = 1;
        return found + search(layout, clues, with, limit - found, budget);
    }
    public static Slitherlink generate(Random random, Generation budget) {
        return generate(N, random, budget);
    }
    public static Slitherlink generate(int n, Random random, Generation budget) {
        Layout layout = layout(n);
        for (int attempt = 0; attempt < 100; attempt++) {
            budget.tick();
            boolean[] inside = new boolean[n * n]; inside[random.nextInt(inside.length)] = true;
            int target = n == 4 ? 3 + random.nextInt(10) : n * n / 3 + random.nextInt(n * n / 3), filled = 1;
            for (int tries = 0; filled < target && tries < (n == 4 ? 200 : n * n * 20); tries++) {
                budget.tick();
                int cell = random.nextInt(inside.length);
                if (inside[cell]) continue;
                for (int next : Generation.neighbors(cell, n)) if (next >= 0 && inside[next]) {
                    inside[cell] = true; filled++; break;
                }
            }
            int[] edges = new int[layout.grid.a.length], clues = new int[n * n];
            Arrays.fill(clues, -1);
            for (int cell = 0; cell < inside.length; cell++) if (inside[cell])
                for (int edge : layout.sides[cell]) edges[edge] ^= 1;
            if (!valid(clues, edges)) continue;
            for (int cell = 0; cell < clues.length; cell++) {
                clues[cell] = 0; for (int edge : layout.sides[cell]) clues[cell] += edges[edge];
            }
            if (count(clues, 2, budget) != 1) continue;
            int retained = clues.length;
            for (int cell : Generation.order(clues.length, random)) {
                if (retained <= clues.length / 2) break;
                int old = clues[cell]; clues[cell] = -1;
                if (count(clues, 2, budget) == 1) retained--; else clues[cell] = old;
            }
            return new Slitherlink(layout, clues, new int[edges.length]);
        }
        throw new IllegalStateException("Could not certify a unique loop. Please retry.");
    }
    @Override public void write(DataOutputStream out) throws IOException {
        if (size() != N) { out.writeInt(-2); out.writeInt(size()); }
        PuzzleStore.array(out, clues); PuzzleStore.array(out, edges);
    }
    static Slitherlink read(DataInputStream in) throws IOException {
        in.mark(8);
        int first = in.readInt();
        Layout layout;
        if (first == -2) {
            int n = in.readInt();
            if (n != 6 && n != 8) throw new IOException("Invalid loop dimensions.");
            layout = layout(n);
        } else { in.reset(); layout = SMALL; }
        return new Slitherlink(layout, PuzzleStore.array(in, layout.n * layout.n, -1, 4),
                PuzzleStore.array(in, layout.grid.a.length, 0, 2));
    }
}
