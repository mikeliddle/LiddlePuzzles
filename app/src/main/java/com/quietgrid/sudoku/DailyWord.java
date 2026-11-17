package com.quietgrid.sudoku;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Offline calendar schedule and independent, date-keyed Daily Word progress. */
public final class DailyWord {
    public static final String SCHEDULE = "local-v1";
    public static final LocalDate EPOCH = LocalDate.of(2026, 11, 16);
    public static final int ABSENT = 0, PRESENT = 1, CORRECT = 2;
    private final Set<String> allowed;
    private final List<String> answers;
    private final Map<LocalDate, Day> days = new LinkedHashMap<>();

    public static final class Day {
        public final LocalDate date;
        private final List<String> guesses = new ArrayList<>();
        private String draft = "";

        private Day(LocalDate date) { this.date = date; }
        private Day(Day source) {
            date = source.date;
            guesses.addAll(source.guesses);
            draft = source.draft;
        }
        public List<String> guesses() { return Collections.unmodifiableList(guesses); }
        public String draft() { return draft; }
    }

    public DailyWord(Reader guesses, Reader schedule) throws IOException {
        allowed = new HashSet<>(words(guesses));
        answers = Collections.unmodifiableList(words(schedule));
        if (allowed.isEmpty() || answers.isEmpty() || !allowed.containsAll(answers)) {
            throw new IOException("Daily Word dictionary or answer schedule is incomplete.");
        }
    }

    public DailyWord(DailyWord source) {
        allowed = source.allowed;
        answers = source.answers;
        for (Day day : source.days.values()) days.put(day.date, new Day(day));
    }

    private static List<String> words(Reader source) throws IOException {
        List<String> words = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        BufferedReader reader = new BufferedReader(source);
        for (String line; (line = reader.readLine()) != null;) {
            for (String word : line.trim().split("\\s+")) {
                if (word.isEmpty()) continue;
                if (!word.matches("[a-z]{5}") || !seen.add(word)) {
                    throw new IOException("Daily Word contains invalid or duplicate dictionary data.");
                }
                words.add(word);
            }
        }
        return words;
    }

    public static LocalDate today(Clock clock, ZoneId zone) {
        return clock.instant().atZone(zone).toLocalDate();
    }

    public int scheduleLength() { return answers.size(); }
    public String answer(LocalDate date) {
        return answers.get((int) Math.floorMod(date.toEpochDay() - EPOCH.toEpochDay(), answers.size()));
    }
    public Day day(LocalDate date) { return days.computeIfAbsent(date, Day::new); }
    public boolean won(Day day) {
        return !day.guesses.isEmpty() && day.guesses.get(day.guesses.size() - 1).equals(answer(day.date));
    }
    public boolean finished(Day day) { return won(day) || day.guesses.size() == 6; }

    public boolean letter(Day day, char letter) {
        if (finished(day) || day.draft.length() == 5 || letter < 'a' || letter > 'z') return false;
        day.draft += letter;
        return true;
    }

    public boolean erase(Day day) {
        if (finished(day) || day.draft.isEmpty()) return false;
        day.draft = day.draft.substring(0, day.draft.length() - 1);
        return true;
    }

    /** Returns a visible error without consuming an attempt, or null on success. */
    public String submit(Day day) {
        if (finished(day)) return "Today's puzzle is finished. Come back after local midnight.";
        if (day.draft.length() != 5) return "Enter five letters.";
        if (!allowed.contains(day.draft)) return "Not in the word list. Try another word.";
        if (day.guesses.contains(day.draft)) return "You already tried that word.";
        day.guesses.add(day.draft);
        day.draft = "";
        return null;
    }

    public int[] score(Day day, String guess) { return score(answer(day.date), guess); }

    public static int[] score(String answer, String guess) {
        if (!answer.matches("[a-z]{5}") || !guess.matches("[a-z]{5}")) {
            throw new IllegalArgumentException("Scoring requires two five-letter English words.");
        }
        int[] score = new int[5], remaining = new int[26];
        for (int i = 0; i < 5; i++) {
            if (answer.charAt(i) == guess.charAt(i)) score[i] = CORRECT;
            else remaining[answer.charAt(i) - 'a']++;
        }
        for (int i = 0; i < 5; i++) if (score[i] != CORRECT) {
            int letter = guess.charAt(i) - 'a';
            if (remaining[letter] > 0) { score[i] = PRESENT; remaining[letter]--; }
        }
        return score;
    }

    public int[] keyboard(Day day) {
        int[] result = new int[26];
        java.util.Arrays.fill(result, -1);
        for (String guess : day.guesses) {
            int[] scored = score(day, guess);
            for (int i = 0; i < 5; i++) {
                int letter = guess.charAt(i) - 'a';
                result[letter] = Math.max(result[letter], scored[i]);
            }
        }
        return result;
    }

    public void write(DataOutputStream out) throws IOException {
        out.writeInt(0x51445731);
        out.writeUTF(SCHEDULE);
        out.writeInt(days.size());
        for (Day day : days.values()) {
            out.writeUTF(day.date.toString());
            out.writeInt(day.guesses.size());
            for (String guess : day.guesses) out.writeUTF(guess);
            out.writeUTF(day.draft);
        }
    }

    public void read(DataInputStream in) throws IOException {
        if (in.readInt() != 0x51445731 || !in.readUTF().equals(SCHEDULE)) {
            throw new IOException("Unrecognized Daily Word save or schedule.");
        }
        int count = in.readInt();
        if (count < 0 || count > 366000) throw new IOException("Invalid Daily Word record count.");
        Map<LocalDate, Day> restored = new LinkedHashMap<>();
        try {
            for (int i = 0; i < count; i++) {
                LocalDate date = LocalDate.parse(in.readUTF());
                if (restored.containsKey(date)) throw new IOException("Duplicate Daily Word date.");
                Day day = new Day(date);
                int attempts = in.readInt();
                if (attempts < 0 || attempts > 6) throw new IOException("Invalid Daily Word attempt count.");
                for (int j = 0; j < attempts; j++) {
                    day.draft = in.readUTF();
                    if (submit(day) != null) throw new IOException("Invalid Daily Word saved guess.");
                }
                String draft = in.readUTF();
                if (!draft.matches("[a-z]{0,5}") || (finished(day) && !draft.isEmpty())) {
                    throw new IOException("Invalid Daily Word draft.");
                }
                day.draft = draft;
                restored.put(date, day);
            }
            if (in.read() != -1) throw new IOException("Unexpected Daily Word save contents.");
        } catch (DateTimeParseException ex) {
            throw new IOException("Invalid Daily Word date.", ex);
        }
        days.clear();
        days.putAll(restored);
    }
}
