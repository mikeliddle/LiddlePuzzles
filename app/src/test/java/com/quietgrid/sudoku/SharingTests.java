package com.quietgrid.sudoku;

import java.net.URI;
import java.util.Arrays;
import java.util.Base64;
import com.quietgrid.sudoku.puzzles.*;

public final class SharingTests {
    public static void main(String[] args) {
        run();
        System.out.println("PASS: seven-mode puzzle sharing");
    }

    static void run() {
        int[] clues = fixture();
        String link = SudokuShare.link(clues);
        transports(link);
        invalidTransports(link);
        Game imported = SudokuShare.open(link);
        check(Arrays.equals(clues, imported.clues), "Exact starting givens round-trip");
        check(Arrays.equals(clues, imported.values) && imported.elapsedMillis == 0 && !imported.canUndo(),
                "Recipients start with no moves, time or history");
        check(Sudoku.valid(imported.solution) && Sudoku.solutions(clues, 2) == 1, "Solution is reconstructed uniquely");
        int target = 0;
        while (clues[target] != 0) target++;
        imported.selected = target;
        imported.enter(imported.solution[target]);
        imported.notes[target] = Sudoku.ALL;
        imported.elapsedMillis = 123456;
        check(link.equals(SudokuShare.link(imported.clues)), "Sharing omits answers, moves, notes and elapsed time");
        reject(null);
        reject("");
        reject(link + "A");
        reject(link.replace("/v1/", "/v2/"));
        reject(link.replace("liddlepuzzles-local", "https"));
        reject(link + "?tracking=1");
        reject(link + "#fragment");
        reject(SudokuShare.PREFIX + "=".repeat(55));
        reject(SudokuShare.PREFIX + "A".repeat(100000));
        reject(SudokuShare.link(new int[81]));
        reject(SudokuShare.link(imported.solution));
        byte[] packed = Base64.getUrlDecoder().decode(SudokuShare.token(link));
        packed[40] |= 1;
        reject(SudokuShare.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(packed));
        packed[0] = (byte) 0xFF;
        reject(SudokuShare.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(packed));
        int[] inconsistent = clues.clone();
        inconsistent[2] = 5;
        try { SudokuShare.link(inconsistent); throw new AssertionError("Invalid givens accepted"); }
        catch (IllegalArgumentException expected) { /* Rejected before encoding. */ }
        int[] unsolvable = clues.clone();
        unsolvable[2] = 1;
        check(Sudoku.valid(unsolvable), "Unsolvable fixture has no duplicate givens");
        reject(SudokuShare.link(unsolvable));
        puzzleDefinitions();
        wordDateAndSchedule();
    }

    private static void transports(String link) {
        String fragment = link.substring(LocalShareLink.SCHEME.length());
        String web = LocalShareLink.toTransport(link, "liddlepuzzles");
        check(web.equals("https://mllab.fyi/liddle-puzzles/share/#" + fragment), "Exact outbound HTTPS link");
        URI uri = URI.create(web);
        check(uri.getRawPath().equals("/liddle-puzzles/share/") && uri.getRawQuery() == null
                && fragment.equals(uri.getRawFragment()), "Only the fragment carries the puzzle");
        String legacy = "liddlepuzzles://" + fragment;
        check(LocalShareLink.fromTransport(web, "liddlepuzzles").equals(link)
                && LocalShareLink.fromTransport(legacy, "liddlepuzzles").equals(link),
                "HTTPS and legacy VIEW intents keep the same starting definition and save key");
        check(LocalShareLink.toTransport(link, "liddlepuzzles-local").equals(link)
                && LocalShareLink.fromTransport(link, "liddlepuzzles-local").equals(link),
                "Validation still shares and receives its own custom scheme");
        for (String pasted : new String[]{"  " + web + "  ", legacy, fragment}) {
            check(LocalShareLink.manualTransport(pasted, "liddlepuzzles").equals(web), "Release paste resolves to HTTPS");
        }
        for (String pasted : new String[]{web, link, fragment}) {
            check(LocalShareLink.manualTransport(pasted, "liddlepuzzles-local").equals(link),
                    "Validation paste opens inside the validation package");
        }
    }

    private static void invalidTransports(String link) {
        String web = LocalShareLink.toTransport(link, "liddlepuzzles");
        String legacy = "liddlepuzzles://" + link.substring(LocalShareLink.SCHEME.length());
        for (String invalid : new String[]{
                web.replace("https:", "http:"), web.replace("mllab.fyi", "other.mllab.fyi"),
                web.replace("mllab.fyi", "user@mllab.fyi"), web.replace("mllab.fyi", "mllab.fyi:443"),
                web.replace("share/#", "share#"), web.replace("share/#", "share/?q=1#"),
                web.replace("share/#", "share/#%73"), web.replace("/v1/", "/v2/"),
                web.replace("#sudoku/", "#minesweeper/"), web + "#extra", web + "?q=1",
                web + "A".repeat(512), legacy + "#extra", legacy + "?q=1", link}) {
            try {
                LocalShareLink.fromTransport(invalid, "liddlepuzzles");
                throw new AssertionError("Invalid release VIEW link accepted");
            } catch (IllegalArgumentException expected) { /* Reject malformed or foreign routes. */ }
            try {
                LocalShareLink.manualTransport(invalid, "liddlepuzzles");
                throw new AssertionError("Invalid release paste accepted");
            } catch (IllegalArgumentException expected) { /* Reject malformed or foreign routes. */ }
        }
        for (String foreign : new String[]{web, legacy}) {
            try {
                LocalShareLink.fromTransport(foreign, "liddlepuzzles-local");
                throw new AssertionError("Validation accepted a production VIEW link");
            } catch (IllegalArgumentException expected) { /* Validation has no production VIEW route. */ }
        }
        try {
            LocalShareLink.manualTransport(legacy, "liddlepuzzles-local");
            throw new AssertionError("Validation accepted a foreign custom scheme");
        } catch (IllegalArgumentException expected) { /* Only HTTPS is interoperable through explicit paste. */ }
    }

    private static void puzzleDefinitions() {
        for (Mode mode : new Mode[]{Mode.TAKUZU, Mode.NONOGRAM, Mode.FUTOSHIKI, Mode.NUMBERLINK, Mode.SLITHERLINK}) {
            for (int setting : mode.settings()) {
                Puzzle starting = mode.generate(setting, new java.util.Random(9200 + setting), new Generation());
                String link = PuzzleShare.link(starting);
                transports(link);
                Puzzle imported = PuzzleShare.open(link);
                check(link.equals(PuzzleShare.link(imported)) && imported.size() == starting.size(), "Exact " + mode + " definition and variant");
                check(Arrays.equals(((UndoablePuzzle) starting).entries(), ((UndoablePuzzle) imported).entries()),
                        "Starting entries restored without sender moves");
                ((UndoablePuzzle) starting).entries()[0] = 1;
                check(link.equals(PuzzleShare.link(starting)), "No " + mode + " moves in token");
                String kind = PuzzleShare.kind(mode);
                byte[] data = LocalShareLink.decode(link, kind);
                byte[] trailing = Arrays.copyOf(data, data.length + 1);
                rejectPuzzle(LocalShareLink.encode(kind, trailing));
                data[0] = 100;
                rejectPuzzle(LocalShareLink.encode(kind, data));
                rejectPuzzle(link.replace("/v1/", "/v2/"));
                rejectPuzzle(link + "?x=1");
                if (imported instanceof Takuzu p) {
                    Arrays.fill(p.clues, -1);
                    rejectPuzzle(PuzzleShare.link(p));
                } else if (imported instanceof Slitherlink p) {
                    Arrays.fill(p.clues, -1);
                    rejectPuzzle(PuzzleShare.link(p));
                }
            }
        }
        rejectPuzzle("liddlepuzzles-local://minesweeper/v1/AAAA");
        rejectPuzzle(LocalShareLink.prefix("takuzu") + "A".repeat(10000));
    }

    private static void rejectPuzzle(String link) {
        try { PuzzleShare.open(link); throw new AssertionError("Invalid shared definition accepted"); }
        catch (IllegalArgumentException | IllegalStateException expected) { /* Bounded validation rejects invalid puzzles. */ }
    }

    private static void wordDateAndSchedule() {
        try {
            DailyWord game = DailyWordTests.fresh();
            java.time.LocalDate date = DailyWord.EPOCH.plusDays(7);
            String link = WordShare.link(game, date);
            transports(link);
            check(WordShare.date(link, game).equals(date), "Exact Word date");
            game.letter(game.day(date), 'a');
            game.day(date).setElapsed(20000);
            check(link.equals(WordShare.link(game, date)), "Word guesses, draft and elapsed time are not shared");
            DailyWord fresh = DailyWordTests.fresh();
            check(WordShare.date(link, fresh).equals(date) && fresh.day(date).draft().isEmpty(), "Independent recipient Word starts empty");
            check(!new String(LocalShareLink.decode(link, WordShare.KIND), java.nio.charset.StandardCharsets.US_ASCII)
                    .contains(game.answer(date)), "Word token never reveals the answer");
            byte[] changed = LocalShareLink.decode(link, WordShare.KIND); changed[8] ^= 1;
            try { WordShare.date(LocalShareLink.encode(WordShare.KIND, changed), fresh); throw new AssertionError("Unknown schedule accepted"); }
            catch (IllegalArgumentException expected) { /* Never replace a missing frozen schedule. */ }
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            game.write(new java.io.DataOutputStream(bytes));
            fresh.readShared(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())), date);
            check(fresh.day(date).draft().equals("a"), "Only shared date's progress resumes");
            try {
                fresh.readShared(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())), date.plusDays(1));
                throw new AssertionError("Mismatched shared date accepted");
            } catch (java.io.IOException expected) { /* Keep mismatched history untouched. */ }
        } catch (java.io.IOException ex) { throw new AssertionError(ex); }
    }

    static int[] fixture() {
        String digits = "530070000600195000098000060800060003400803001700020006060000280000419005000080079";
        return digits.chars().map(c -> c - '0').toArray();
    }

    private static void reject(String link) {
        try { SudokuShare.open(link); throw new AssertionError("Invalid shared puzzle accepted"); }
        catch (IllegalArgumentException expected) { /* Input errors are explicit. */ }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
