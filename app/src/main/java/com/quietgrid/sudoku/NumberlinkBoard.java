package com.quietgrid.sudoku;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.Button;

import com.quietgrid.sudoku.puzzles.Numberlink;

import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Consumer;

/** Native square buttons expose every path edit to touch, keyboard and accessibility. */
final class NumberlinkBoard extends ViewGroup {
    private final MainActivity.Palette palette;
    private final int cell, n;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Numberlink game;
    private int selected = -1;
    private final Consumer<Numberlink> commit;
    private Numberlink.Drag drag;
    private float downX, downY, lastX, lastY;
    private int pointer = -1;
    private boolean dragging, editable, canceled;
    NumberlinkBoard(Context context, MainActivity.Palette palette, Function<String, Button> buttons, IntConsumer click,
                    Consumer<Numberlink> commit, int cell, int n) {
        super(context); this.palette = palette; this.cell = cell; this.n = n; setWillNotDraw(false);
        this.commit = commit;
        for (int i = 0; i < n * n; i++) {
            int index = i; Button button = buttons.apply("");
            button.setOnClickListener(v -> click.accept(index)); addView(button);
        }
    }
    void show(Numberlink game, int selected, boolean enabled) {
        if (this.game != game) cancelDrag();
        this.game = game; this.selected = selected; editable = enabled;
        for (int i = 0; i < n * n; i++) {
            Button button = (Button) getChildAt(i);
            int endpoint = game.endpoints[i];
            button.setText(endpoint == 0 ? "\u00b7" : "" + endpoint);
            button.setTextSize(22);
            button.setTextColor(pairColor(palette, game.pairAt(i)));
            button.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            button.setEnabled(enabled);
            StringBuilder description = new StringBuilder("Row " + (i / n + 1) + " column " + (i % n + 1)
                    + (endpoint == 0 ? game.pairAt(i) == 0 ? ", path square" : ", pair " + game.pairAt(i) + " path square"
                    : ", pair " + endpoint + " endpoint") + (selected == i ? ", selected" : ""));
            for (int edge : game.grid.at[i]) if (game.edges[edge] == 1) {
                int other = game.grid.other(edge, i);
                description.append(", connects to row ").append(other / n + 1).append(" column ").append(other % n + 1);
            }
            button.setContentDescription(description.toString());
        }
        invalidate();
    }

    static int pairColor(MainActivity.Palette palette, int pair) {
        boolean dark = android.graphics.Color.luminance(palette.background) < .5;
        int[] colors = dark ? new int[]{palette.ink, 0xFF91C9FF, 0xFFFFC077, 0xFFE9ACED, 0xFF6ED6C1, 0xFFF2DF78}
                : new int[]{palette.ink, 0xFF145A96, 0xFF855000, 0xFF843C8D, 0xFF006953, 0xFF685A00};
        return colors[pair >= 1 && pair <= Numberlink.MAX_PAIRS ? pair : 0];
    }

    android.graphics.RectF edgeBounds(int edge) {
        int a = game.grid.a[edge], b = game.grid.b[edge];
        return new android.graphics.RectF((a % n + .5f) * cell, (a / n + .5f) * cell,
                (b % n + .5f) * cell, (b / n + .5f) * cell);
    }

    private int hit(float x, float y) {
        if (x < 0 || y < 0 || x >= getWidth() || y >= getHeight()) return -1;
        return (int) (y / cell) * n + (int) (x / cell);
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            cancelDrag(); canceled = false;
            downX = lastX = event.getX(); downY = lastY = event.getY();
            pointer = event.getPointerId(0);
            if (editable && game != null) drag = Numberlink.Drag.begin(game, hit(downX, downY));
            if (drag != null) getParent().requestDisallowInterceptTouchEvent(true);
        } else if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            cancelDrag(); canceled = true; return true;
        }
        else if (event.getActionMasked() == MotionEvent.ACTION_MOVE && drag != null) {
            float slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
            if (Math.hypot(event.getX() - downX, event.getY() - downY) > slop) {
                dragging = true;
                for (int h = 0; h < event.getHistorySize(); h++) advance(event.getHistoricalX(h), event.getHistoricalY(h));
                advance(event.getX(), event.getY());
                getParent().requestDisallowInterceptTouchEvent(true); return true;
            }
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) cancelDrag();
        return false;
    }

    // Native square buttons own click/key actions; this parent handles continuous drags only.
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (canceled) return true;
        if (!dragging || drag == null) return super.onTouchEvent(event);
        if (event.getPointerCount() != 1 || event.getPointerId(0) != pointer
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            cancelDrag(); return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE || event.getActionMasked() == MotionEvent.ACTION_UP) {
            for (int h = 0; h < event.getHistorySize(); h++) advance(event.getHistoricalX(h), event.getHistoricalY(h));
            advance(event.getX(), event.getY());
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                Numberlink finished = drag.changed() ? drag.preview : null;
                cancelDrag();
                if (finished != null) commit.accept(finished);
            }
            return true;
        }
        return true;
    }

    private void advance(float x, float y) {
        int samples = Math.max(1, (int) Math.ceil(Math.hypot(x - lastX, y - lastY) / (cell / 8f)));
        for (int i = 1; i <= samples; i++) {
            int next = hit(lastX + (x - lastX) * i / samples, lastY + (y - lastY) * i / samples);
            if (next != drag.head() && !drag.moveTo(next)) break;
        }
        lastX = x; lastY = y; invalidate();
    }

    void cancelDrag() {
        drag = null; dragging = false; pointer = -1;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        invalidate();
    }

    @Override protected void onDetachedFromWindow() { cancelDrag(); super.onDetachedFromWindow(); }

    @Override protected void onMeasure(int width, int height) {
        for (int i = 0; i < n * n; i++) getChildAt(i).measure(MeasureSpec.makeMeasureSpec(cell, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(cell, MeasureSpec.EXACTLY));
        setMeasuredDimension(cell * n, cell * n);
    }
    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < n * n; i++) {
            View child = getChildAt(i); int x = i % n * cell, y = i / n * cell;
            child.layout(x, y, x + child.getMeasuredWidth(), y + child.getMeasuredHeight());
        }
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (game == null) return;
        Numberlink shown = dragging && drag != null ? drag.preview : game;
        paint.setStrokeWidth(cell / 9f); paint.setStrokeCap(Paint.Cap.ROUND);
        for (int edge = 0; edge < shown.edges.length; edge++) if (shown.edges[edge] == 1) {
            int a = shown.grid.a[edge], b = shown.grid.b[edge];
            paint.setColor(pairColor(palette, shown.pairAt(a)));
            canvas.drawLine((a % n + .5f) * cell, (a / n + .5f) * cell,
                    (b % n + .5f) * cell, (b / n + .5f) * cell, paint);
        }
        for (int i = 0; i < n * n; i++) {
            float x = (i % n + .5f) * cell, y = (i / n + .5f) * cell;
            if (game.endpoints[i] > 0) {
                paint.setStyle(Paint.Style.FILL); paint.setColor(palette.surface);
                canvas.drawCircle(x, y, cell * .31f, paint);
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Math.max(1, cell / 40f)); paint.setColor(pairColor(palette, game.endpoints[i]));
                canvas.drawCircle(x, y, cell * .31f, paint);
            }
            if (selected == i) {
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(cell / 25f); paint.setColor(palette.accent);
                canvas.drawRoundRect(x - cell * .46f, y - cell * .46f, x + cell * .46f, y + cell * .46f,
                        cell / 8f, cell / 8f, paint);
            }
        }
        paint.setStyle(Paint.Style.FILL);
    }
}
