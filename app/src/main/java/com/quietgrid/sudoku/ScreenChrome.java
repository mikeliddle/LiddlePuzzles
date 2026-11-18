package com.quietgrid.sudoku;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

/** Keeps navigation outside the fitted or scrolling puzzle content. */
final class ScreenChrome {
    static LinearLayout frame(Context context, View heading, View body, int background) {
        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(background);
        heading.setPadding(Math.round(16 * density), Math.round(4 * density), Math.round(16 * density), Math.round(4 * density));
        root.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        body.setOnApplyWindowInsetsListener(null);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            }
            android.view.DisplayCutout cutout = insets.getDisplayCutout();
            view.setPadding(Math.max(insets.getSystemWindowInsetLeft(), cutout == null ? 0 : cutout.getSafeInsetLeft()),
                    Math.max(insets.getSystemWindowInsetTop(), cutout == null ? 0 : cutout.getSafeInsetTop()),
                    Math.max(insets.getSystemWindowInsetRight(), cutout == null ? 0 : cutout.getSafeInsetRight()),
                    Math.max(insets.getSystemWindowInsetBottom(), cutout == null ? 0 : cutout.getSafeInsetBottom()));
            return insets;
        });
        return root;
    }

    static void pin(LinearLayout content, View heading) {
        android.view.ViewGroup scroll = (android.view.ViewGroup) content.getParent();
        LinearLayout root = (LinearLayout) scroll.getParent();
        if (heading.getParent() == content) content.removeView(heading);
        root.addView(heading, 0, new LinearLayout.LayoutParams(-1, -2));
        scroll.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1));
    }
}
