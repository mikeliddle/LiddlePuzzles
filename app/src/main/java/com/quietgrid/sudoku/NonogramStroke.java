package com.quietgrid.sudoku;

import java.util.BitSet;

/** One pointer in grid units; clues and space outside the grid are not cells. */
final class NonogramStroke {
    final BitSet crossed = new BitSet();
    private final int size, start;
    private final double downX, downY, slop;
    private final long holdAt;
    private double x, y;
    private boolean tap = true, holdEligible = true, held;
    private int paint;

    NonogramStroke(int size, double x, double y, double slop, long holdAt) {
        this.size = size;
        this.x = downX = x;
        this.y = downY = y;
        this.slop = slop;
        this.holdAt = holdAt;
        start = cell(x, y);
        crossed.set(start);
    }

    void sample(double nextX, double nextY, long time) {
        if (holdEligible && time >= holdAt) held = true;
        if (cell(nextX, nextY) != start) {
            tap = false;
            holdEligible = false;
        } else if (Math.hypot(nextX - downX, nextY - downY) > slop) {
            holdEligible = false;
        }
        if (x != nextX || y != nextY) {
            for (int row = 0; row < size; row++) for (int col = 0; col < size; col++) {
                if (crosses(x, y, nextX, nextY, col, row)) crossed.set(row * size + col);
            }
            if (paint == 0 && crossed.cardinality() > 1) paint = held ? 2 : 1;
        }
        x = nextX;
        y = nextY;
    }

    int paint() { return paint; }
    boolean tap() { return tap && !held; }
    boolean hold() { return tap && held; }

    private int cell(double x, double y) {
        return x >= 0 && x < size && y >= 0 && y < size ? (int) y * size + (int) x : -1;
    }

    private static boolean crosses(double x, double y, double nextX, double nextY, int col, int row) {
        double dx = nextX - x, dy = nextY - y;
        if (dx == 0 && (x < col || x >= col + 1) || dy == 0 && (y < row || y >= row + 1)) return false;
        double inX = dx == 0 ? 0 : (col + (dx < 0 ? 1 : 0) - x) / dx;
        double outX = dx == 0 ? 1 : (col + (dx > 0 ? 1 : 0) - x) / dx;
        double inY = dy == 0 ? 0 : (row + (dy < 0 ? 1 : 0) - y) / dy;
        double outY = dy == 0 ? 1 : (row + (dy > 0 ? 1 : 0) - y) / dy;
        // A corner touch alone has no length inside a cell.
        return Math.max(0, Math.max(inX, inY)) < Math.min(1, Math.min(outX, outY));
    }
}
