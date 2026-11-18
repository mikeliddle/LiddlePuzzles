package com.quietgrid.sudoku;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

import com.quietgrid.sudoku.puzzles.Slitherlink;

import java.util.function.IntConsumer;

/** Canvas edge targets; native spinner/button alternatives are supplied beside this board. */
final class SlitherlinkBoard extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final MainActivity.Palette palette;
    private final IntConsumer click;
    private final int cell, n, width;
    private Slitherlink game;
    private Slitherlink.Errors errors;
    private boolean editable;
    private int pressed = -1;
    SlitherlinkBoard(Context context, MainActivity.Palette palette, IntConsumer click, int cell, int n) {
        super(context); this.palette = palette; this.click = click; this.cell = cell;
        this.n = n; width = n + 1;
        setContentDescription("Slitherlink board. Cell clues and Edge controls below provide labeled, large-text alternatives.");
    }
    void show(Slitherlink game, Slitherlink.Errors errors, boolean enabled) {
        this.game = game; this.errors = errors; editable = enabled; invalidate();
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
        if (!editable) return false;
        if (event.getPointerCount() > 1) { pressed = -1; return true; }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            pressed = nearest(event.getX(), event.getY()); return pressed >= 0;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            int edge = nearest(event.getX(), event.getY());
            if (pressed >= 0 && edge == pressed) { click.accept(edge); performClick(); }
            pressed = -1; return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) { pressed = -1; return true; }
        return true;
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
