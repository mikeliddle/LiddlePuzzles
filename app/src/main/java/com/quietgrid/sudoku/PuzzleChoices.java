package com.quietgrid.sudoku;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.widget.Button;

import com.quietgrid.sudoku.puzzles.Mode;

final class PuzzleChoices {
    static String label(Mode mode, int setting) {
        return mode == Mode.MINESWEEPER ? setting + " mines"
                : mode == Mode.NUMBERLINK ? "6\u00d76" : mode.settingLabel(setting).replace(" ", "");
    }

    static int selected(Context context, Mode mode) {
        int value = context.getSharedPreferences("settings", 0).getInt(mode.settingKey(), mode.defaultSetting());
        return mode.supports(value) ? value : mode.defaultSetting();
    }

    static void style(Button button, MainActivity.Palette palette, boolean selected) {
        float density = button.getResources().getDisplayMetrics().density;
        button.setAllCaps(false); button.setTextSize(14); button.setGravity(Gravity.CENTER);
        button.setPadding(Math.round(16 * density), 0, Math.round(16 * density), 0);
        button.setMinWidth(0); button.setMinimumWidth(0);
        button.setMinHeight(Math.round(56 * density)); button.setMinimumHeight(Math.round(56 * density));
        button.setTextColor(palette.accent); button.setSelected(selected); button.setStateListAnimator(null);
        GradientDrawable fill = new GradientDrawable(); fill.setColor(selected ? palette.tint : palette.surface);
        fill.setCornerRadius(12 * density); fill.setStroke(Math.round((selected ? 2 : 1) * density), palette.accent);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf((palette.ink & 0xFFFFFF) | 0x30000000), fill, null));
    }
}
