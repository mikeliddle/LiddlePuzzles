package com.quietgrid.sudoku;

import java.util.Locale;
import java.util.function.LongSupplier;

/** Monotonic active time; only accumulated time, never a running anchor, is saved. */
final class ActivePlayTimer {
    private final LongSupplier now;
    private long elapsed, started;
    private boolean running;

    ActivePlayTimer(LongSupplier now) { this.now = now; }

    void restore(long millis) {
        if (millis < 0) throw new IllegalArgumentException("Negative play time.");
        elapsed = millis; running = false;
    }

    void setRunning(boolean active) {
        long current = now.getAsLong();
        if (running) elapsed = total(current);
        started = current; running = active;
    }

    long elapsedMillis() { return running ? total(now.getAsLong()) : elapsed; }

    private long total(long current) {
        long delta = Math.max(0, current - started);
        return elapsed > Long.MAX_VALUE - delta ? Long.MAX_VALUE : elapsed + delta;
    }

    static String label(long millis) {
        long seconds = millis / 1000;
        return String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60);
    }

    static String description(long millis) {
        return "Time played: " + millis / 60000 + " minutes " + millis / 1000 % 60 + " seconds";
    }
}
