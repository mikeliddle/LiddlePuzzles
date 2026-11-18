package com.quietgrid.sudoku;

import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.Button;

final class ControlStyle {
    private ControlStyle() {}

    static void state(Button button, boolean selected) {
        button.setSelected(selected);
        if (android.os.Build.VERSION.SDK_INT >= 30) button.setStateDescription(selected ? "On" : "Off");
        else button.setContentDescription(button.getContentDescription() + (selected ? ", On" : ", Off"));
    }

    static void icon(Button button, String name, int resource, int ink) {
        Drawable icon = button.getContext().getDrawable(resource).mutate();
        int size = Math.round(24 * button.getResources().getDisplayMetrics().density);
        icon.setTint(ink); icon.setBounds(0, 0, size, size);
        button.setText("");
        button.setGravity(Gravity.CENTER);
        button.setCompoundDrawablesRelative(null, null, null, null);
        button.setCompoundDrawablePadding(0);
        button.setForeground(icon); button.setForegroundGravity(Gravity.CENTER);
        button.setContentDescription(name); button.setTooltipText(name);
    }
}
