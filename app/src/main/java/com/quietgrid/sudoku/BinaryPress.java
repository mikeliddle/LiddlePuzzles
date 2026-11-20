package com.quietgrid.sudoku;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;

/** Gameplay holds run sooner while retaining native long-click accessibility actions. */
final class BinaryPress {
    private BinaryPress() {}

    static int timeout() { return timeout(ViewConfiguration.getLongPressTimeout()); }

    static int timeout(int systemTimeout) {
        return systemTimeout > 500 ? systemTimeout : Math.round(systemTimeout * .8f);
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    static void attach(Button button, String secondaryName, Runnable secondary) {
        final class Press implements View.OnTouchListener, View.OnAttachStateChangeListener,
                android.view.ViewTreeObserver.OnWindowFocusChangeListener {
            private boolean canceled, held;
            private float x, y;
            private final Runnable hold = () -> {
                if (!canceled && !held && button.isEnabled() && button.isPressed()
                        && button.isShown() && button.isAttachedToWindow() && button.hasWindowFocus()) {
                    button.cancelLongPress();
                    button.performLongClick();
                    button.setPressed(false);
                }
            };

            private void cancel() {
                canceled = true; button.removeCallbacks(hold);
                button.cancelLongPress(); button.setPressed(false);
            }

            @Override public boolean onTouch(View view, MotionEvent event) {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    button.removeCallbacks(hold); canceled = held = false;
                    x = event.getX(); y = event.getY();
                    button.postDelayed(hold, timeout());
                }
                if (event.getPointerCount() > 1 || action == MotionEvent.ACTION_CANCEL
                        || action == MotionEvent.ACTION_MOVE && Math.hypot(event.getX() - x, event.getY() - y)
                        > ViewConfiguration.get(button.getContext()).getScaledTouchSlop()) cancel();
                if (action == MotionEvent.ACTION_UP) {
                    button.removeCallbacks(hold);
                    if (canceled || held) { button.cancelLongPress(); button.setPressed(false); return true; }
                }
                return canceled || held;
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
        button.setHapticFeedbackEnabled(false);
        button.setOnLongClickListener(view -> {
            button.removeCallbacks(press.hold); press.held = true;
            secondary.run(); return true;
        });
        button.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo node) {
                super.onInitializeAccessibilityNodeInfo(host, node);
                node.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                        AccessibilityNodeInfo.ACTION_LONG_CLICK, secondaryName));
            }
        });
        button.addOnAttachStateChangeListener(press);
        button.setOnTouchListener(press);
    }
}
