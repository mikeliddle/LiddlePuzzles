package com.quietgrid.sudoku;

import java.util.Base64;

/** Strict, bounded local transport. Version and mode define all rules, not device defaults. */
public final class LocalShareLink {
    public static final String SCHEME = "liddlepuzzles-local://";
    private static final int MAX_LENGTH = 512;

    private LocalShareLink() {}

    static String fromTransport(String link, String scheme) {
        String prefix = scheme + "://";
        if (link == null || link.length() > MAX_LENGTH || !link.startsWith(prefix))
            throw new IllegalArgumentException("This puzzle link is not for this version of Liddle Puzzles.");
        String canonical = SCHEME + link.substring(prefix.length());
        kind(canonical);
        return canonical;
    }

    static String toTransport(String canonical, String scheme) {
        kind(canonical);
        return scheme + "://" + canonical.substring(SCHEME.length());
    }

    static String manualTransport(String text, String scheme) {
        String link = text.trim();
        if (link.indexOf(':') < 0) link = scheme + "://" + link;
        String canonical = fromTransport(link, scheme);
        String kind = kind(canonical);
        switch (kind) {
            case "sudoku", "daily-word", "takuzu", "nonogram", "futoshiki", "numberlink", "slitherlink" -> {}
            default -> throw new IllegalArgumentException("This puzzle type cannot be shared.");
        }
        decode(canonical, kind);
        return link;
    }

    public static String kind(String link) {
        if (link == null || link.length() > MAX_LENGTH || !link.startsWith(SCHEME))
            throw new IllegalArgumentException("This is not a supported local puzzle link.");
        int end = link.indexOf('/', SCHEME.length());
        if (end < 0) throw new IllegalArgumentException("The puzzle link has no version.");
        String kind = link.substring(SCHEME.length(), end);
        if (!link.startsWith(prefix(kind))) throw new IllegalArgumentException("Unsupported puzzle link version.");
        token(link, kind);
        return kind;
    }

    public static String prefix(String kind) { return SCHEME + kind + "/v1/"; }

    public static String token(String link, String kind) {
        String prefix = prefix(kind);
        if (link == null || link.length() > MAX_LENGTH || !link.startsWith(prefix) || link.length() <= prefix.length())
            throw new IllegalArgumentException("This is not a supported local puzzle link.");
        String token = link.substring(prefix.length());
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (!(c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '-' || c == '_'))
                throw new IllegalArgumentException("The puzzle link contains invalid data.");
        }
        return token;
    }

    public static String encode(String kind, byte[] data) {
        String link = prefix(kind) + Base64.getUrlEncoder().withoutPadding().encodeToString(data);
        token(link, kind);
        return link;
    }

    public static byte[] decode(String link, String kind) {
        byte[] data = Base64.getUrlDecoder().decode(token(link, kind));
        if (!encode(kind, data).equals(link)) throw new IllegalArgumentException("The puzzle link is not canonically encoded.");
        return data;
    }

    public static String saveKey(String link, String kind) {
        String token = token(link, kind);
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            StringBuilder key = new StringBuilder();
            for (byte value : digest) key.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            return key.toString();
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 is unavailable", ex); }
    }
}
