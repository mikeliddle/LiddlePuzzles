package com.quietgrid.sudoku;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/** One process-wide ordered writer per save; pending timer snapshots are coalesced. */
final class SaveQueue {
    interface Write { void run() throws IOException; }
    private static final Map<String, SaveQueue> queues = new HashMap<>();
    static synchronized SaveQueue forPath(String path) {
        return queues.computeIfAbsent(path, ignored -> new SaveQueue());
    }
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "puzzle-save"); thread.setDaemon(true); return thread;
    });
    private static final class Pending {
        Write write;
        Consumer<IOException> error;
    }
    private Pending pending;

    synchronized void checkpoint(Write write, Consumer<IOException> error) {
        if (pending == null) {
            Pending batch = new Pending();
            batch.write = write; batch.error = error; pending = batch;
            worker.execute(() -> drain(batch));
        } else { pending.write = write; pending.error = error; }
    }
    private void drain(Pending batch) {
        Write write; Consumer<IOException> error;
        synchronized (this) {
            write = batch.write; error = batch.error;
            if (pending == batch) pending = null;
        }
        if (write != null) {
            try { write.run(); }
            catch (IOException ex) { error.accept(ex); }
        }
    }
    void commit(Write write, Runnable success, Consumer<IOException> error) {
        synchronized (this) {
            if (pending != null) { pending.write = null; pending = null; }
            worker.execute(() -> {
                try { write.run(); success.run(); }
                catch (IOException ex) { error.accept(ex); }
            });
        }
    }
    synchronized void flush(Runnable complete) { worker.execute(complete); }
    void writeBlocking(Write write) throws IOException {
        Future<?> result;
        synchronized (this) {
            if (pending != null) { pending.write = null; pending = null; }
            result = worker.submit(() -> { write.run(); return null; });
        }
        await(result);
    }
    void flushBlocking() throws IOException {
        Future<?> result;
        synchronized (this) { result = worker.submit(() -> null); }
        await(result);
    }
    void readBlocking(Write read) throws IOException {
        Future<?> result;
        synchronized (this) { result = worker.submit(() -> { read.run(); return null; }); }
        await(result);
    }
    private static void await(Future<?> result) throws IOException {
        boolean interrupted = false;
        try {
            for (;;) {
                try { result.get(); return; }
                catch (InterruptedException ex) { interrupted = true; }
                catch (ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    if (cause instanceof IOException) throw (IOException) cause;
                    if (cause instanceof RuntimeException) throw (RuntimeException) cause;
                    if (cause instanceof Error) throw (Error) cause;
                    throw new IOException("Save worker failed", cause);
                }
            }
        } finally { if (interrupted) Thread.currentThread().interrupt(); }
    }
}
