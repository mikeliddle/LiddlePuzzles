package com.quietgrid.sudoku.puzzles;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Random;

public enum Mode {
    TAKUZU("Takuzu",
            "Fill every square with 0 or 1. Each row and column must contain equal numbers of both: three each on 6x6, four each on 8x8. "
            + "Never place three identical digits in a row, horizontally or vertically. All completed rows must differ, and all completed columns must differ.\n\n"
            + "By default, tap a square to enter 0 or hold it to enter 1. Repeat the same default tap/hold value to clear it. "
            + "Or select 0 or 1, then tap squares repeatedly to enter that digit. Holding still enters or clears 1. "
            + "Switching digits changes only the tool, never the last square. Tap the active tool again or outside the grid and tool bank to return to default tap/hold input. "
            + "The Erase tool (eraser icon) selects repeated local erasure. No separate cell-first selection is needed. "
            + "Given clues cannot be changed. Undo restores the last real edit, including after completion; up to 500 moves survive app restarts. "
            + "For example, 00_ must end in 1. Zeros are cyan and ones amber, with actual digits retained. Starting clues are underlined and fixed. "
            + "An exclamation mark and error outline flag triples, too many of one digit, or identical completed rows or columns; square descriptions explain why. "
            + "Errors clear when corrected. The large completion banner appears only when every rule is satisfied. "
            + "The Takuzu menu offers 6x6 or 8x8 for NEW puzzles; Resume keeps the saved board's size. Replacing active progress requires confirmation. "
            + "The full board and controls fit together without scrolling. On constrained screens they scale down; native keyboard and screen-reader square actions remain available."),
    NONOGRAM("Nonograms",
            "Numbers beside a row or above a column describe its filled runs, in order. Separate consecutive runs by at least one empty square. "
            + "A clue of 0 means no filled squares. For example, 2 1 means two filled squares, a gap, then one filled square.\n\n"
            + "Each tap cycles a square from undecided (blank) to filled (check mark), then crossed empty (X), then blank again. "
            + "Hold to enter a cross directly, or clear an existing cross to blank. "
            + "Native long-click actions also enter or clear a cross. Match every row and column clue. "
            + "A red dashed clue outline means no placement of its runs fits your current filled and crossed-empty squares. Undecided squares stay unconstrained; an unfinished line alone is not an error. "
            + "The signal concerns the whole row or column, not one particular square, and does not check against a hidden answer. Correcting marks or Undo updates it immediately. "
            + "The menu offers 5x5, 8x8 and 10x10 size-based challenges. Larger does not mean a certified solving-technique difficulty. "
            + "New puzzle generates a fresh, uniquely solvable set of clues locally, with no finite puzzle pack to exhaust; Resume retains the saved board's size. "
            + "The full board, clues and controls fit together without scrolling, scaling down on constrained screens; every square and clue also has native keyboard/screen-reader access. "
            + "Crosses help your reasoning; only filled squares determine the result. Undo restores the last square change, including after completion; up to 500 moves are saved."),
    FUTOSHIKI("Futoshiki",
            "Fill each row and column with 1 to 4, once each. Follow every inequality: the pointed end faces the smaller number. "
            + "For example, 1 < 3 and 4 > 2. A vertical v points to the smaller number below; ^ points to the smaller number above.\n\n"
            + "Tap a square, then a number in the bank. Erase (eraser icon) removes that square's entry. Given numbers cannot be changed. "
            + "The selected entry is underlined. An outline and ! flag repeated row/column digits or a broken inequality between entered numbers; square descriptions explain the rule. "
            + "Blank squares alone are not errors. Correction or Undo updates the signal immediately. "
            + "Undo restores the last entry or erasure, including after completion; up to 500 moves are saved."),
    MINESWEEPER("Minesweeper",
            "Reveal all squares without mines. A number counts mines in the eight neighboring squares. Blank squares flood open. "
            + "Your first reveal and its neighbors are safe. Later moves may require guessing; this is not a no-guess generator.\n\n"
            + "Tap a square to reveal it; press and hold to place or remove a flag without also revealing. Revealed squares have a different persistent fill from covered squares. "
            + "The Flag tool remains available for repeated tap-to-flag entry and keyboard/screen-reader use. Flags prevent accidental reveals. "
            + "Tap a revealed number with exactly that many neighboring flags to reveal its other neighbors. Incorrect flags can expose a mine; flags are not checked against the answer. Holding a revealed number does nothing. "
            + "The menu offers an 8x8 grid with ten or fourteen mines. Flags are limited to that board's mine count. "
            + "More mines changes density, not the safe-first-reveal guarantee. A mine ends the game; revealing every safe square wins."),
    NUMBERLINK("Numberlink",
            "Connect each matching pair of numbered endpoints with one continuous path. Move only horizontally or vertically. "
            + "Paths may not cross or share squares. Cover EVERY square, and do not branch or form extra loops.\n\n"
            + "Drag from an endpoint or an existing path through neighboring squares. Drag back along your route to retract it. "
            + "Starting within a path keeps its prefix from the first numbered endpoint and replaces its tail when you move. "
            + "Other paths and different-number endpoints cannot be overwritten. Release saves the whole drag as one Undo move; interruption cancels it. "
            + "Alternatively, tap two neighboring squares to toggle their connecting edge; native keyboard and screen-reader square actions work the same way. "
            + "Selected squares are outlined. Tap a selected square to deselect it. Toggle an existing edge off to edit a path. "
            + "Each pair has its own stable color, with actual numbers retained. New puzzles use a 6x6 board with five pairs. Older 4x4 puzzles and their Undo history can still be resumed. "
            + "The 6x6 generator requires nontrivial routes, at least twelve bends and ten extra route steps beyond shortest endpoint distances across the unique solution. "
            + "These measured route constraints are not a certified human difficulty rating. All sizes still require complete coverage. "
            + "A disconnected tap path can also be dragged; it acquires a pair identity when joined to an endpoint. "
            + "Undo restores the last drag or edge edit, including after completion; up to 500 moves are saved."),
    SLITHERLINK("Slitherlink",
            "Draw exactly one closed loop along the edges between dots. A number inside a square specifies how many of its four sides belong to the loop. "
            + "Blank squares have no count constraint. No branches, loose ends, or separate smaller loops are allowed. For example, a 0 has no line on any side.\n\n"
            + "Tap an edge to draw a line, or hold to cross it out. Repeat the same mark to clear it. Crosses are notes, not loop edges. "
            + "Cell clues lists every numbered or blank square with coordinates. You can also choose any edge from the accessible edge controls below the board and use Line, Cross or Cycle. "
            + "Red lines or clue digits with subtle outline rings mark branches, too many drawn sides for a clue, or a closed loop incompatible with remaining clues or drawn lines. "
            + "An unfinished path alone is not an error. Cell clues and Edge controls retain local reasons in screen-reader descriptions, without visible error paragraphs; correction or Undo updates them. "
            + "Choose 4x4, 6x6 or 8x8 for a fresh uniquely generated loop. These are size choices, not certified human difficulty ratings. Resume keeps the saved size. "
            + "Undo restores the last edge change, including crosses or completion; up to 500 moves are saved.");

    public final String title, help;
    Mode(String title, String help) { this.title = title; this.help = help; }

    public int[] settings() {
        switch (this) {
            case TAKUZU: return new int[]{6, 8};
            case NONOGRAM: return new int[]{5, 8, 10};
            case NUMBERLINK: return new int[]{6};
            case MINESWEEPER: return new int[]{10, 14};
            case SLITHERLINK: return new int[]{4, 6, 8};
            default: return new int[]{0};
        }
    }

    public int defaultSetting() { return this == TAKUZU ? 8 : this == NUMBERLINK ? 6 : settings()[0]; }
    public String settingKey() { return this == TAKUZU ? "takuzuSize" : name().toLowerCase(java.util.Locale.ROOT) + "Setting"; }
    public boolean supports(int setting) { for (int value : settings()) if (value == setting) return true; return false; }
    public String settingLabel(int setting) {
        if (!supports(setting) && !(this == NUMBERLINK && (setting == 3 || setting == 4)))
            throw new IllegalArgumentException("Unsupported puzzle setting.");
        switch (this) {
            case TAKUZU: return setting + " \u00d7 " + setting;
            case NUMBERLINK: return setting == 6 ? "6 \u00d7 6 \u00b7 5 pairs" : "4 \u00d7 4 \u00b7 " + setting + " pairs";
            case MINESWEEPER: return "8 \u00d7 8 \u00b7 " + setting + " mines";
            case NONOGRAM: return setting + " \u00d7 " + setting;
            case SLITHERLINK: return setting + " \u00d7 " + setting;
            default: return "4 \u00d7 4";
        }
    }

    public int settingOf(Puzzle puzzle) {
        if (puzzle.mode() != this) throw new IllegalArgumentException("Puzzle belongs to another mode.");
        if (puzzle instanceof Takuzu) return puzzle.size();
        if (puzzle instanceof Nonogram) return puzzle.size();
        if (puzzle instanceof Slitherlink) return puzzle.size();
        if (puzzle instanceof Numberlink) return puzzle.size() == 6 ? 6 : ((Numberlink) puzzle).pairs();
        if (puzzle instanceof Minesweeper) return ((Minesweeper) puzzle).mineCount;
        return 0;
    }

    public Puzzle generate(int setting, Random random, Generation budget) {
        if (!supports(setting)) throw new IllegalArgumentException("Unsupported puzzle setting.");
        switch (this) {
            case TAKUZU: return Takuzu.generate(setting, random, budget);
            case NONOGRAM: return Nonogram.generate(setting, random, budget);
            case NUMBERLINK: return Numberlink.generate(setting, random, budget);
            case MINESWEEPER: return new Minesweeper(random.nextLong(), setting);
            case SLITHERLINK: return Slitherlink.generate(setting, random, budget);
            default: return generate(random, budget);
        }
    }

    public Puzzle generate(Random random, Generation budget) {
        switch (this) {
            case TAKUZU: return Takuzu.generate(random, budget);
            case NONOGRAM: return Nonogram.generate(random, budget);
            case FUTOSHIKI: return Futoshiki.generate(random, budget);
            case MINESWEEPER: return new Minesweeper(random.nextLong());
            case NUMBERLINK: return Numberlink.generate(random, budget);
            case SLITHERLINK: return Slitherlink.generate(random, budget);
            default: throw new AssertionError(this);
        }
    }

    Puzzle read(DataInputStream in) throws IOException {
        switch (this) {
            case TAKUZU: return Takuzu.read(in);
            case NONOGRAM: return Nonogram.read(in);
            case FUTOSHIKI: return Futoshiki.read(in);
            case MINESWEEPER: return Minesweeper.read(in);
            case NUMBERLINK: return Numberlink.read(in);
            case SLITHERLINK: return Slitherlink.read(in);
            default: throw new AssertionError(this);
        }
    }
}
