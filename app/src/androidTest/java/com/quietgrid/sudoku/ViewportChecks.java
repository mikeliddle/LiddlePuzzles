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
    static void awaitEdits(android.app.Instrumentation instrumentation, Activity activity) {
        try {
            java.lang.reflect.Field field = activity.getClass().getDeclaredField("saveSession");
            field.setAccessible(true);
            boolean[] idle = {false};
            long deadline = android.os.SystemClock.uptimeMillis() + 5000;
            do {
                instrumentation.runOnMainSync(() -> {
                    try {
                        idle[0] = activity.isFinishing() || activity.isDestroyed()
                                || ((SaveSession<?>) field.get(activity)).idle();
                    } catch (IllegalAccessException ex) { throw new AssertionError(ex); }
                });
                if (!idle[0]) android.os.SystemClock.sleep(10);
            } while (!idle[0] && android.os.SystemClock.uptimeMillis() < deadline);
            Assert.assertTrue("Input waits for the durable edit acknowledgement", idle[0]);
        } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }

    static void polishedGameplay(Activity activity) {
        View root = activity.findViewById(android.R.id.content);
        View title = root.findViewWithTag("puzzle-title");
        View viewport = root.findViewWithTag("play-viewport");
        Assert.assertNotNull(title); Assert.assertNotNull(viewport);
        Assert.assertEquals("Gameplay title is on the viewport axis",
                bounds(viewport).centerX(), bounds(title).centerX(), 2f);
        float density = title.getResources().getDisplayMetrics().density;
        Assert.assertTrue("Title header follows one text line, not fontScale times a 56dp slot",
                title.getHeight() <= Math.max(48 * density,
                        32 * density * title.getResources().getConfiguration().fontScale)
                        + title.getPaddingTop() + title.getPaddingBottom() + 2);
        all(activity);
    }

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

    static void textFits(android.widget.TextView view) {
        android.text.Layout layout = view.getLayout();
        Assert.assertNotNull(layout);
        Assert.assertTrue("All text lines fit the content height: " + view.getText(),
                layout.getHeight() <= view.getHeight() - view.getCompoundPaddingTop() - view.getCompoundPaddingBottom());
        for (int line = 0; line < layout.getLineCount(); line++) {
            Assert.assertEquals("Text is not ellipsized", 0, layout.getEllipsisCount(line));
            Assert.assertTrue("Text fits its padded content width: " + view.getText(),
                    layout.getLineWidth(line) <= view.getWidth() - view.getCompoundPaddingLeft() - view.getCompoundPaddingRight() + 1);
        }
    }

    static String resumeStyle(android.widget.Button button) {
        android.widget.LinearLayout.LayoutParams slot = (android.widget.LinearLayout.LayoutParams) button.getLayoutParams();
        android.graphics.drawable.RippleDrawable ripple = (android.graphics.drawable.RippleDrawable) button.getBackground();
        android.graphics.drawable.GradientDrawable shape = (android.graphics.drawable.GradientDrawable) ripple.getDrawable(0);
        Assert.assertNotNull("Resume has a matching rounded ripple mask", ripple.findDrawableByLayerId(android.R.id.mask));
        return button.getTop() + ":" + button.getHeight() + ":" + button.getTextSize()
                + ":" + button.getPaddingLeft() + ":" + button.getPaddingTop() + ":" + button.getPaddingRight() + ":" + button.getPaddingBottom()
                + ":" + slot.topMargin + ":" + slot.bottomMargin + ":" + shape.getCornerRadius() + ":" + shape.getColor().getDefaultColor();
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
