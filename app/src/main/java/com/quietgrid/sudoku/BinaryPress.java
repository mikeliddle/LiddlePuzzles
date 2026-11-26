package com.quietgrid.sudoku;

import android.view.MotionEvent;
import android.content.Context;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;

/** Gameplay holds run sooner while retaining native long-click accessibility actions. */
final class BinaryPress {
    private BinaryPress() {}
    static final String SETTING = "holdDelayPercent";
    static final int DEFAULT_PERCENT = 50;

    static int percent(Context context) {
        return Math.max(20, Math.min(100, context.getSharedPreferences("settings", 0)
                .getInt(SETTING, DEFAULT_PERCENT)));
    }

    static int timeout(Context context) {
        return timeout(ViewConfiguration.getLongPressTimeout(), percent(context));
    }

    static int timeout(int systemTimeout, int percent) {
        return (int) Math.round(systemTimeout * (Math.max(20, Math.min(100, percent)) / 100.0));
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    static void attach(Button button, String secondaryName, Runnable secondary) {
        final class Press implements View.OnTouchListener, View.OnAttachStateChangeListener,
                android.view.ViewTreeObserver.OnWindowFocusChangeListener {
            private boolean canceled = true, held;
            private float x, y;
            private final Runnable hold = () -> {
                if (!canceled && !held && button.isEnabled() && button.isPressed()
                        && button.isShown() && button.isAttachedToWindow() && button.hasWindowFocus()) {
                    button.performLongClick();
                    button.setPressed(false);
                }
            };

            private void cancel() {
                canceled = true;
                cancelHold();
                button.setPressed(false);
            }

            private void cancelHold() {
                button.removeCallbacks(hold);
            }

            private void track(float nextX, float nextY) {
                if (nextX < 0 || nextY < 0 || nextX >= button.getWidth() || nextY >= button.getHeight()) {
                    cancel();
                } else if (Math.hypot(nextX - x, nextY - y)
                        > ViewConfiguration.get(button.getContext()).getScaledTouchSlop()) {
                    cancelHold();
                }
            }

            @Override public boolean onTouch(View view, MotionEvent event) {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    button.removeCallbacks(hold); canceled = held = false;
                    x = event.getX(); y = event.getY();
                    button.drawableHotspotChanged(x, y);
                    button.setPressed(true);
                    button.postDelayed(hold, timeout(button.getContext()));
                }
                if (event.getPointerCount() > 1 || action == MotionEvent.ACTION_CANCEL) cancel();
                if (!canceled && !held && (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP)) {
                    for (int i = 0; i < event.getHistorySize() && !canceled; i++) {
                        track(event.getHistoricalX(i), event.getHistoricalY(i));
                    }
                    if (!canceled) track(event.getX(), event.getY());
                }
                if (action == MotionEvent.ACTION_UP) {
                    button.removeCallbacks(hold);
                    boolean click = !canceled && !held && button.isShown() && button.hasWindowFocus();
                    cancel();
                    if (click) button.post(button::performClick);
                }
                return true;
            }

            @Override public void onWindowFocusChanged(boolean focused) { if (!focused) cancel(); }
            @Override public void onViewDetachedFromWindow(View view) {
                cancel();
                if (view.getViewTreeObserver().isAlive()) view.getViewTreeObserver().removeOnWindowFocusChangeListener(this);
            }
            @Override public void onViewAttachedToWindow(View view) {
                view.getViewTreeObserver().addOnWindowFocusChangeListener(this);
            }
        }
        Press press = new Press();
        secondaryAction(button, secondaryName, () -> {
            button.removeCallbacks(press.hold); press.held = true;
            secondary.run();
        });
        button.addOnAttachStateChangeListener(press);
        button.setOnTouchListener(press);
    }

    static void secondaryAction(Button button, String secondaryName, Runnable secondary) {
        button.setHapticFeedbackEnabled(false);
        button.setOnLongClickListener(view -> { secondary.run(); return true; });
        button.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo node) {
                super.onInitializeAccessibilityNodeInfo(host, node);
                node.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                        AccessibilityNodeInfo.ACTION_LONG_CLICK, secondaryName));
            }
        });
    }
}
