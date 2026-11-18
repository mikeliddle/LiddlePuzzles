package com.quietgrid.sudoku;

import android.app.Activity;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;

import junit.framework.Assert;

final class ViewportChecks {
    static void navigation(Activity activity, View back, View settings) {
        android.view.WindowInsets insets = activity.getWindow().getDecorView().getRootWindowInsets();
        float density = activity.getResources().getDisplayMetrics().density;
        RectF frame = bounds(activity.getWindow().getDecorView());
        for (View control : new View[]{back, settings}) if (control != null) {
            RectF actual = bounds(control);
            Assert.assertTrue("Navigation stays at the top, outside centered/fitted content",
                    actual.top <= frame.top + insets.getSystemWindowInsetTop() + 16 * density);
            Assert.assertTrue("Navigation retains native 48dp target", actual.width() >= 47 * density && actual.height() >= 47 * density);
        }
        if (back != null) Assert.assertTrue("Back is in the left corner",
                bounds(back).left <= frame.left + insets.getSystemWindowInsetLeft() + 32 * density);
        if (settings != null) Assert.assertTrue("Settings is in the right corner",
                bounds(settings).right >= frame.right - insets.getSystemWindowInsetRight() - 32 * density);
    }

    static RectF bounds(View view) {
        Matrix matrix = new Matrix(); view.transformMatrixToGlobal(matrix);
        RectF bounds = new RectF(0, 0, view.getWidth(), view.getHeight());
        matrix.mapRect(bounds); return bounds;
    }

    static void all(Activity activity) {
        controls(activity.findViewById(android.R.id.content));
        ViewportChecks.walk(activity.findViewById(android.R.id.content), null);
        navigation(activity, navigationControl(activity.findViewById(android.R.id.content), false),
                navigationControl(activity.findViewById(android.R.id.content), true));
    }

    private static View navigationControl(View view, boolean settings) {
        String name = String.valueOf(view.getContentDescription());
        if (view instanceof android.widget.ImageButton && (settings ? name.equals("Settings") : name.startsWith("Back to ") || name.equals("Menu")))
            return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View found = navigationControl(((ViewGroup) view).getChildAt(i), settings);
            if (found != null) return found;
        }
        return null;
    }

    static void controls(View view) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof android.widget.Button) {
            android.widget.Button button = (android.widget.Button) view;
            java.util.regex.Matcher words = java.util.regex.Pattern.compile("\\p{L}+").matcher(button.getText());
            int count = 0; while (words.find()) count++;
            Assert.assertTrue("At most one visible word per action (hub game titles excepted): " + button.getText(),
                    count <= 1 || "hub-puzzle".equals(button.getTag())
                            || String.valueOf(button.getTag()).startsWith("game-settings-"));
            if (button.getText().length() == 0 && button.getForeground() != null) {
                android.graphics.Rect icon = button.getForeground().getBounds();
                Assert.assertEquals("Icon-only action is horizontally centered", button.getWidth() / 2f, icon.exactCenterX(), 1f);
                Assert.assertEquals("Icon-only action is vertically centered", button.getHeight() / 2f, icon.exactCenterY(), 1f);
            }
        }
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            controls(((ViewGroup) view).getChildAt(i));
    }

    private static void walk(View view, RectF viewport) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof PlayViewport) {
            viewport = bounds(view);
            viewport.left += view.getPaddingLeft(); viewport.right -= view.getPaddingRight();
            viewport.top += view.getPaddingTop(); viewport.bottom -= view.getPaddingBottom();
        }
        if (viewport != null) {
            Assert.assertFalse("No scrolling is needed during normal play", view instanceof ScrollView || view instanceof HorizontalScrollView);
            if ("puzzle-title".equals(view.getTag())) {
                android.widget.TextView title = (android.widget.TextView) view;
                Assert.assertTrue("Full puzzle heading fits without clipping: " + title.getText(),
                        title.getPaint().measureText(title.getText().toString())
                                <= title.getWidth() - title.getCompoundPaddingLeft() - title.getCompoundPaddingRight() + 1);
            }
            if (view.isClickable() || "puzzle-board".equals(view.getTag()) || view instanceof BoardView || view instanceof DailyWordBoard) {
                RectF actual = bounds(view);
                Assert.assertTrue("Full control/board is inside viewport: " + view.getTag() + " " + view.getContentDescription()
                        + " " + actual + " vs " + viewport, actual.left >= viewport.left - 2 && actual.top >= viewport.top - 2
                        && actual.right <= viewport.right + 2 && actual.bottom <= viewport.bottom + 2);
                Assert.assertTrue(actual.width() > 0 && actual.height() > 0);
            }
        }
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            walk(((ViewGroup) view).getChildAt(i), viewport);
    }
}
