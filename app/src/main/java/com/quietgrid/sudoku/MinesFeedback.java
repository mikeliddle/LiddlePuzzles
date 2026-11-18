package com.quietgrid.sudoku;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.View;
import android.widget.Button;

import com.quietgrid.sudoku.puzzles.Minesweeper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Paint-only cell feedback: native layout, hit regions and saved result stay final. */
final class MinesFeedback {
    private final Map<ObjectAnimator, View> running = new LinkedHashMap<>();

    void changed(Minesweeper before, Minesweeper after, List<Button> cells) {
        changed(before, after, cells, ValueAnimator.areAnimatorsEnabled());
    }

    void changed(Minesweeper before, Minesweeper after, List<Button> cells, boolean motion) {
        stop();
        if (!motion) return;
        boolean loss = !before.lost() && after.lost();
        int order = 0;
        for (int i = 0; i < cells.size(); i++) {
            if (before.cells[i] == after.cells[i] && !(loss && after.mines[i] == 1)) continue;
            Button cell = cells.get(i);
            boolean hit = loss && after.mines[i] == 1 && after.cells[i] == 1;
            boolean pulse = loss || before.cells[i] == 2 || after.cells[i] == 2;
            ObjectAnimator animation = pulse ? ObjectAnimator.ofFloat(cell, View.ALPHA, 1f, .4f, 1f)
                    : ObjectAnimator.ofFloat(cell, View.ALPHA, .35f, 1f);
            animation.setDuration(hit ? 420 : 200);
            animation.setStartDelay(hit || after.cells[i] == 2 ? 0 : Math.min(order++, 16) * 16L);
            animation.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator value) { cell.setAlpha(1); running.remove(animation); }
            });
            running.put(animation, cell); animation.start();
        }
    }

    boolean active() { return !running.isEmpty(); }

    void stop() {
        for (ObjectAnimator animation : new ArrayList<>(running.keySet())) {
            animation.removeAllListeners(); animation.cancel(); running.get(animation).setAlpha(1);
        }
        running.clear();
    }
}
