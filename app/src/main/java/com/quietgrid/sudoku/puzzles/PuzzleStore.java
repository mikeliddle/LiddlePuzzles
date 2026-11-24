package com.quietgrid.sudoku.puzzles;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/** Versioned per-mode envelopes detect truncation, corruption and accidental cross-mode reads. */
public final class PuzzleStore {
    public static final int MAX_SAVE_BYTES = 262144;
    private PuzzleStore() {}

    public static byte[] encode(Puzzle puzzle) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(body)) {
            out.writeUTF(puzzle.mode().name());
            puzzle.write(out);
        }
        byte[] data = body.toByteArray();
        CRC32 crc = new CRC32(); crc.update(data);
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(file)) {
            out.writeInt(0x4C505A31); out.writeInt(data.length); out.write(data); out.writeLong(crc.getValue());
        }
        return file.toByteArray();
    }

    public static byte[] encode(PuzzleProgress progress) throws IOException {
        if (progress.elapsedMillis != 0) {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(body)) {
                out.writeLong(progress.elapsedMillis);
                out.write(encode(progress.withElapsed(0)));
            }
            return envelope(0x4C505A33, body.toByteArray());
        }
        if (!progress.canUndo() && !progress.canRedo()) return encode(progress.game);
        boolean compact = progress.game instanceof Slitherlink && progress.game.size() > 4;
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(body)) {
            byte[] current = encode(progress.game);
            out.writeInt(current.length); out.write(current);
            writeHistory(out, progress.history(), compact);
            if (progress.canRedo()) writeHistory(out, progress.redoHistory(), compact);
        }
        return envelope(progress.canRedo() ? 0x4C505A35 : compact ? 0x4C505A34 : 0x4C505A32, body.toByteArray());
    }

    private static void writeHistory(DataOutputStream out, List<int[]> stack, boolean compact) throws IOException {
        out.writeInt(stack.size());
        for (int[] entries : stack) {
            if (compact) for (int entry : entries) {
                if (entry < 0 || entry > 2) throw new IOException("Invalid compact loop mark.");
                out.writeByte(entry);
            }
            else array(out, entries);
        }
    }

    private static byte[] envelope(int version, byte[] data) throws IOException {
        CRC32 crc = new CRC32(); crc.update(data);
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(file)) {
            out.writeInt(version); out.writeInt(data.length); out.write(data); out.writeLong(crc.getValue());
        }
        if (file.size() > MAX_SAVE_BYTES) throw new IOException("Puzzle history is too large.");
        return file.toByteArray();
    }

    public static Puzzle decode(Mode expected, byte[] bytes) throws IOException {
        return decodeProgress(expected, bytes).game;
    }

    public static PuzzleProgress decodeProgress(Mode expected, byte[] bytes) throws IOException {
        return decodeProgress(expected, bytes, true);
    }

    private static PuzzleProgress decodeProgress(Mode expected, byte[] bytes, boolean timed) throws IOException {
        if (bytes.length > MAX_SAVE_BYTES) throw new IOException("Puzzle history is too large.");
        try (DataInputStream file = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int version = file.readInt();
            if (version == 0x4C505A31) return new PuzzleProgress(decodeLegacy(expected, bytes));
            boolean hasRedo = version == 0x4C505A35;
            if (!hasRedo && version != 0x4C505A32 && version != 0x4C505A34 && !(timed && version == 0x4C505A33))
                throw new IOException("Unknown puzzle save version.");
            int length = file.readInt();
            if (length < 1 || length > MAX_SAVE_BYTES - 16) throw new IOException("Invalid history save length.");
            byte[] data = new byte[length]; file.readFully(data);
            CRC32 crc = new CRC32(); crc.update(data);
            if (file.readLong() != crc.getValue() || file.read() != -1) throw new IOException("Puzzle history is damaged.");
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
                if (version == 0x4C505A33) {
                    long elapsed = in.readLong();
                    if (elapsed < 0) throw new IOException("Invalid puzzle play time.");
                    byte[] previous = new byte[in.available()]; in.readFully(previous);
                    return decodeProgress(expected, previous, false).withElapsed(elapsed);
                }
                int currentLength = in.readInt();
                if (currentLength < 1 || currentLength > 16384) throw new IOException("Invalid current puzzle length.");
                byte[] current = new byte[currentLength]; in.readFully(current);
                Puzzle game = decodeLegacy(expected, current);
                if (!(game instanceof UndoablePuzzle)) throw new IOException("This game does not permit Undo.");
                boolean largeLoop = game instanceof Slitherlink && game.size() > 4;
                if (version == 0x4C505A34 && !largeLoop)
                    throw new IOException("Compact history belongs to larger loops.");
                boolean compact = version == 0x4C505A34 || (hasRedo && largeLoop);
                List<int[]> history = readHistory(in, game, compact, hasRedo ? 0 : 1, PuzzleProgress.MAX_UNDO);
                List<int[]> redo = hasRedo
                        ? readHistory(in, game, compact, 1, PuzzleProgress.MAX_UNDO - history.size())
                        : List.of();
                if (in.read() != -1) throw new IOException("Unexpected history data.");
                return new PuzzleProgress(game, history, redo, 0);
            }
        }
    }

    private static List<int[]> readHistory(DataInputStream in, Puzzle game, boolean compact, int min, int max)
            throws IOException {
        int count = in.readInt();
        if (count < min || count > max) throw new IOException("Invalid move history count.");
        int entryCount = ((UndoablePuzzle) game).entries().length;
        List<int[]> history = new ArrayList<>(count);
        for (int step = 0; step < count; step++) {
            int[] entries;
            if (compact) {
                entries = new int[entryCount];
                for (int i = 0; i < entryCount; i++) {
                    entries[i] = in.readUnsignedByte();
                    if (entries[i] > 2) throw new IOException("Invalid compact loop mark.");
                }
            } else entries = array(in, entryCount, -1, 4);
            Puzzle previous = game.copy();
            System.arraycopy(entries, 0, ((UndoablePuzzle) previous).entries(), 0, entryCount);
            decodeLegacy(game.mode(), encode(previous));
            history.add(entries);
        }
        return history;
    }

    private static Puzzle decodeLegacy(Mode expected, byte[] bytes) throws IOException {
        if (bytes.length > 16384) throw new IOException("Puzzle save is too large.");
        try (DataInputStream file = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (file.readInt() != 0x4C505A31) throw new IOException("Unknown puzzle save version.");
            int length = file.readInt();
            if (length < 1 || length > 16000) throw new IOException("Invalid puzzle save length.");
            byte[] data = new byte[length]; file.readFully(data);
            CRC32 crc = new CRC32(); crc.update(data);
            if (file.readLong() != crc.getValue() || file.read() != -1) throw new IOException("Puzzle save is damaged.");
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
                if (!expected.name().equals(in.readUTF())) throw new IOException("Puzzle belongs to another game.");
                Puzzle result = expected.read(in);
                if (in.read() != -1) throw new IOException("Unexpected puzzle save data.");
                return result;
            }
        } catch (IllegalArgumentException ex) { throw new IOException("Invalid puzzle save.", ex); }
    }

    static void array(DataOutputStream out, int[] values) throws IOException {
        for (int value : values) out.writeInt(value);
    }

    static int[] array(DataInputStream in, int length, int min, int max) throws IOException {
        int[] values = new int[length];
        for (int i = 0; i < length; i++) {
            values[i] = in.readInt();
            if (values[i] < min || values[i] > max) throw new IOException("Puzzle value out of range.");
        }
        return values;
    }
}
