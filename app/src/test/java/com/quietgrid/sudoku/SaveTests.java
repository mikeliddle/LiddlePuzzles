package com.quietgrid.sudoku;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentLinkedQueue;

final class SaveTests {
    private SaveTests() {}
    static void run() throws Exception {
        File directory = Files.createTempDirectory("puzzle-save-test").toFile();
        File file = new File(directory, "game.bin");
        byte[] old = {1, 2}, next = {3, 4};
        class Operations extends DurableFile.FileOperations {
            int calls, failAt = -1;
            void step() throws IOException { if (++calls == failAt) throw new IOException("Injected final commit failure"); }
            @Override public void write(File target, byte[] bytes) throws IOException { step(); super.write(target, bytes); }
            @Override public void copy(File source, File target) throws IOException { step(); super.copy(source, target); }
            @Override public void replace(File from, File to) throws IOException { step(); super.replace(from, to); }
            @Override public void delete(File target) throws IOException { step(); super.delete(target); }
            @Override public void syncDirectory(File target) throws IOException { step(); }
        }
        try {
            for (int stage = 1; stage <= 8; stage++) {
                Files.write(file.toPath(), old);
                Operations operations = new Operations(); operations.failAt = stage;
                DurableFile save = new DurableFile(file, operations);
                boolean failed = false;
                try { save.commit(next); } catch (IOException ex) { failed = true; }
                require(failed && Arrays.equals(old, save.read()), "Every staging/write/sync/replace/final-sync failure preserves last good save");
            }
            Files.write(new File(file + ".bak").toPath(), old);
            Files.write(file.toPath(), next);
            DurableFile save = new DurableFile(file, new Operations());
            require(Arrays.equals(old, save.read()), "Legacy interrupted AtomicFile backup recovers");
            save.commit(next);
            require(Arrays.equals(next, save.read()), "Successful commit persists exact next bytes");
            for (int stage = 1; stage <= 5; stage++) {
                Files.deleteIfExists(file.toPath());
                Operations operations = new Operations(); operations.failAt = stage;
                boolean failed = false;
                try { new DurableFile(file, operations).commit(next); } catch (IOException ex) { failed = true; }
                require(failed && !file.exists(), "Failed first save must not appear committed");
            }
            Files.write(file.toPath(), old);
            DurableFile persistent = new DurableFile(file, new Operations() {
                @Override public void replace(File from, File to) throws IOException {
                    if (to.equals(file)) throw new IOException("Persistent replacement failure");
                    super.replace(from, to);
                }
            });
            IOException failure = null;
            try { persistent.commit(next); } catch (IOException ex) { failure = ex; }
            require(failure != null && failure.getSuppressed().length == 1,
                    "Persistent rollback failure is explicitly attached to commit error");
            require(Arrays.equals(old, Files.readAllBytes(new File(file + ".bak").toPath())),
                    "Never truncate an existing good recovery file while rollback is failing");
            require(Arrays.equals(old, save.read()), "Recovery after persistent failure returns last good bytes");
            boolean[] retired = {false};
            DurableFile finalFailure = new DurableFile(file, new Operations() {
                @Override public void replace(File from, File to) throws IOException {
                    super.replace(from, to);
                    if (from.equals(new File(file + ".bak")) && to.equals(new File(file + ".new"))) retired[0] = true;
                }
                @Override public void write(File target, byte[] bytes) throws IOException {
                    if (retired[0]) throw new IOException("No space for rollback writes");
                    super.write(target, bytes);
                }
                @Override public void syncDirectory(File target) throws IOException {
                    if (retired[0]) throw new IOException("Persistent final directory-sync failure");
                }
            });
            failure = null;
            try { finalFailure.commit(next); } catch (IOException ex) { failure = ex; }
            require(failure != null && failure.getSuppressed().length == 1
                            && Arrays.equals(old, Files.readAllBytes(new File(file + ".bak").toPath())),
                    "Final sync plus persistent storage failure keeps synced old bytes without rewriting them");
            require(Arrays.equals(old, save.read()), "Recover final-sync failure after storage becomes available");
            DurableFile partial = new DurableFile(file, new Operations() {
                @Override public void copy(File source, File target) throws IOException {
                    Files.write(target.toPath(), new byte[]{0});
                    throw new IOException("Partial copy");
                }
                @Override public void write(File target, byte[] bytes) throws IOException {
                    Files.write(target.toPath(), new byte[]{0});
                    throw new IOException("Partial write");
                }
                @Override public void delete(File target) throws IOException { throw new IOException("Cleanup failed"); }
            });
            failure = null;
            try { partial.commit(next); } catch (IOException ex) { failure = ex; }
            require(failure != null && failure.getSuppressed().length == 1 && Arrays.equals(old, save.read()),
                    "Partial staging write and failed cleanup never replace good recovery bytes");
            Files.deleteIfExists(new File(file + ".new").toPath());

            SaveQueue queue = SaveQueue.forPath(file.getAbsolutePath());
            CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
            AtomicReference<IOException> error = new AtomicReference<>();
            queue.checkpoint(() -> {
                started.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Test timed out"); }
                catch (InterruptedException ex) { throw new IOException(ex); }
                save.commit(old);
            }, error::set);
            require(started.await(5, TimeUnit.SECONDS), "Timer encoding runs on worker");
            queue.checkpoint(() -> save.commit(new byte[]{9}), error::set);
            Thread unblock = new Thread(release::countDown); unblock.start();
            queue.writeBlocking(() -> save.commit(next));
            queue.flushBlocking();
            require(error.get() == null && Arrays.equals(next, save.read()), "In-flight/pending older timer never overwrites edit");
            queue.checkpoint(() -> { throw new IOException("Timer failed"); }, error::set);
            queue.flushBlocking();
            require(error.get() != null, "Asynchronous checkpoint failures are observable");
            for (String transition : new String[]{"Undo", "replacement", "terminal result", "daily rollover"}) {
                error.set(null);
                CountDownLatch busy = new CountDownLatch(1), proceed = new CountDownLatch(1);
                queue.checkpoint(() -> {
                    busy.countDown();
                    try { if (!proceed.await(5, TimeUnit.SECONDS)) throw new IOException("Test timed out"); }
                    catch (InterruptedException ex) { throw new IOException(ex); }
                }, error::set);
                require(busy.await(5, TimeUnit.SECONDS), "Block writer before " + transition);
                queue.checkpoint(() -> save.commit(old), error::set);
                Thread edit = new Thread(() -> {
                    try { queue.writeBlocking(() -> save.commit(next)); } catch (IOException ex) { error.set(ex); }
                });
                edit.start();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (edit.getState() != Thread.State.WAITING && System.nanoTime() < deadline) Thread.sleep(1);
                require(edit.getState() == Thread.State.WAITING, "Transition admitted before its newer timer");
                byte[] after = {7, 8};
                queue.checkpoint(() -> save.commit(after), error::set);
                proceed.countDown(); edit.join(5000); queue.flushBlocking();
                require(!edit.isAlive() && error.get() == null && Arrays.equals(after, save.read()),
                        "Canceled old batch cannot steal post-" + transition + " timer before the transition");
            }
            asyncSessions(queue, save);
            String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
            Process child = new ProcessBuilder(new File(new File(System.getProperty("java.home"), "bin"), executable).getPath(),
                    "-Xmx32m", "-cp", System.getProperty("java.class.path"), SaveTests.class.getName(), "oversized")
                    .inheritIO().start();
            require(child.waitFor() == 0, "Oversized replacement and restoration must fit a 32 MiB heap");
            System.out.println("PASS: save final-commit rollback, recovery and ordered checkpoint assertions");
        } finally {
            for (File child : directory.listFiles()) Files.delete(child.toPath());
            Files.delete(directory.toPath());
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    public static void main(String[] args) throws Exception {
        File directory = Files.createTempDirectory("oversized-save-test").toFile();
        File source = new File(directory, "oversized.bin"), target = new File(directory, "game.bin");
        class Operations extends DurableFile.FileOperations {
            int steps, failAt = -1;
            void step() throws IOException { if (++steps == failAt) throw new IOException("Oversized final commit failure"); }
            @Override public void copy(File from, File to) throws IOException { step(); super.copy(from, to); }
            @Override public void write(File file, byte[] bytes) throws IOException { step(); super.write(file, bytes); }
            @Override public void replace(File from, File to) throws IOException { step(); super.replace(from, to); }
            @Override public void delete(File file) throws IOException { step(); super.delete(file); }
            @Override public void syncDirectory(File file) throws IOException { step(); }
        }
        try {
            try (java.io.RandomAccessFile file = new java.io.RandomAccessFile(source, "rw")) {
                file.setLength(64L * 1024 * 1024);
                file.writeLong(123456789); file.seek(file.length() - 8); file.writeLong(987654321);
            }
            byte[] original = digest(source);
            for (int failure : new int[]{5, 8}) {
                new Operations().copy(source, target);
                Operations operations = new Operations(); operations.failAt = failure;
                DurableFile save = new DurableFile(target, operations);
                boolean rejected = false;
                try { save.read(262144); } catch (IOException ex) { rejected = true; }
                require(rejected, "Oversized active save is rejected before allocation");
                boolean failed = false;
                try { save.commit(new byte[]{1, 2, 3}); } catch (IOException ex) { failed = true; }
                require(failed && Arrays.equals(original, digest(target)), "Oversized prior bytes restored exactly");
            }
            DurableFile save = new DurableFile(target, new Operations());
            save.commit(new byte[]{1, 2, 3});
            require(Arrays.equals(new byte[]{1, 2, 3}, save.read(262144)), "Confirmed oversized save replacement succeeds");
            System.out.println("PASS: 64 MiB rejected-save replacement/rollback under 32 MiB heap");
        } finally {
            for (File child : directory.listFiles()) Files.delete(child.toPath());
            Files.delete(directory.toPath());
        }
    }
    private static byte[] digest(File file) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        try (java.io.InputStream in = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return digest.digest();
    }
    private static void asyncSessions(SaveQueue queue, DurableFile save) throws Exception {
        class Owner { int published, errors; }
        Owner old = new Owner(), replacement = new Owner();
        ConcurrentLinkedQueue<Runnable> callbacks = new ConcurrentLinkedQueue<>();
        AtomicReference<IOException> error = new AtomicReference<>();
        SaveSession<Owner> session = new SaveSession<>(queue, callbacks::add, error::set);
        session.attach(old); session.ready();
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        long begin = System.nanoTime();
        session.dispatch(owner -> session.commit(() -> {
            started.countDown();
            try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Test stalled"); }
            catch (InterruptedException ex) { throw new IOException(ex); }
            save.commit(new byte[]{1});
        }, ownerNow -> ownerNow.published = 1, (ownerNow, ex) -> ownerNow.errors++));
        session.dispatch(owner -> {
            int next = owner.published + 1;
            session.commit(() -> save.commit(new byte[]{(byte) next}),
                    ownerNow -> ownerNow.published = next, (ownerNow, ex) -> ownerNow.errors++);
        });
        session.dispatch(owner -> {
            int undo = owner.published - 1;
            session.commit(() -> save.commit(new byte[]{(byte) undo}),
                    ownerNow -> ownerNow.published = undo, (ownerNow, ex) -> ownerNow.errors++);
        });
        queue.flush(() -> {});
        require(System.nanoTime() - begin < TimeUnit.MILLISECONDS.toNanos(100),
                "Input, queued edits/Undo and lifecycle barrier return without waiting on stalled disk");
        require(started.await(5, TimeUnit.SECONDS) && old.published == 0, "No edit/win acknowledged before durable completion");
        session.detach(old, false); session.attach(replacement); session.ready();
        require(replacement.published == 0, "Recreation is responsive while storage remains stalled");
        release.countDown(); settle(session, callbacks);
        require(old.published == 0 && replacement.published == 1 && save.read()[0] == 1 && error.get() == null,
                "Retained ordered input/Undo publishes only to recreated owner after each successful write");
        session.dispatch(owner -> session.commit(() -> { throw new IOException("Stalled write failed"); },
                ownerNow -> ownerNow.published = 9, (ownerNow, ex) -> ownerNow.errors++));
        session.dispatch(owner -> { throw new AssertionError("Dependent input must not publish after failed save"); });
        settle(session, callbacks);
        require(error.get() != null && replacement.errors == 1 && replacement.published == 1,
                "Failed asynchronous save reports error, preserves published state and cancels dependent input");
        session.dispatch(owner -> session.commit(() -> save.commit(new byte[]{2}),
                ownerNow -> ownerNow.published = 2, (ownerNow, ex) -> ownerNow.errors++));
        settle(session, callbacks);
        require(replacement.published == 2 && save.read()[0] == 2, "Failed input remains retryable");
        System.out.println("PASS: nonblocking stalled/failing writer, retained quick input/Undo and delayed acknowledgement");
    }
    private static void settle(SaveSession<?> session, ConcurrentLinkedQueue<Runnable> callbacks) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!session.idle() && System.nanoTime() < deadline) {
            Runnable callback;
            while ((callback = callbacks.poll()) != null) callback.run();
            Thread.sleep(1);
        }
        require(session.idle(), "Asynchronous session did not settle");
    }
}
