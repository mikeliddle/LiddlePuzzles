package com.quietgrid.sudoku;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

public final class DailyWordTests {
    private static final Path DATA = Path.of("app", "src", "main", "assets", "daily-word");
    private static int checks;

    static void run() throws Exception {
        schedule();
        calendar();
        scoring();
        playAndPersistence();
        badData();
        System.out.println("PASS: " + checks + " Daily Word assertions");
    }

    private static void check(boolean valid, String reason) {
        checks++;
        if (!valid) throw new AssertionError(reason);
    }

    static DailyWord fresh() throws IOException {
        try (var guesses = Files.newBufferedReader(DATA.resolve("guesses-v1.txt"));
             var answers = Files.newBufferedReader(DATA.resolve("answers-v1.txt"))) {
            return new DailyWord(guesses, answers);
        }
    }

    private static void schedule() throws Exception {
        for (String[] expected : new String[][]{
                {"answers-v1.txt", "c74528789b30d50ecc6a9d04fb8cfa05ebe160a79fa5dce80c9b111690b7f7f6"},
                {"guesses-v1.txt", "1e1d84f7a96ac1e53802d28d99f4db6393933d941b2c68e531ba3a9735857e1d"}}) {
            check(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(DATA.resolve(expected[0])))).equals(expected[1]),
                    "Released v1 dictionary/schedule bytes must stay immutable");
        }
        DailyWord first = fresh(), second = fresh();
        check(first.scheduleLength() == 619, "Documented finite cycle");
        check(first.answer(DailyWord.EPOCH).equals("bless"), "Golden epoch assignment");
        check(first.answer(DailyWord.EPOCH.plusDays(1)).equals("sense"), "Golden following day");
        check(first.answer(DailyWord.EPOCH.plusDays(2)).equals("medal"), "Golden third day");
        List<String> distinct = new ArrayList<>();
        for (int i = -619; i < 1238; i++) {
            LocalDate date = DailyWord.EPOCH.plusDays(i);
            check(first.answer(date).equals(second.answer(date)), "Install-independent schedule");
            check(first.answer(date).equals(first.answer(date.plusDays(619))), "Cycle stable before/after epoch");
            if (i >= 0 && i < 619) distinct.add(first.answer(date));
        }
        check(new java.util.HashSet<>(distinct).size() == 619, "Every cycle word is distinct");
    }

    private static void calendar() throws Exception {
        ZoneId la = ZoneId.of("America/Los_Angeles"), ny = ZoneId.of("America/New_York");
        check(date("2028-03-01T07:59:59Z", la).equals(LocalDate.of(2028, 2, 29)), "Leap-day late night");
        check(date("2028-03-01T08:00:00Z", la).equals(LocalDate.of(2028, 3, 1)), "Local midnight, not UTC");
        check(date("2024-03-10T09:59:59Z", la).equals(date("2024-03-10T10:00:00Z", la)), "DST spring gap not a new puzzle");
        check(date("2024-11-03T08:59:59Z", la).equals(date("2024-11-03T09:00:00Z", la)), "DST repeated hour not a new puzzle");
        check(date("2024-03-11T06:59:59Z", la).equals(LocalDate.of(2024, 3, 10)), "23-hour day lasts until midnight");
        check(date("2024-03-11T07:00:00Z", la).equals(LocalDate.of(2024, 3, 11)), "Midnight after 23-hour day");
        check(date("2024-11-04T07:59:59Z", la).equals(LocalDate.of(2024, 11, 3)), "25-hour day lasts until midnight");
        check(date("2024-11-04T08:00:00Z", la).equals(LocalDate.of(2024, 11, 4)), "Midnight after 25-hour day");
        check(fresh().answer(date("2026-11-17T06:00:00Z", la))
                .equals(fresh().answer(date("2026-11-16T18:00:00Z", ny))), "Same local date across zones, same answer");
        check(!date("2026-11-16T12:00:00Z", ZoneId.of("Pacific/Kiritimati"))
                .equals(date("2026-11-16T12:00:00Z", ZoneId.of("Pacific/Honolulu"))), "Date-line devices may have different dates");
    }

    private static LocalDate date(String instant, ZoneId zone) {
        return DailyWord.today(Clock.fixed(Instant.parse(instant), ZoneOffset.UTC), zone);
    }

    private static void scoring() {
        check(Arrays.equals(DailyWord.score("apple", "alley"), new int[]{2, 1, 0, 1, 0}), "Excess duplicates do not become yellow");
        check(Arrays.equals(DailyWord.score("apple", "eerie"), new int[]{0, 0, 0, 0, 2}), "Greens consume counts before yellows");
        for (int a = 0; a < 32; a++) for (int g = 0; g < 32; g++) {
            String answer = binaryWord(a), guess = binaryWord(g);
            int[] reference = new int[5];
            List<Character> pool = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                if (answer.charAt(i) == guess.charAt(i)) reference[i] = 2;
                else pool.add(answer.charAt(i));
            }
            for (int i = 0; i < 5; i++) if (reference[i] != 2 && pool.remove(Character.valueOf(guess.charAt(i)))) reference[i] = 1;
            check(Arrays.equals(reference, DailyWord.score(answer, guess)), "Exhaustive duplicate oracle " + a + "/" + g);
        }
    }

    private static String binaryWord(int value) {
        StringBuilder word = new StringBuilder();
        for (int i = 0; i < 5; i++) word.append((value & (1 << i)) == 0 ? 'a' : 'b');
        return word.toString();
    }

    private static void type(DailyWord game, DailyWord.Day day, String word) {
        while (game.erase(day)) { }
        for (char letter : word.toCharArray()) check(game.letter(day, letter), "Letter accepted");
    }

    private static void playAndPersistence() throws Exception {
        DailyWord game = fresh();
        DailyWord.Day day = game.day(DailyWord.EPOCH);
        check(game.submit(day) != null && day.guesses().isEmpty(), "Short guess consumes no attempt");
        type(game, day, "zzzzz");
        check(game.submit(day) != null && day.guesses().isEmpty(), "Invalid word consumes no attempt");
        check(!game.letter(day, 'a'), "Five-letter draft cap");
        type(game, day, "apple");
        check(game.submit(day) == null && day.guesses().size() == 1, "Valid guess");
        type(game, day, "apple");
        check(game.submit(day) != null && day.guesses().size() == 1, "Repeated guess consumes no attempt");
        type(game, day, "ble");
        byte[] before = bytes(game);
        DailyWord restored = fresh();
        restored.read(new DataInputStream(new ByteArrayInputStream(before)));
        check(restored.day(DailyWord.EPOCH).guesses().equals(List.of("apple"))
                && restored.day(DailyWord.EPOCH).draft().equals("ble"), "Draft and attempts survive restart");
        check(Arrays.equals(before, bytes(restored)), "Exact canonical round trip");
        DailyWord.Day tomorrow = restored.day(DailyWord.EPOCH.plusDays(1));
        type(restored, tomorrow, "sense");
        check(restored.submit(tomorrow) == null && restored.won(tomorrow), "New date has independent win");
        DailyWord.Day yesterday = restored.day(DailyWord.EPOCH);
        check(yesterday.draft().equals("ble") && yesterday.guesses().size() == 1, "Returning to old date never resets progress");
        type(restored, yesterday, "bless");
        check(restored.submit(yesterday) == null && restored.won(yesterday), "Finish restored draft");
        check(!restored.letter(yesterday, 'a') && !restored.erase(yesterday) && restored.submit(yesterday) != null, "Won day is immutable");
        check(restored.keyboard(yesterday)['e' - 'a'] == DailyWord.CORRECT, "Keyboard retains strongest evidence");
        DailyWord copy = new DailyWord(restored);
        copy.day(DailyWord.EPOCH.plusDays(2));
        check(bytes(copy).length > bytes(restored).length, "Transactional snapshot independent of original");

        DailyWord loss = fresh();
        DailyWord.Day lost = loss.day(DailyWord.EPOCH);
        for (String guess : List.of("crane", "grace", "apple", "bread", "house", "slate")) {
            type(loss, lost, guess);
            check(loss.submit(lost) == null, "Six accepted wrong guesses");
        }
        check(loss.finished(lost) && !loss.won(lost) && lost.guesses().size() == 6, "Six-guess loss");
        check(!loss.letter(lost, 'a') && !loss.erase(lost) && loss.submit(lost) != null, "No seventh guess");
        DailyWord reloaded = fresh();
        reloaded.read(new DataInputStream(new ByteArrayInputStream(bytes(loss))));
        check(reloaded.finished(reloaded.day(DailyWord.EPOCH)), "Loss persists");
        check(!reloaded.finished(reloaded.day(DailyWord.EPOCH.plusDays(1))), "Rollover unlocks next puzzle only");
        check(reloaded.finished(reloaded.day(DailyWord.EPOCH)), "Clock rollback cannot reset played day");
    }

    private static byte[] bytes(DailyWord game) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        game.write(new DataOutputStream(bytes));
        return bytes.toByteArray();
    }

    private static void badData() throws Exception {
        for (String words : new String[]{"apple apple", "APPLE", "four", ""}) {
            try {
                new DailyWord(new StringReader(words), new StringReader("apple"));
                throw new AssertionError("Invalid vocabulary accepted");
            } catch (IOException expected) { checks++; }
        }
        DailyWord game = fresh();
        type(game, game.day(DailyWord.EPOCH), "apple");
        byte[] baseline = bytes(game);
        for (byte[] bad : new byte[][]{new byte[0], Arrays.copyOf(baseline, baseline.length - 1),
                Arrays.copyOf(baseline, baseline.length + 1)}) {
            try {
                game.read(new DataInputStream(new ByteArrayInputStream(bad)));
                throw new AssertionError("Corrupt save accepted");
            } catch (IOException expected) {
                check(Arrays.equals(bytes(game), baseline), "Corrupt read never replaces current records");
            }
        }
        ByteArrayOutputStream bad = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bad);
        out.writeInt(0x51445731); out.writeUTF(DailyWord.SCHEDULE); out.writeInt(1);
        out.writeUTF(DailyWord.EPOCH.toString()); out.writeInt(7);
        try {
            game.read(new DataInputStream(new ByteArrayInputStream(bad.toByteArray())));
            throw new AssertionError("Seven-guess save accepted");
        } catch (IOException expected) { check(Arrays.equals(bytes(game), baseline), "Invalid attempt count fails closed"); }
    }
}
