package com.quietgrid.sudoku;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Stable identifiers for reproducible generator evidence, not a runtime catalog. */
final class GeneratedPuzzleSamples {
    private GeneratedPuzzleSamples() {}

    static String givens(int[] clues) {
        StringBuilder result = new StringBuilder(clues.length);
        for (int clue : clues) result.append((char) ('0' + clue));
        return result.toString();
    }

    static String id(int[] clues) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(givens(clues).getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException ex) {
            throw new AssertionError("SHA-256 is required by Java", ex);
        }
    }
}
