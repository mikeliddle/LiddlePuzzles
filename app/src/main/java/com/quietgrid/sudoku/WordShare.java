package com.quietgrid.sudoku;

import java.nio.ByteBuffer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Arrays;

/** v1 identifies local-v1's epoch and ordered schedule by digest, never a day's answer. */
final class WordShare {
    static final String KIND = "daily-word";
    private WordShare() {}

    static String link(DailyWord game, LocalDate date) {
        return LocalShareLink.encode(KIND, ByteBuffer.allocate(40)
                .putLong(date.toEpochDay()).put(game.scheduleFingerprint()).array());
    }

    static LocalDate date(String link, DailyWord game) {
        byte[] data = LocalShareLink.decode(link, KIND);
        if (data.length != 40) throw new IllegalArgumentException("Invalid shared Word date or schedule.");
        if (!Arrays.equals(Arrays.copyOfRange(data, 8, 40), game.scheduleFingerprint()))
            throw new IllegalArgumentException("This shared Word schedule is not installed. Its answer has not been substituted.");
        try { return LocalDate.ofEpochDay(ByteBuffer.wrap(data).getLong()); }
        catch (DateTimeException ex) { throw new IllegalArgumentException("Invalid shared Word date.", ex); }
    }
}
