package com.quietgrid.sudoku;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.widget.Button;

import com.quietgrid.sudoku.puzzles.Minesweeper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Paint-only cell feedback: native layout, hit regions and saved result stay final. */
final class MinesFeedback {
    private final Map<Animator, Runnable> running = new LinkedHashMap<>();

    void changed(Minesweeper before, Minesweeper after, List<Button> cells, int coveredColor) {
        changed(before, after, cells, ValueAnimator.areAnimatorsEnabled(), coveredColor);
    }

    void changed(Minesweeper before, Minesweeper after, List<Button> cells, boolean motion, int coveredColor) {
        stop();
        if (!motion) return;
        boolean loss = !before.lost() && after.lost();
        int order = 0;
        for (int i = 0; i < cells.size(); i++) {
            if (before.cells[i] == after.cells[i] && !(loss && after.mines[i] == 1)) continue;
            Button cell = cells.get(i);
            boolean hit = loss && after.mines[i] == 1 && after.cells[i] == 1;
            boolean pulse = loss || before.cells[i] == 2 || after.cells[i] == 2;
            Animator animation;
            Runnable finish;
            if (pulse) {
                float density = cell.getResources().getDisplayMetrics().density;
                GradientDrawable ring = new GradientDrawable();
                ring.setColor(android.graphics.Color.TRANSPARENT);
                ring.setCornerRadius(5 * density);
                ring.setStroke(Math.round(3 * density), cell.getCurrentTextColor());
                ring.setBounds(0, 0, cell.getWidth(), cell.getHeight());
                cell.getOverlay().add(ring);
                ValueAnimator pulseRing = ValueAnimator.ofInt(0, 220, 0);
                pulseRing.addUpdateListener(value -> ring.setAlpha((int) value.getAnimatedValue()));
                animation = pulseRing;
                finish = () -> cell.getOverlay().remove(ring);
            } else {
                GradientDrawable fill = (GradientDrawable) ((RippleDrawable) cell.getBackground()).getDrawable(0);
                int finalColor = fill.getColor().getDefaultColor();
                ValueAnimator reveal = ValueAnimator.ofArgb(coveredColor, finalColor);
                fill.setColor(coveredColor);
                reveal.addUpdateListener(value -> fill.setColor((int) value.getAnimatedValue()));
                animation = reveal;
                finish = () -> fill.setColor(finalColor);
            }
            animation.setDuration(hit ? 420 : pulse ? 320 : 200);
            animation.setStartDelay(hit || after.cells[i] == 2 ? 0 : Math.min(order++, 16) * 16L);
            animation.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator value) {
                    finish.run(); running.remove(animation);
                }
            });
            running.put(animation, finish); animation.start();
        }
    }

    boolean active() { return !running.isEmpty(); }

    void stop() {
        for (Animator animation : new ArrayList<>(running.keySet())) {
            animation.removeAllListeners(); animation.cancel(); running.get(animation).run();
        }
        running.clear();
    }
}
