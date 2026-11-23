package com.quietgrid.sudoku;

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

    private PuzzleSave(File file) { this.file = file; }

    boolean limited(boolean enabled, LocalDate today) {
        return enabled && finishedToday(today) >= LIMIT;
    }

    int finishedToday(LocalDate today) { return tally.count(today.toEpochDay()); }

    byte[] read(DurableFile storage, int maxBytes) throws IOException {
        byte[][] payload = {null};
        SaveQueue.forPath(file.getAbsolutePath()).readBlocking(() -> {
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
            tally = restored;
            payload[0] = bytes;
        });
        return payload[0];
    }

    void commit(DurableFile storage, byte[] payload, Edit edit, boolean enabled, LocalDate today) throws IOException {
        Tally previous = tally;
        long day = today.toEpochDay();
        int count = previous.count(day);
        if (edit != Edit.CHECKPOINT && enabled && count >= LIMIT) throw new LimitReached();
        Tally next = switch (edit) {
            case FINISH -> previous.finished ? previous : new Tally(day, count + 1, true);
            case DAILY_FINISH -> count > 0 ? previous : new Tally(day, 1, false);
            case UNDO_FINISH -> new Tally(previous.day, previous.count, true);
            case REPLACE -> new Tally(previous.day, previous.count, false);
            default -> previous;
        };
        // Old saves without metadata need no conversion until there's something new to record.
        storage.commit(next.equals(EMPTY) ? payload : ByteBuffer.allocate(HEADER + payload.length)
                .putLong(MAGIC).putLong(next.day).putInt(next.count).put((byte) (next.finished ? 1 : 0))
                .put(payload).array());
        tally = next;
    }
}
