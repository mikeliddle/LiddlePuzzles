package com.quietgrid.sudoku;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IntSummaryStatistics;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/** Fixed-seed generated-Easy calibration, separate from the unfiltered classification sample. */
public final class EasyGenerationBatch {
    private static final long FIRST_SEED = 2026111600L;
    private static final int COUNT = 100;

    private EasyGenerationBatch() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !args[1].matches("before|after")) {
            throw new IllegalArgumentException("Supply an output directory and before/after stage.");
        }
        Path output = Path.of(args[0]).resolve(args[1]);
        Files.createDirectories(output);
        IntSummaryStatistics clues = new IntSummaryStatistics(), openings = new IntSummaryStatistics();
        IntSummaryStatistics passes = new IntSummaryStatistics();
        Set<String> seen = new HashSet<>();
        long start = System.nanoTime();
        double generationMillis = 0;
        try (BufferedWriter rows = Files.newBufferedWriter(output.resolve("puzzles.csv"), StandardCharsets.UTF_8);
             BufferedWriter traces = Files.newBufferedWriter(output.resolve("traces.csv"), StandardCharsets.UTF_8)) {
            rows.write("index,seed,id,givens,solution,classification,clues,opening_hidden_cells,unique_solutions,hidden_placements,hidden_passes,remaining,generation_ms\n");
            traces.write("index,step,pass,house,cell,digit\n");
            for (int index = 1; index <= COUNT; index++) {
                long seed = FIRST_SEED + index - 1, before = System.nanoTime();
                Sudoku.Puzzle puzzle = Sudoku.generate(Sudoku.Difficulty.FOCUSED, new Random(seed));
                double elapsed = (System.nanoTime() - before) / 1e6;
                generationMillis += elapsed;
                if (!seen.add(PuzzleLibrary.id(puzzle.clues))) throw new AssertionError("Duplicate puzzle");
                DifficultyTests.verifyPuzzle(puzzle);
                if (puzzle.difficulty != Sudoku.Difficulty.FOCUSED || !puzzle.rating.solved
                        || puzzle.rating.nakedSingles != 0) throw new AssertionError("Not hidden-only Easy");
                int clueCount = (int) Arrays.stream(puzzle.clues).filter(value -> value != 0).count();
                int opening = DifficultyTests.openingHiddenSingles(puzzle.clues);
                clues.accept(clueCount); openings.accept(opening); passes.accept(puzzle.rating.passes);
                rows.write(String.format(Locale.ROOT, "%d,%d,%s,%s,%s,Easy,%d,%d,1,%d,%d,%d,%.3f%n",
                        index, seed, PuzzleLibrary.id(puzzle.clues), PuzzleLibrary.givens(puzzle.clues),
                        PuzzleLibrary.givens(puzzle.solution), clueCount, opening,
                        puzzle.rating.hiddenSingles, puzzle.rating.passes, puzzle.rating.remaining, elapsed));
                int number = 0;
                for (Sudoku.Step step : puzzle.rating.steps) {
                    traces.write(index + "," + (++number) + "," + step.pass + "," + step.house
                            + "," + step.cell + "," + step.digit + "\n");
                }
            }
        }
        try (BufferedWriter controls = Files.newBufferedWriter(output.resolve("controls.csv"), StandardCharsets.UTF_8)) {
            controls.write("sampler,seed,id,givens,classification,hidden,naked,passes,remaining\n");
            for (int index = 0; index < 12; index++) {
                long seed = FIRST_SEED + index;
                control(controls, "common", seed, Sudoku.generateCandidate(new Random(seed)));
                for (Sudoku.Difficulty tier : new Sudoku.Difficulty[]{Sudoku.Difficulty.MEDIUM, Sudoku.Difficulty.HARD}) {
                    control(controls, tier.label, seed, Sudoku.generate(tier, new Random(seed)));
                }
            }
        }
        double elapsed = (System.nanoTime() - start) / 1e9;
        String summary = "{\n  \"stage\": \"" + args[1] + "\",\n  \"count\": " + COUNT
                + ",\n  \"firstSeed\": " + FIRST_SEED + ",\n  \"lastSeed\": " + (FIRST_SEED + COUNT - 1)
                + ",\n  \"sampling\": \"One fresh Random(seed) per requested generated Easy; rejection sampling, not natural classification prevalence. Fixed seeds selected before changing production code.\","
                + "\n  \"verification\": \"Independent uniqueness proof, trace replay and snapshot-wave fixed point for every puzzle; controls record unchanged common/Medium/Hard samplers.\","
                + "\n  \"metricSemantics\": \"Openings count distinct cells forced by hidden singles in the original givens, without cascading placements. Passes include the terminal no-change pass; they are order-dependent, not a human difficulty score.\","
                + "\n  \"clues\": " + stats(clues) + ",\n  \"openingHiddenCells\": " + stats(openings)
                + ",\n  \"hiddenPasses\": " + stats(passes)
                + String.format(Locale.ROOT, ",\n  \"generationMillis\": %.3f,\n  \"elapsedSeconds\": %.3f\n}\n",
                        generationMillis, elapsed);
        Files.writeString(output.resolve("summary.json"), summary, StandardCharsets.UTF_8);
        System.out.println(summary);
    }

    private static String stats(IntSummaryStatistics values) {
        return String.format(Locale.ROOT, "{\"min\":%d,\"max\":%d,\"mean\":%.3f}",
                values.getMin(), values.getMax(), values.getAverage());
    }

    private static void control(BufferedWriter output, String sampler, long seed, Sudoku.Puzzle puzzle) throws Exception {
        output.write(sampler + "," + seed + "," + PuzzleLibrary.id(puzzle.clues) + ","
                + PuzzleLibrary.givens(puzzle.clues) + "," + puzzle.difficulty.label + ","
                + puzzle.rating.hiddenSingles + "," + puzzle.rating.nakedSingles + ","
                + puzzle.rating.passes + "," + puzzle.rating.remaining + "\n");
    }
}
