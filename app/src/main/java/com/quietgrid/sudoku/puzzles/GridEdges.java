package com.quietgrid.sudoku.puzzles;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Orthogonal grid topology shared by path and loop puzzles. Horizontal edges precede vertical ones. */
public final class GridEdges {
    public final int width;
    public final int[] a, b;
    public final int[][] at;
    public GridEdges(int width) {
        this.width = width;
        int half = width * (width - 1);
        a = new int[half * 2]; b = new int[a.length]; at = new int[width * width][];
        for (int row = 0; row < width; row++) for (int col = 0; col < width - 1; col++) {
            int edge = row * (width - 1) + col;
            a[edge] = row * width + col; b[edge] = a[edge] + 1;
        }
        for (int row = 0; row < width - 1; row++) for (int col = 0; col < width; col++) {
            int edge = half + row * width + col;
            a[edge] = row * width + col; b[edge] = a[edge] + width;
        }
        for (int vertex = 0; vertex < at.length; vertex++) {
            List<Integer> edges = new ArrayList<>();
            for (int edge = 0; edge < a.length; edge++) if (a[edge] == vertex || b[edge] == vertex) edges.add(edge);
            at[vertex] = edges.stream().mapToInt(Integer::intValue).toArray();
        }
    }
    public int between(int x, int y) {
        for (int edge : at[x]) if (a[edge] == y || b[edge] == y) return edge;
        return -1;
    }
    public int other(int edge, int vertex) { return a[edge] == vertex ? b[edge] : a[edge]; }
    public int degree(int[] selected, int vertex) {
        int count = 0; for (int edge : at[vertex]) if (selected[edge] == 1) count++; return count;
    }
    public boolean[] component(int[] edges, int start) {
        boolean[] seen = new boolean[at.length]; seen[start] = true;
        ArrayDeque<Integer> queue = new ArrayDeque<>(); queue.add(start);
        while (!queue.isEmpty()) {
            int v = queue.remove();
            for (int edge : at[v]) if (edges[edge] == 1) {
                int next = other(edge, v);
                if (!seen[next]) { seen[next] = true; queue.add(next); }
            }
        }
        return seen;
    }
}
