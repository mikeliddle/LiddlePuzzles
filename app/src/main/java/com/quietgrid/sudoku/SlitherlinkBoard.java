package com.quietgrid.sudoku;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

import com.quietgrid.sudoku.puzzles.Slitherlink;

import java.util.function.IntConsumer;

/** Canvas edge targets; native spinner/button alternatives are supplied beside this board. */
final class SlitherlinkBoard extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final MainActivity.Palette palette;
    private final IntConsumer click, cross;
    private final int cell, n, width;
    private Slitherlink game;
    private Slitherlink.Errors errors;
    private boolean editable;
    private int pressed = -1, focusedEdge = -1, hovered = -1;
    private boolean held, canceled;
    private float downX, downY;
    private final Runnable hold = this::holdEdge;
    private void holdEdge() {
        if (!canceled && !held && pressed >= 0 && editable && isShown()
                && isAttachedToWindow() && hasWindowFocus()) {
            held = true; cross.accept(pressed);
        }
    }
    SlitherlinkBoard(Context context, MainActivity.Palette palette, IntConsumer click, IntConsumer cross, int cell, int n) {
        super(context); this.palette = palette; this.click = click; this.cross = cross; this.cell = cell;
        this.n = n; width = n + 1;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription("Slitherlink board. Cell clues and Edge controls below provide labeled, large-text alternatives.");
    }
    void show(Slitherlink game, Slitherlink.Errors errors, boolean enabled) {
        this.game = game; this.errors = errors; editable = enabled;
        if (!enabled) cancelPress();
        invalidate();
    }
    android.graphics.RectF edgeBounds(int edge) {
        int a = game.grid.a[edge], b = game.grid.b[edge];
        return new android.graphics.RectF((a % width + .5f) * cell, (a / width + .5f) * cell,
                (b % width + .5f) * cell, (b / width + .5f) * cell);
    }
    android.graphics.RectF clueBounds(int clue) {
        float x = (clue % n + 1) * cell, y = (clue / n + 1) * cell;
        return new android.graphics.RectF(x - cell * .38f, y - cell * .38f, x + cell * .38f, y + cell * .38f);
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) { setMeasuredDimension(cell * width, cell * width); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); if (game == null) return;
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int e = 0; e < game.edges.length; e++) {
            int a = game.grid.a[e], b = game.grid.b[e];
            float x = (a % width + .5f) * cell, y = (a / width + .5f) * cell;
            float xx = (b % width + .5f) * cell, yy = (b / width + .5f) * cell;
            paint.setColor(errors.edges[e] != null ? palette.error : game.edges[e] == 1 ? palette.accent : palette.line);
            paint.setStrokeWidth(game.edges[e] == 1 ? cell / 13f : Math.max(1, cell / 60f));
            canvas.drawLine(x, y, xx, yy, paint);
            if (game.edges[e] == 2) {
                paint.setColor(palette.ink); paint.setStrokeWidth(cell / 30f);
                float cx = (x + xx) / 2, cy = (y + yy) / 2, radius = cell / 12f;
                canvas.drawLine(cx - radius, cy - radius, cx + radius, cy + radius, paint);
                canvas.drawLine(cx - radius, cy + radius, cx + radius, cy - radius, paint);
            }
            if (errors.edges[e] != null) {
                float cx = (x + xx) / 2, cy = (y + yy) / 2;
                paint.setColor(palette.error);
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Math.max(1, cell / 40f));
                canvas.drawCircle(cx, cy, cell * .12f, paint);
                paint.setStyle(Paint.Style.FILL);
            }
        }
        paint.setColor(palette.ink);
        for (int vertex = 0; vertex < width * width; vertex++) canvas.drawCircle((vertex % width + .5f) * cell,
                (vertex / width + .5f) * cell, cell / 24f, paint);
        paint.setTextSize(22 * getResources().getDisplayMetrics().scaledDensity);
        paint.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < game.clues.length; i++) if (game.clues[i] >= 0) {
            float y = (i / n + 1) * cell - (paint.ascent() + paint.descent()) / 2;
            boolean error = errors.cells[i] != null;
            paint.setColor(error ? palette.error : palette.ink);
            canvas.drawText("" + game.clues[i], (i % n + 1) * cell, y, paint);
            if (error) {
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Math.max(1, cell / 40f));
                canvas.drawCircle((i % n + 1) * cell, (i / n + 1) * cell, cell * .27f, paint);
                paint.setStyle(Paint.Style.FILL);
            }
        }
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!editable || !hasWindowFocus()) return false;
        if (event.getPointerCount() > 1 || event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            cancelPress(); return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            cancelPress(); canceled = held = false;
            downX = event.getX(); downY = event.getY();
            pressed = nearest(downX, downY);
            if (pressed >= 0) postDelayed(hold, BinaryPress.timeout());
            return pressed >= 0;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            if (nearest(event.getX(), event.getY()) != pressed
                    || Math.hypot(event.getX() - downX, event.getY() - downY)
                    > ViewConfiguration.get(getContext()).getScaledTouchSlop()) cancelPress();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            int edge = nearest(event.getX(), event.getY());
            if (!canceled && !held && pressed >= 0 && edge == pressed) { click.accept(edge); performClick(); }
            cancelPress(); return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) { cancelPress(); return true; }
        return true;
    }
    void cancelPress() { removeCallbacks(hold); canceled = true; pressed = -1; }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused); if (!focused) cancelPress();
    }
    @Override protected void onDetachedFromWindow() { cancelPress(); super.onDetachedFromWindow(); }

    private void edgeEvent(int edge, int type) {
        if (!getContext().getSystemService(AccessibilityManager.class).isEnabled() || getParent() == null) return;
        AccessibilityEvent event = AccessibilityEvent.obtain(type);
        event.setSource(this, edge); event.setPackageName(getContext().getPackageName());
        event.setClassName("android.widget.Button");
        getParent().requestSendAccessibilityEvent(this, event);
    }
    @Override public boolean dispatchHoverEvent(MotionEvent event) {
        if (game == null || !getContext().getSystemService(AccessibilityManager.class).isTouchExplorationEnabled())
            return super.dispatchHoverEvent(event);
        int edge = event.getActionMasked() == MotionEvent.ACTION_HOVER_EXIT ? -1 : nearest(event.getX(), event.getY());
        if (edge != hovered) {
            if (hovered >= 0) edgeEvent(hovered, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);
            hovered = edge;
            if (edge >= 0) edgeEvent(edge, AccessibilityEvent.TYPE_VIEW_HOVER_ENTER);
        }
        return edge >= 0;
    }

    @Override public AccessibilityNodeProvider getAccessibilityNodeProvider() {
        return new AccessibilityNodeProvider() {
            @Override public AccessibilityNodeInfo createAccessibilityNodeInfo(int id) {
                if (id == HOST_VIEW_ID) {
                    AccessibilityNodeInfo node = AccessibilityNodeInfo.obtain(SlitherlinkBoard.this);
                    onInitializeAccessibilityNodeInfo(node);
                    if (game != null) for (int edge = 0; edge < game.edges.length; edge++) node.addChild(SlitherlinkBoard.this, edge);
                    return node;
                }
                if (game == null || id < 0 || id >= game.edges.length) return null;
                int a = game.grid.a[id], b = game.grid.b[id];
                AccessibilityNodeInfo node = AccessibilityNodeInfo.obtain();
                node.setSource(SlitherlinkBoard.this, id); node.setParent(SlitherlinkBoard.this);
                node.setPackageName(getContext().getPackageName()); node.setClassName("android.widget.Button");
                node.setContentDescription((b == a + 1 ? "Horizontal" : "Vertical") + " edge at dot row "
                        + (a / width + 1) + " column " + (a % width + 1)
                        + (game.edges[id] == 1 ? ", line" : game.edges[id] == 2 ? ", crossed out" : ", undecided")
                        + (errors.edges[id] == null ? "" : ". Error: " + errors.edges[id]));
                node.setEnabled(editable); node.setVisibleToUser(isShown());
                node.setFocusable(true); node.setClickable(true); node.setLongClickable(true);
                node.setAccessibilityFocused(focusedEdge == id);
                node.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, "Enter or clear line"));
                node.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, "Enter or clear cross"));
                node.addAction(focusedEdge == id ? AccessibilityNodeInfo.AccessibilityAction.ACTION_CLEAR_ACCESSIBILITY_FOCUS
                        : AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS);
                float x = ((a % width + b % width) / 2f + .5f) * cell;
                float y = ((a / width + b / width) / 2f + .5f) * cell;
                Rect bounds = new Rect((int) (x - cell * .21f), (int) (y - cell * .21f),
                        (int) (x + cell * .21f), (int) (y + cell * .21f));
                node.setBoundsInParent(bounds);
                android.graphics.Matrix transform = new android.graphics.Matrix(); transformMatrixToGlobal(transform);
                android.graphics.RectF screen = new android.graphics.RectF(bounds); transform.mapRect(screen); screen.roundOut(bounds);
                node.setBoundsInScreen(bounds); return node;
            }
            @Override public boolean performAction(int id, int action, Bundle arguments) {
                if (game == null || id < 0 || id >= game.edges.length) return false;
                if (action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS
                        || action == AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS) {
                    if (focusedEdge >= 0) edgeEvent(focusedEdge, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
                    focusedEdge = action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS ? id : -1;
                    if (focusedEdge >= 0) edgeEvent(focusedEdge, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
                    return true;
                }
                if (!editable) return false;
                if (action == AccessibilityNodeInfo.ACTION_CLICK) {
                    click.accept(id); edgeEvent(id, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED); return true;
                }
                if (action == AccessibilityNodeInfo.ACTION_LONG_CLICK) {
                    cross.accept(id); edgeEvent(id, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED); return true;
                }
                return false;
            }
        };
    }
    @Override public boolean performClick() { return super.performClick(); }
    private int nearest(float x, float y) {
        int best = -1; double distance = cell * .42;
        for (int e = 0; e < game.edges.length; e++) {
            int a = game.grid.a[e], b = game.grid.b[e];
            double dx = x - ((a % width + b % width) / 2f + .5f) * cell;
            double dy = y - ((a / width + b / width) / 2f + .5f) * cell;
            double d = Math.hypot(dx, dy);
            if (d < distance) { best = e; distance = d; }
        }
        return best;
    }
}
