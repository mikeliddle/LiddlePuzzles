package com.quietgrid.sudoku;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.quietgrid.sudoku.puzzles.Mode;
import com.quietgrid.sudoku.puzzles.PuzzleProgress;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** The same focused Resume/new-puzzle menu used by Sudoku, without invented ratings. */
public final class PuzzleMenuActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Future<?> pending;
    private Mode mode;
    private PuzzleProgress progress;
    private boolean failed, busy;
    private int appearance;
    private MainActivity.Palette palette;

    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(MainActivity.appearanceContext(base)); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String id = getIntent().getStringExtra(PuzzleActivity.MODE);
        try { mode = Mode.valueOf(id == null ? "" : id); }
        catch (IllegalArgumentException ex) { Log.e("PuzzleMenu", "Unknown puzzle mode", ex); finish(); return; }
        appearance = getSharedPreferences("settings", 0).getInt("appearance", 0);
        if (android.os.Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override protected void onResume() {
        super.onResume();
        if (mode == null) return;
        if (appearance != getSharedPreferences("settings", 0).getInt("appearance", 0)) { recreate(); return; }
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int accent = getSharedPreferences("settings", 0).getInt("accentColor", 3);
        palette = new MainActivity.Palette(dark, accent >= 0 && accent < MainActivity.COLOR_NAMES.length ? accent : 3);
        load();
    }

    private void load() {
        busy = true; failed = false; build();
        pending = worker.submit(() -> {
            try {
                PuzzleProgress loaded = PuzzleActivity.readProgress(this, mode);
                handler.post(() -> { if (!isDestroyed()) { progress = loaded; busy = false; build(); } });
            } catch (IOException ex) {
                Log.e("PuzzleMenu", "Could not read " + mode, ex);
                handler.post(() -> { if (!isDestroyed()) { progress = null; busy = false; failed = true; build(); } });
            }
        });
    }

    private void build() {
        LinearLayout root = column(); root.setBackgroundColor(palette.background);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.view.DisplayCutout cutout = insets.getDisplayCutout();
            view.setPadding(Math.max(insets.getSystemWindowInsetLeft(), cutout == null ? 0 : cutout.getSafeInsetLeft()),
                    Math.max(insets.getSystemWindowInsetTop(), cutout == null ? 0 : cutout.getSafeInsetTop()),
                    Math.max(insets.getSystemWindowInsetRight(), cutout == null ? 0 : cutout.getSafeInsetRight()),
                    Math.max(insets.getSystemWindowInsetBottom(), cutout == null ? 0 : cutout.getSafeInsetBottom()));
            return insets;
        });
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); root.addView(scroll);
        LinearLayout content = column(); content.setPadding(dp(16), dp(12), dp(16), dp(16)); content.setGravity(Gravity.CENTER_VERTICAL);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -1));
        ImageButton back = icon("Back to Liddle Puzzles", R.drawable.ic_back); back.setOnClickListener(v -> finish());
        LinearLayout heading = new LinearLayout(this); heading.setGravity(Gravity.TOP);
        heading.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = text(mode.title, 26); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD); title.setMinHeight(dp(56));
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        ImageButton settings = icon("Settings", R.drawable.ic_settings);
        settings.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class).putExtra(MainActivity.OPEN_SETTINGS, true)
                .putExtra(MainActivity.SETTINGS_GAME, mode.name())));
        heading.addView(settings, new LinearLayout.LayoutParams(dp(48), dp(48)));
        ScreenChrome.pin(content, heading);
        if (busy) content.addView(text("Opening saved puzzle...", 16));
        if (!busy && progress != null) {
            String saved = mode.settingLabel(mode.settingOf(progress.game)) + " \u00b7 " + ActivePlayTimer.label(progress.elapsedMillis);
            Button resume = button(progress.game.solved() || progress.game.lost() ? "View" : "Resume", true);
            resume.setContentDescription(resume.getText() + " " + mode.title + ", " + saved);
            resume.setTag("puzzle-resume"); resume.setOnClickListener(v -> play(null)); add(content, resume, 16);
        }
        if (failed) {
            TextView error = text("Saved progress could not be read. It has not been replaced.", 16);
            error.setTextColor(palette.error); content.addView(error);
            Button retry = button("Retry", false); retry.setOnClickListener(v -> load()); add(content, retry, 12);
        }
        TextView choose = text("New puzzle", 22);
        choose.setPadding(0, dp(12), 0, dp(4)); content.addView(choose);
        int selected = PuzzleChoices.selected(this, mode);
        for (int setting : mode.settings()) {
            Button start = button(PuzzleChoices.label(mode, setting), false);
            PuzzleChoices.style(start, palette, setting == selected);
            start.setContentDescription("New " + mode.title + ", " + mode.settingLabel(setting));
            start.setTooltipText(start.getContentDescription());
            start.setTag("new-setting-" + setting); start.setSelected(setting == selected); start.setEnabled(!busy);
            start.setOnClickListener(v -> choose(setting)); add(content, start, 12);
        }
        setContentView(root); root.requestApplyInsets();
    }

    private void choose(int setting) {
        if (busy) return;
        if (failed || progress != null && !progress.game.solved() && !progress.game.lost()) {
            new AlertDialog.Builder(this).setTitle("Replace this puzzle?")
                    .setMessage(failed ? "The unreadable save will be replaced only after a new puzzle is generated and saved."
                            : "Your current " + mode.title + " progress and Undo history will be replaced. Other puzzles are not affected.")
                    .setNegativeButton("Keep", null).setPositiveButton("Replace", (d, w) -> play(setting)).show();
        } else play(setting);
    }

    private void play(Integer setting) {
        if (setting != null && !getSharedPreferences("settings", 0).edit().putInt(mode.settingKey(), setting).commit()) {
            Log.e("PuzzleMenu", "Could not save new puzzle preference");
            new AlertDialog.Builder(this).setMessage("Could not save your puzzle setting. Please try again. Your progress is unchanged.")
                    .setPositiveButton("OK", null).show(); return;
        }
        Intent intent = new Intent(this, PuzzleActivity.class).putExtra(PuzzleActivity.MODE, mode.name()).putExtra(PuzzleActivity.FROM_MENU, true);
        if (setting != null) intent.putExtra(PuzzleActivity.NEW_SETTING, setting);
        startActivity(intent);
    }

    private LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private TextView text(String value, int size) { TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(palette.ink); view.setGravity(Gravity.CENTER); return view; }
    private Button button(String value, boolean primary) {
        Button view = new Button(this); view.setText(value); view.setAllCaps(false); view.setTextSize(16);
        view.setGravity(Gravity.CENTER); view.setPadding(dp(16), dp(8), dp(16), dp(8)); view.setMinimumHeight(dp(56));
        view.setTextColor(primary ? palette.onPrimary : palette.accent);
        GradientDrawable fill = new GradientDrawable(); fill.setColor(primary ? palette.primary : palette.surface);
        fill.setCornerRadius(dp(12)); fill.setStroke(dp(1), palette.line);
        view.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf((palette.ink & 0xFFFFFF) | 0x30000000), fill, null));
        view.setStateListAnimator(null); return view;
    }
    private ImageButton icon(String name, int resource) {
        ImageButton view = new ImageButton(this); view.setImageResource(resource); view.setContentDescription(name); view.setTooltipText(name);
        view.setImageTintList(android.content.res.ColorStateList.valueOf(palette.accent)); view.setPadding(dp(12), dp(12), dp(12), dp(12));
        view.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf((palette.ink & 0xFFFFFF) | 0x30000000), null,
                new android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE))); return view;
    }
    private void add(LinearLayout parent, View view, int gap) {
        LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(-1, -2); slot.topMargin = dp(gap); parent.addView(view, slot);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override protected void onDestroy() {
        if (pending != null) pending.cancel(true); worker.shutdownNow(); handler.removeCallbacksAndMessages(null); super.onDestroy();
    }
}
