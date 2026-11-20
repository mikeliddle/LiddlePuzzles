package com.quietgrid.sudoku;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;

import com.quietgrid.sudoku.puzzles.*;

import java.util.ArrayList;
import java.util.List;

/** One noninteractive paint layer; the final board and all hit regions remain underneath. */
final class PuzzleFeedback extends View {
    private static final class Mark {
        final View target;
        final RectF bounds;
        final boolean wave, line, erase;
        final float delay;
        final int color;
        Mark(View target, RectF bounds, boolean wave, boolean line, boolean erase, float delay, int color) {
            this.target = target; this.bounds = bounds; this.wave = wave; this.line = line;
            this.erase = erase; this.delay = delay; this.color = color;
        }
    }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix global = new Matrix(), inverse = new Matrix();
    private final RectF box = new RectF();
    private final List<Mark> marks = new ArrayList<>();
    private final MainActivity.Palette palette;
    private ValueAnimator animator;
    private float progress;
    private long duration;

    PuzzleFeedback(FrameLayout stage, MainActivity.Palette palette) {
        super(stage.getContext()); this.palette = palette;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setFocusable(false); setClickable(false); setWillNotDraw(false);
        stage.addView(this, new FrameLayout.LayoutParams(-1, -1));
    }

    void changed(UndoablePuzzle before, UndoablePuzzle after, List<Button> cells, NumberlinkBoard paths, SlitherlinkBoard loop) {
        changed(before, after, cells, paths, loop, ValueAnimator.areAnimatorsEnabled());
    }

    void changed(UndoablePuzzle before, UndoablePuzzle after, List<Button> cells, NumberlinkBoard paths, SlitherlinkBoard loop, boolean motion) {
        stop();
        if (!motion) return;
        int[] old = before.entries(), next = after.entries();
        boolean[] satisfied = PuzzleMilestones.newlySatisfied(before, after);
        int n = after.size(), origin = 0;
        for (int i = 0; i < next.length; i++) if (old[i] != next[i]) {
            origin = i;
            if (paths != null) {
                add(paths, paths.edgeBounds(i), false, true, next[i] == 0, 0, palette.ink);
            } else if (loop != null) {
                RectF edge = loop.edgeBounds(i);
                if (next[i] != 1) {
                    float radius = Math.max(edge.width(), edge.height()) * .23f;
                    edge.set(edge.centerX() - radius, edge.centerY() - radius, edge.centerX() + radius, edge.centerY() + radius);
                }
                add(loop, edge, false, next[i] == 1, next[i] == 0, 0, palette.ink);
            } else {
                Button cell = cells.get(i);
                boolean erase = next[i] == (after instanceof Takuzu ? -1 : 0);
                add(cell, new RectF(0, 0, cell.getWidth(), cell.getHeight()), false, false, erase, 0, cell.getCurrentTextColor());
            }
        }
        for (int milestone = 0; milestone < satisfied.length; milestone++) if (satisfied[milestone]) {
            if (paths != null) {
                Numberlink path = (Numberlink) after;
                int vertex = 0, previousEdge = -1;
                while (path.endpoints[vertex] != milestone) vertex++;
                for (int order = 0; order < path.endpoints.length; order++) {
                    int following = -1;
                    for (int edge : path.grid.at[vertex]) if (path.edges[edge] == 1 && edge != previousEdge) {
                        following = edge; break;
                    }
                    if (following < 0) break;
                    add(paths, paths.edgeBounds(following), true, true, false,
                            Math.min(order * .025f, .22f), NumberlinkBoard.pairColor(palette, milestone));
                    vertex = path.grid.other(following, vertex); previousEdge = following;
                }
            } else if (loop != null) {
                add(loop, loop.clueBounds(milestone), true, false, false, 0, palette.accent);
            } else {
                for (int offset = 0; offset < n; offset++) {
                    int cell = milestone < n ? milestone * n + offset : offset * n + milestone - n;
                    int distance = Math.abs(cell / n - origin / n) + Math.abs(cell % n - origin % n);
                    View target = cells.get(cell);
                    add(target, new RectF(0, 0, target.getWidth(), target.getHeight()), true, false, false,
                            distance * .025f, palette.accent);
                }
            }
        }
        if (marks.isEmpty()) return;
        duration = marks.stream().anyMatch(mark -> mark.wave) ? 900 : 480;
        animator = ValueAnimator.ofFloat(0, 1); animator.setDuration(duration);
        animator.setInterpolator(new android.view.animation.LinearInterpolator());
        animator.addUpdateListener(value -> { progress = (float) value.getAnimatedValue(); invalidate(); });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator value) { animator = null; marks.clear(); invalidate(); }
        });
        animator.start();
    }

    private void add(View target, RectF bounds, boolean wave, boolean line, boolean erase, float delay, int color) {
        if (wave) for (int i = 0; i < marks.size(); i++) {
            Mark prior = marks.get(i);
            if (prior.wave && prior.target == target && prior.bounds.equals(bounds)) {
                if (prior.delay <= delay) return;
                marks.remove(i); break;
            }
        }
        marks.add(new Mark(target, bounds, wave, line, erase, delay, color));
    }
    boolean active() { return animator != null; }
    int milestones() { int count = 0; for (Mark mark : marks) if (mark.wave) count++; return count; }
    void stop() {
        if (animator != null) { animator.removeAllListeners(); animator.cancel(); animator = null; }
        marks.clear(); invalidate();
    }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }

    @Override protected void onDraw(Canvas canvas) {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            if (active()) stop();
            return;
        }
        global.reset();
        transformMatrixToGlobal(global); global.invert(inverse);
        for (Mark mark : marks) {
            if (!mark.target.isAttachedToWindow()) continue;
            float phase = mark.wave ? (progress - mark.delay) / .72f : progress * duration / 480f;
            if (phase < 0 || phase >= 1) continue;
            float strength = mark.wave ? Math.min(1, phase / .18f) * Math.min(1, (1 - phase) / .55f)
                    : 1 - phase;
            canvas.save(); canvas.concat(inverse);
            global.reset();
            mark.target.transformMatrixToGlobal(global); canvas.concat(global);
            box.set(mark.bounds);
            float size = Math.max(box.width(), box.height());
            paint.setColor(mark.color); paint.setAlpha(Math.round((mark.wave ? 170 : 190) * strength));
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(Math.max(1.5f * getResources().getDisplayMetrics().density, size * .028f));
            if (mark.line) {
                float fraction = mark.wave ? 1 : .35f + .65f * (1 - (1 - phase) * (1 - phase));
                float dx = box.width() * fraction / 2, dy = box.height() * fraction / 2;
                paint.setStrokeWidth(Math.max(paint.getStrokeWidth(), size * (mark.wave ? .12f : .07f)));
                canvas.drawLine(box.centerX() - dx, box.centerY() - dy, box.centerX() + dx, box.centerY() + dy, paint);
            } else {
                canvas.clipRect(box);
                float inset = Math.min(box.width(), box.height()) * (mark.wave ? .06f : mark.erase ? .06f + .24f * phase : .2f - .14f * phase);
                box.inset(inset, inset);
                canvas.drawRoundRect(box, size * .08f, size * .08f, paint);
            }
            canvas.restore();
        }
        paint.setAlpha(255);
    }
}
