package com.quietgrid.sudoku;

import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.Button;
import android.view.View;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;

final class ControlStyle {
    private ControlStyle() {}

    static RippleDrawable ripple(ColorStateList color, GradientDrawable shape) {
        return new RippleDrawable(color, shape, shape.getConstantState().newDrawable().mutate());
    }

    static void navigation(View view, ColorStateList color) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(android.graphics.Color.WHITE);
        mask.setShape(GradientDrawable.OVAL);
        view.setBackground(new RippleDrawable(color, null, mask));
        int padding = Math.round(12 * view.getResources().getDisplayMetrics().density);
        view.setPadding(padding, padding, padding, padding);
    }

    static void menuResume(android.widget.LinearLayout parent, Button button, MainActivity.Palette palette) {
        float density = button.getResources().getDisplayMetrics().density;
        button.setTextSize(16); button.setAllCaps(false); button.setGravity(Gravity.CENTER);
        button.setMinHeight(Math.round(56 * density));
        button.setPadding(Math.round(16 * density), Math.round(8 * density), Math.round(16 * density), Math.round(8 * density));
        button.setTextColor(palette.onPrimary);
        button.setStateListAnimator(null); button.setElevation(0); button.setTranslationZ(0);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(palette.primary); shape.setCornerRadius(12 * density);
        shape.setStroke(Math.round(density), palette.line);
        button.setBackground(ripple(ColorStateList.valueOf((palette.ink & 0xFFFFFF) | 0x30000000), shape));
        android.widget.LinearLayout.LayoutParams slot = new android.widget.LinearLayout.LayoutParams(-1, -2);
        slot.topMargin = Math.round(16 * density);
        parent.addView(button, slot);
    }

    static void countedIcon(Button button, int resource, String count, String widestCount, int color) {
        Drawable glyph = button.getContext().getDrawable(resource).mutate();
        glyph.setTint(color);
        float density = button.getResources().getDisplayMetrics().density;
        android.graphics.Paint ink = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        ink.setColor(color); ink.setTextSize(button.getTextSize()); ink.setTypeface(button.getTypeface());
        ink.setTextAlign(android.graphics.Paint.Align.CENTER);
        float iconSize = 24 * density, gap = 8 * density, countWidth = ink.measureText(widestCount);
        button.setText("");
        button.setForeground(new Drawable() {
            @Override public void draw(android.graphics.Canvas canvas) {
                android.graphics.Rect bounds = getBounds();
                float x = bounds.exactCenterX() - (iconSize + gap + countWidth) / 2;
                float y = bounds.exactCenterY();
                glyph.setBounds(Math.round(x), Math.round(y - iconSize / 2), Math.round(x + iconSize), Math.round(y + iconSize / 2));
                glyph.draw(canvas);
                canvas.drawText(count, x + iconSize + gap + countWidth / 2, y - (ink.ascent() + ink.descent()) / 2, ink);
            }
            @Override public void setAlpha(int alpha) { glyph.setAlpha(alpha); ink.setAlpha(alpha); }
            @Override public void setColorFilter(android.graphics.ColorFilter filter) { glyph.setColorFilter(filter); ink.setColorFilter(filter); }
            @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        });
        button.setForegroundGravity(Gravity.FILL);
    }

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
