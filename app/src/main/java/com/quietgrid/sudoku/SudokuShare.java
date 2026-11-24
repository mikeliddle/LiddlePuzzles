package com.quietgrid.sudoku;

import java.util.Base64;

/** Sudoku v1 definition codec. Android maps each build's transport to this internal form. */
final class SudokuShare {
    static final String PREFIX = "liddlepuzzles-local://sudoku/v1/";
    private static final int TOKEN_LENGTH = 55;

    private SudokuShare() {}

    static String link(int[] clues) {
        if (!Sudoku.valid(clues)) throw new IllegalArgumentException("The starting givens are invalid.");
        byte[] packed = new byte[41];
        for (int i = 0; i < 81; i++) packed[i / 2] |= (byte) (clues[i] << (i % 2 == 0 ? 4 : 0));
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(packed);
    }

    static String token(String link) {
        if (link == null || link.length() != PREFIX.length() + TOKEN_LENGTH || !link.startsWith(PREFIX)) {
            throw new IllegalArgumentException("This is not a supported local Sudoku link.");
        }
        String token = link.substring(PREFIX.length());
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (!(c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '-' || c == '_')) {
                throw new IllegalArgumentException("The Sudoku link contains invalid data.");
            }
        }
        return token;
    }

    static Game open(String link) {
        String token = token(link);
        byte[] packed = Base64.getUrlDecoder().decode(token);
        int[] clues = new int[81];
        for (int i = 0; i < 81; i++) clues[i] = (packed[i / 2] >>> (i % 2 == 0 ? 4 : 0)) & 15;
        if (!link(clues).equals(link)) throw new IllegalArgumentException("The Sudoku link is not canonically encoded.");
        int[] solution = Sudoku.uniqueSolution(clues);
        return new Game(new Sudoku.Puzzle(clues, solution, Sudoku.Difficulty.FOCUSED, null));
    }
}
