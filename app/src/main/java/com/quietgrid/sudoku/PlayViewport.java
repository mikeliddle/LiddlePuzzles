package com.quietgrid.sudoku;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;

/** Fits one measured puzzle/control composition without introducing a scroll surface. */
final class PlayViewport extends ViewGroup {
    private final View content;
    private int minimumWidth;

    PlayViewport(Context context, View content, int minimumWidth, int background) {
        super(context);
        this.content = content; this.minimumWidth = minimumWidth;
        setBackgroundColor(background); setTag("play-viewport");
        content.setOnApplyWindowInsetsListener(null);
        addView(content);
        setOnApplyWindowInsetsListener((view, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                android.view.DisplayCutout cutout = insets.getDisplayCutout();
                setPadding(Math.max(insets.getSystemWindowInsetLeft(), cutout == null ? 0 : cutout.getSafeInsetLeft()),
                        Math.max(insets.getSystemWindowInsetTop(), cutout == null ? 0 : cutout.getSafeInsetTop()),
                        Math.max(insets.getSystemWindowInsetRight(), cutout == null ? 0 : cutout.getSafeInsetRight()),
                        Math.max(insets.getSystemWindowInsetBottom(), cutout == null ? 0 : cutout.getSafeInsetBottom()));
            }

            return insets;
        });
    }

    void setMinimumContentWidth(int pixels) {
        minimumWidth = pixels; requestLayout();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
        int available = Math.max(1, width - getPaddingLeft() - getPaddingRight());
        content.measure(MeasureSpec.makeMeasureSpec(Math.max(available, minimumWidth), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        setMeasuredDimension(width, height);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = getWidth() - getPaddingLeft() - getPaddingRight();
        int height = getHeight() - getPaddingTop() - getPaddingBottom();
        float scale = Math.min(1f, Math.min(width / (float) Math.max(1, content.getMeasuredWidth()),
                height / (float) Math.max(1, content.getMeasuredHeight())));
        content.setPivotX(0); content.setPivotY(0);
        content.setScaleX(scale); content.setScaleY(scale);
        int x = getPaddingLeft() + Math.round((width - content.getMeasuredWidth() * scale) / 2);
        content.layout(x, getPaddingTop(), x + content.getMeasuredWidth(), getPaddingTop() + content.getMeasuredHeight());
    }
}
