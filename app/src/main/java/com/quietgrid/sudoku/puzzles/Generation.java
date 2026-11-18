package com.quietgrid.sudoku.puzzles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CancellationException;

/** One cancellable budget spans candidate creation and all uniqueness proofs. */
public final class Generation {
    private int remaining;
    private final long deadline;

    public Generation() { this(3_000_000, 10_000); }
    public Generation(int nodes, long millis) {
        remaining = nodes;
        deadline = System.nanoTime() + millis * 1_000_000;
    }

    public void tick() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException();
        if (--remaining < 0 || System.nanoTime() > deadline) {
            throw new IllegalStateException("Generation reached its safety limit. Please retry.");
        }
    }

    static List<Integer> order(int count, Random random) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < count; i++) order.add(i);
        Collections.shuffle(order, random);
        return order;
    }

    static int[] neighbors(int cell, int n) {
        return new int[]{cell / n > 0 ? cell - n : -1, cell % n + 1 < n ? cell + 1 : -1,
                cell / n + 1 < n ? cell + n : -1, cell % n > 0 ? cell - 1 : -1};
    }
}
