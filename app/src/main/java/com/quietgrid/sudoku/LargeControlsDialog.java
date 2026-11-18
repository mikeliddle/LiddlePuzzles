package com.quietgrid.sudoku;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

/** Reflowing native alternatives for boards and key rows that cannot fit 48dp targets. */
final class LargeControlsDialog {
    final AlertDialog window;
    final LinearLayout content;
    private final Context context;
    private final MainActivity.Palette palette;

    LargeControlsDialog(Context context, MainActivity.Palette palette, String title) {
        this.context = context; this.palette = palette;
        content = new LinearLayout(context); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(8)); content.setBackgroundColor(palette.background);
        ScrollView scroll = new ScrollView(context); scroll.addView(content);
        LinearLayout heading = new LinearLayout(context);
        heading.setPadding(dp(16), dp(8), dp(8), 0);
        heading.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView label = new TextView(context); label.setText(title); label.setTextSize(20); label.setTextColor(palette.ink); label.setGravity(android.view.Gravity.CENTER);
        heading.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        ImageButton close = new ImageButton(context); close.setImageResource(R.drawable.ic_close);
        close.setImageTintList(android.content.res.ColorStateList.valueOf(palette.accent));
        close.setContentDescription("Close"); close.setTooltipText("Close"); close.setPadding(dp(12), dp(12), dp(12), dp(12));
        close.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf((palette.ink & 0xFFFFFF) | 0x30000000), null,
                new android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE)));
        heading.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        window = new AlertDialog.Builder(context).setCustomTitle(heading).setView(scroll).create();
        close.setOnClickListener(v -> window.dismiss());
    }

    TextView text(String value) {
        TextView view = new TextView(context); view.setText(value); view.setTextColor(palette.ink);
        view.setTextSize(20); view.setPadding(0, dp(8), 0, dp(8)); view.setFocusable(true); view.setGravity(android.view.Gravity.CENTER);
        content.addView(view, new LinearLayout.LayoutParams(-1, -2)); return view;
    }

    Button action(String value) {
        Button view = new Button(context); view.setText(value); view.setAllCaps(false); view.setTextSize(20);
        view.setMinHeight(dp(56)); view.setMinimumHeight(dp(56)); view.setMinWidth(dp(48));
        view.setPadding(dp(8), dp(8), dp(8), dp(8)); view.setTextColor(palette.accent);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(palette.surface);
        bg.setCornerRadius(dp(8)); bg.setStroke(dp(1), palette.line);
        view.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf((palette.ink & 0xFFFFFF) | 0x30000000), bg, null));
        LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(-1, -2); slot.bottomMargin = dp(8);
        content.addView(view, slot); return view;
    }

    Button action(String value, int icon) {
        Button view = action(value);
        ControlStyle.icon(view, value, icon, palette.accent);
        return view;
    }
    Spinner picker(String label) {
        TextView heading = text(label);
        Spinner view = new Spinner(context); view.setId(View.generateViewId());
        heading.setLabelFor(view.getId()); view.setMinimumHeight(dp(56));
        view.setBackgroundTintList(android.content.res.ColorStateList.valueOf(palette.accent));
        content.addView(view, new LinearLayout.LayoutParams(-1, -2)); return view;
    }

    ArrayAdapter<String> choices(Spinner picker) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, new java.util.ArrayList<>()) {
            private View readable(View view) {
                TextView text = (TextView) view; text.setSingleLine(false); text.setTextSize(20);
                text.setTextColor(palette.ink); text.setBackgroundColor(palette.surface);
                text.setMinHeight(dp(56)); text.setPadding(dp(8), dp(8), dp(8), dp(8)); return text;
            }
            @Override public View getView(int position, View recycled, ViewGroup parent) { return readable(super.getView(position, recycled, parent)); }
            @Override public View getDropDownView(int position, View recycled, ViewGroup parent) { return readable(super.getDropDownView(position, recycled, parent)); }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        picker.setAdapter(adapter); return adapter;
    }

    void announce(String message) { content.announceForAccessibility(message); }
    private int dp(float value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
