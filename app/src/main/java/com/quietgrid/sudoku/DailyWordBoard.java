package com.quietgrid.sudoku;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** Readable rows for TalkBack; marks distinguish feedback without relying on color. */
final class DailyWordBoard extends LinearLayout {
    private final TextView[][] tiles = new TextView[6][5];
    private final LinearLayout[] rows = new LinearLayout[6];
    private final MainActivity.Palette palette;

    DailyWordBoard(Context context, MainActivity.Palette palette) {
        super(context);
        this.palette = palette;
        setOrientation(VERTICAL);
        setPadding(dp(6), dp(6), dp(6), dp(6));
        for (int r = 0; r < 6; r++) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(HORIZONTAL);
            row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            row.setFocusable(true);
            rows[r] = row;
            addView(row, new LayoutParams(-1, 0, 1));
            for (int c = 0; c < 5; c++) {
                TextView tile = new TextView(context);
                tile.setGravity(Gravity.CENTER);
                tile.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                tile.setIncludeFontPadding(false);
                tile.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                LayoutParams slot = new LayoutParams(0, -1, 1);
                slot.setMargins(dp(3), dp(3), dp(3), dp(3));
                row.addView(tile, slot);
                tiles[r][c] = tile;
            }
        }
    }

    void focusDraft(int row) {
        rows[row].setFocusableInTouchMode(true);
        rows[row].requestFocus();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = resolveSize(dp(320), widthSpec);
        int height = resolveSize(Math.round((width - dp(12)) * 6f / 5 + dp(12)), heightSpec);
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float density = getResources().getDisplayMetrics().density;
        float scale = getResources().getConfiguration().fontScale;
        float fittingSp = Math.min((w / density - 42) / 5f, (h / density - 48) / 6f) / (1.85f * scale);
        for (TextView[] row : tiles) for (TextView tile : row) tile.setTextSize(Math.min(32, fittingSp));
    }

    void show(DailyWord game, DailyWord.Day day) {
        for (int r = 0; r < 6; r++) {
            boolean submitted = r < day.guesses().size();
            boolean active = !game.finished(day) && r == day.guesses().size();
            String word = submitted ? day.guesses().get(r) : active ? day.draft() : "";
            int[] scored = submitted ? game.score(day, word) : null;
            for (int c = 0; c < 5; c++) {
                TextView tile = tiles[r][c];
                int state = submitted ? scored[c] : -1;
                String letter = c < word.length() ? word.substring(c, c + 1).toUpperCase(Locale.ROOT) : "";
                String mark = state == DailyWord.CORRECT ? "\u2713" : state == DailyWord.PRESENT ? "\u25CB"
                        : state == DailyWord.ABSENT ? "\u2013" : "";
                SpannableString display = new SpannableString(letter + "\n" + mark);
                display.setSpan(new RelativeSizeSpan(.48f), letter.length(), display.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                tile.setText(display);
                tile.setTextColor(foreground(palette, state));
                GradientDrawable background = new GradientDrawable();
                background.setColor(fill(palette, state));
                background.setCornerRadius(dp(6));
                if (!submitted) background.setStroke(dp(active ? 2 : 1), active ? palette.accent : palette.line);
                tile.setBackground(background);
            }
            rows[r].setContentDescription(rowDescription(game, day, r));
        }
    }

    static String rowDescription(DailyWord game, DailyWord.Day day, int row) {
        boolean submitted = row < day.guesses().size();
        boolean active = !game.finished(day) && row == day.guesses().size();
        String word = submitted ? day.guesses().get(row) : active ? day.draft() : "";
        int[] scores = submitted ? game.score(day, word) : null;
        StringBuilder text = new StringBuilder("Guess ").append(row + 1).append(": ");
        if (word.isEmpty()) text.append(active ? "enter your word" : "empty");
        for (int i = 0; i < word.length(); i++) {
            if (i > 0) text.append(", ");
            text.append(Character.toUpperCase(word.charAt(i)));
            if (submitted) text.append(" ").append(stateName(scores[i]));
        }
        return text.toString();
    }

    static String stateName(int state) {
        return state == DailyWord.CORRECT ? "correct spot" : state == DailyWord.PRESENT ? "in word, different spot"
                : state == DailyWord.ABSENT ? "no remaining copy" : "not tried";
    }

    static int fill(MainActivity.Palette palette, int state) {
        boolean dark = Color.luminance(palette.background) < .5;
        if (state == DailyWord.CORRECT) return Color.parseColor(dark ? "#9ACFA9" : "#23603C");
        if (state == DailyWord.PRESENT) return Color.parseColor(dark ? "#EBD080" : "#795900");
        if (state == DailyWord.ABSENT) return Color.parseColor("#47534E");
        return palette.surface;
    }

    static int foreground(MainActivity.Palette palette, int state) {
        if (state < 0) return palette.ink;
        return state != DailyWord.ABSENT && Color.luminance(palette.background) < .5
                ? Color.parseColor("#192D28") : Color.WHITE;
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
