package com.quietgrid.sudoku;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import java.util.List;

/** Shared half-key columns keep every letter identical across staggered rows. */
final class DailyWordKeyboard extends ViewGroup {
    private final List<Button> letters;
    private final View erase, enter;
    private final int keyHeight, gap;
    private final float maximumLetterSize;

    DailyWordKeyboard(Context context, List<Button> letters, View erase, View enter, int keyHeight) {
        super(context);
        this.letters = letters;
        this.erase = erase;
        this.enter = enter;
        this.keyHeight = keyHeight;
        maximumLetterSize = letters.get(0).getTextSize();
        gap = Math.round(4 * getResources().getDisplayMetrics().density);
        for (int i = 0; i < 19; i++) addView(letters.get(i));
        addView(erase);
        for (int i = 19; i < 26; i++) addView(letters.get(i));
        addView(enter);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int halfKey = Math.max(1, width / 22);
        int letterWidth = Math.max(1, 2 * halfKey - gap);
        android.text.TextPaint ink = new android.text.TextPaint(letters.get(0).getPaint());
        ink.setTextSize(maximumLetterSize);
        android.graphics.Paint.FontMetrics metrics = ink.getFontMetrics();
        float size = maximumLetterSize;
        for (Button letter : letters) {
            float availableWidth = Math.max(1, letterWidth - letter.getCompoundPaddingLeft() - letter.getCompoundPaddingRight());
            float availableHeight = Math.max(1, keyHeight - letter.getCompoundPaddingTop() - letter.getCompoundPaddingBottom());
            float textHeight = letter.getIncludeFontPadding() ? metrics.bottom - metrics.top : metrics.descent - metrics.ascent;
            size = Math.min(size, maximumLetterSize * Math.min(availableWidth / ink.measureText(letter.getText().toString()),
                    availableHeight / textHeight));
        }
        for (Button letter : letters) letter.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, size);
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            int span = child == enter ? 5 : 2;
            child.measure(MeasureSpec.makeMeasureSpec(Math.max(1, span * halfKey - gap), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(keyHeight, MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(width, resolveSize(3 * (keyHeight + gap), heightSpec));
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        for (int i = 0; i < 26; i++) {
            int row = i < 10 ? 0 : i < 19 ? 1 : 2;
            int column = i < 10 ? 1 + i * 2 : i < 19 ? 2 + (i - 10) * 2 : 3 + (i - 19) * 2;
            place(letters.get(i), row, column);
        }
        place(erase, 1, 20);
        place(enter, 2, 17);
    }

    private void place(View key, int row, int column) {
        int halfKey = Math.max(1, getWidth() / 22);
        int x = (getWidth() - halfKey * 22) / 2 + column * halfKey + gap / 2;
        int y = row * (keyHeight + gap);
        key.layout(x, y, x + key.getMeasuredWidth(), y + keyHeight);
    }
}
