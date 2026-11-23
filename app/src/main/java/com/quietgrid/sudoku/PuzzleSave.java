package com.quietgrid.sudoku;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** The board and its quota metadata commit together on the existing per-file save queue. */
final class PuzzleSave {
    static final int LIMIT = 10;
    private static final long MAGIC = 0x4c5051554f544131L; // LPQUOTA1
    private static final int HEADER = Long.BYTES * 2 + Integer.BYTES + 1;
    private static final Map<File, PuzzleSave> stores = new HashMap<>();
    private static final Tally EMPTY = new Tally(0, 0, false);

    enum Edit { CHECKPOINT, PLAY, REPLACE, FINISH, DAILY_FINISH, UNDO_FINISH }

    static final class LimitReached extends IOException {}

    private record Tally(long day, int count, boolean finished) {
        int count(long today) { return day == today ? count : 0; }
    }

    static synchronized PuzzleSave forFile(File file) {
        return stores.computeIfAbsent(file, PuzzleSave::new);
    }

    private final File file;
    private volatile Tally tally = EMPTY;
    private volatile Map<File, Long> sharedFinishes = Map.of();

    private PuzzleSave(File file) { this.file = file; }

    boolean limited(boolean enabled, LocalDate today) {
        return enabled && finishedToday(today) >= LIMIT;
    }

    int finishedToday(LocalDate today) {
        long day = today.toEpochDay();
        int count = tally.count(day);
        for (long finish : sharedFinishes.values()) if (finish == day) count++;
        return count;
    }

    SaveQueue queue() { return SaveQueue.forPath(file.getAbsolutePath()); }

    byte[] read(DurableFile storage, int maxBytes) throws IOException {
        return read(storage, maxBytes, null, null, null);
    }

    byte[] read(DurableFile storage, int maxBytes, File sharedRoot, String prefix, File sharedFile) throws IOException {
        byte[][] payload = {null};
        queue().readBlocking(() -> {
            if (prefix != null) loadSharedFinishes(sharedRoot, prefix);
            if (sharedFile != null) tally = readHeader(file);
            int limit = maxBytes <= Integer.MAX_VALUE - HEADER ? maxBytes + HEADER : Integer.MAX_VALUE;
            byte[] bytes = storage.read(limit);
            Tally restored = EMPTY;
            if (bytes != null && bytes.length >= Long.BYTES && ByteBuffer.wrap(bytes).getLong() == MAGIC) {
                if (bytes.length <= HEADER) throw new IOException("Incomplete puzzle save header.");
                ByteBuffer in = ByteBuffer.wrap(bytes);
                in.getLong();
                long day = in.getLong();
                int count = in.getInt(), finished = in.get();
                if (count < 0 || (finished != 0 && finished != 1))
                    throw new IOException("Invalid puzzle save header.");
                restored = new Tally(day, count, finished == 1);
                bytes = Arrays.copyOfRange(bytes, HEADER, bytes.length);
            }
            if (bytes != null && bytes.length > maxBytes) throw new IOException("Puzzle save is too large.");
            if (sharedFile == null) tally = restored;
            payload[0] = bytes;
        });
        return payload[0];
    }

    private void loadSharedFinishes(File root, String prefix) throws IOException {
        // A completion lives in the same atomic commit as the recipient's board, not in the normal save.
        File[] files = root.listFiles((dir, name) -> name.startsWith(prefix)
                && (name.endsWith(".bin") || name.endsWith(".bin.bak")));
        if (files == null) throw new IOException("Could not read shared puzzle saves.");
        Map<File, Long> finishes = new HashMap<>();
        for (File entry : files) {
            String name = entry.getName();
            File base = name.endsWith(".bak") ? new File(root, name.substring(0, name.length() - 4)) : entry;
            Tally record = readHeader(base);
            if (record.finished) finishes.put(base, record.day);
        }
        sharedFinishes = Map.copyOf(finishes);
    }

    private static Tally readHeader(File base) throws IOException {
        File backup = new File(base + ".bak");
        File source = backup.isFile() ? backup : base;
        if (source.length() <= HEADER) return EMPTY;
        try (DataInputStream in = new DataInputStream(java.nio.file.Files.newInputStream(source.toPath()))) {
            if (in.readLong() != MAGIC) return EMPTY;
            long day = in.readLong();
            int count = in.readInt(), finished = in.readUnsignedByte();
            if (count < 0 || finished > 1) return EMPTY;
            return new Tally(day, count, finished == 1);
        }
    }

    void commit(DurableFile storage, byte[] payload, Edit edit, boolean enabled, LocalDate today) throws IOException {
        Tally previous = tally;
        long day = today.toEpochDay();
        int count = previous.count(day);
        checkLimit(edit, enabled, today);
        Tally next = switch (edit) {
            case FINISH -> previous.finished ? previous : new Tally(day, count + 1, true);
            case DAILY_FINISH -> count > 0 ? previous : new Tally(day, 1, false);
            case UNDO_FINISH -> new Tally(previous.day, previous.count, true);
            case REPLACE -> new Tally(previous.day, previous.count, false);
            default -> previous;
        };
        write(storage, payload, next);
        tally = next;
    }

    void commitShared(File linkFile, DurableFile storage, byte[] payload, Edit edit, boolean enabled, LocalDate today) throws IOException {
        checkLimit(edit, enabled, today);
        boolean exists = linkFile.isFile() || new File(linkFile + ".bak").isFile();
        if ((edit == Edit.CHECKPOINT && (!exists || limited(enabled, today))) || (edit == Edit.REPLACE && exists)) return;
        Long finishedOn = sharedFinishes.get(linkFile);
        boolean firstFinish = edit == Edit.FINISH && finishedOn == null;
        if (firstFinish) finishedOn = today.toEpochDay();
        write(storage, payload, finishedOn == null ? EMPTY : new Tally(finishedOn, 1, true));
        if (firstFinish) {
            Map<File, Long> next = new HashMap<>(sharedFinishes);
            next.put(linkFile, finishedOn);
            sharedFinishes = Map.copyOf(next);
        }
    }

    private void checkLimit(Edit edit, boolean enabled, LocalDate today) throws LimitReached {
        if (edit != Edit.CHECKPOINT && limited(enabled, today)) throw new LimitReached();
    }

    private static void write(DurableFile storage, byte[] payload, Tally next) throws IOException {
        // Old saves without metadata need no conversion until there's something new to record.
        storage.commit(next.equals(EMPTY) ? payload : ByteBuffer.allocate(HEADER + payload.length)
                .putLong(MAGIC).putLong(next.day).putInt(next.count).put((byte) (next.finished ? 1 : 0))
                .put(payload).array());
    }
}
