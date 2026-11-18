package com.quietgrid.sudoku;

import android.animation.ValueAnimator;
import android.view.View;
import android.widget.FrameLayout;

/** The Sudoku cascade, without Sudoku digits, over an already-visible result. */
final class CompletionFeedback {
    private final FrameLayout stage;
    private final MainActivity.Palette palette;
    private CelebrationView effect;

    CompletionFeedback(FrameLayout stage, MainActivity.Palette palette) {
        this.stage = stage; this.palette = palette;
    }

    void celebrate() { celebrate(ValueAnimator.areAnimatorsEnabled()); }

    void celebrate(boolean motionEnabled) {
        stop();
        if (!motionEnabled) return;
        effect = new CelebrationView(stage.getContext(), palette, this::stop, new String[0]);
        effect.setTag("completion-effect");
        stage.addView(effect, new FrameLayout.LayoutParams(-1, -1));
        effect.start();
    }

    void stop() {
        if (effect == null) return;
        View previous = effect;
        effect.stop(); effect = null;
        stage.removeView(previous);
    }
}
