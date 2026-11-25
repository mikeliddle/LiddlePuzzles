package com.quietgrid.sudoku;

import java.util.Base64;
import java.util.regex.Pattern;

/** Strict, bounded local transport. Version and mode define all rules, not device defaults. */
public final class LocalShareLink {
    public static final String SCHEME = "liddlepuzzles-local://";
    private static final String RELEASE_SCHEME = "liddlepuzzles";
    private static final String WEB_PREFIX = "https://mllab.fyi/liddle-puzzles/share/#";
    private static final int MAX_LENGTH = 512;
    static final int MAX_PASTE_LENGTH = 1024;
    // Match the share_solved_puzzle_message envelope, including localized duration digits.
    private static final Pattern SOLVED_MESSAGE = Pattern.compile(
            "This puzzle took me \\p{Nd}{1,15}:(\\p{Nd})\\p{Nd}! {1,2}(\\S+)");

    private LocalShareLink() {}

    static String fromTransport(String link, String scheme) {
        boolean web = RELEASE_SCHEME.equals(scheme) && link != null && link.startsWith(WEB_PREFIX);
        String prefix = web ? WEB_PREFIX : scheme + "://";
        if (link == null || !link.startsWith(prefix) || link.length() > MAX_LENGTH - SCHEME.length() + prefix.length())
            throw new IllegalArgumentException("This puzzle link is not for this version of Liddle Puzzles.");
        String canonical = SCHEME + link.substring(prefix.length());
        switch (kind(canonical)) {
            case "sudoku", "daily-word", "takuzu", "nonogram", "futoshiki", "numberlink", "slitherlink" -> {}
            default -> throw new IllegalArgumentException("This puzzle type cannot be shared.");
        }
        return canonical;
    }

    static String toTransport(String canonical, String scheme) {
        kind(canonical);
        String prefix = RELEASE_SCHEME.equals(scheme) ? WEB_PREFIX : scheme + "://";
        return prefix + canonical.substring(SCHEME.length());
    }

    static String manualTransport(String text, String scheme) {
        if (text.length() > MAX_PASTE_LENGTH) throw new IllegalArgumentException("This puzzle link is too long.");
        String link = text.trim();
        var message = SOLVED_MESSAGE.matcher(link);
        if (message.matches() && Character.digit(message.group(1).codePointAt(0), 10) < 6) {
            link = message.group(2);
        } else if (link.indexOf(':') < 0) link = scheme + "://" + link;
        String canonical = fromTransport(link, link.startsWith(WEB_PREFIX) ? RELEASE_SCHEME : scheme);
        decode(canonical, kind(canonical));
        return toTransport(canonical, scheme);
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
