package com.quietgrid.sudoku;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Retained input intents wait for durable acknowledgement without blocking the UI thread. */
final class SaveSession<O> {
    private final SaveQueue queue;
    private final Consumer<Runnable> post;
    private final Consumer<IOException> report;
    private final ArrayDeque<Consumer<O>> actions = new ArrayDeque<>();
    private O owner;
    private boolean ready, writing;
    private Consumer<O> completion;

    SaveSession(SaveQueue queue, Consumer<Runnable> post, Consumer<IOException> report) {
        this.queue = queue; this.post = post; this.report = report;
    }
    void attach(O next) { owner = next; ready = false; }
    void ready() {
        ready = true;
        if (completion != null) {
            Consumer<O> result = completion; completion = null; result.accept(owner);
        }
        drain();
    }
    boolean idle() { return ready && !writing && actions.isEmpty(); }
    boolean defer(Consumer<O> action) {
        if (writing || !ready || owner == null) { dispatch(action); return true; }
        return false;
    }
    void dispatch(Consumer<O> action) {
        actions.add(action); drain();
    }
    private void drain() {
        while (owner != null && ready && !writing && !actions.isEmpty()) actions.remove().accept(owner);
    }
    void commit(SaveQueue.Write write, Consumer<O> success, BiConsumer<O, IOException> error) {
        if (writing) throw new IllegalStateException("Submit game edits through the ordered session");
        writing = true;
        queue.commit(write, () -> post.accept(() -> finish(success, false)),
                ex -> { report.accept(ex); post.accept(() -> finish(value -> error.accept(value, ex), true)); });
    }
    private void finish(Consumer<O> result, boolean failed) {
        writing = false;
        if (failed) actions.clear();
        if (owner == null || !ready) completion = result;
        else { result.accept(owner); drain(); }
    }
    void detach(O previous, boolean permanent) {
        if (owner != previous) return;
        owner = null; ready = false;
        if (permanent) { actions.clear(); completion = null; }
    }
}
