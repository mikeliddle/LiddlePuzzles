package com.quietgrid.sudoku;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.AtomicFile;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DailyWordActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Button> letters = new ArrayList<>();
    private DailyWord game;
    private LocalDate date;
    private DailyWordBoard board;
    private TextView status;
    private Button enter;
    private ImageButton erase;
    private MainActivity.Palette palette;
    private boolean registered;
    Clock clock = Clock.systemUTC();
    ZoneId testZone;

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(MainActivity.appearanceContext(base));
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        if (android.os.Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        int color = getSharedPreferences("settings", MODE_PRIVATE).getInt("accentColor", 3);
        palette = new MainActivity.Palette(dark, color >= 0 && color < MainActivity.COLOR_NAMES.length ? color : 3);
        load();
    }

    private AtomicFile saved() { return new AtomicFile(new File(getFilesDir(), "daily-word-v1.bin")); }

    private void load() {
        try (InputStreamReader guesses = new InputStreamReader(getAssets().open("daily-word/guesses-v1.txt"), StandardCharsets.US_ASCII);
             InputStreamReader answers = new InputStreamReader(getAssets().open("daily-word/answers-v1.txt"), StandardCharsets.US_ASCII)) {
            DailyWord loaded = new DailyWord(guesses, answers);
            AtomicFile file = saved();
            if (file.getBaseFile().exists() || new File(file.getBaseFile() + ".bak").exists()) {
                try (DataInputStream in = new DataInputStream(file.openRead())) { loaded.read(in); }
            }
            game = loaded;
            date = today();
            build();
            refresh(null);
        } catch (IOException ex) {
            Log.e("DailyWord", "Could not open daily word data", ex);
            game = null;
            LinearLayout root = root();
            root.addView(home());
            root.addView(copy("Daily Word could not be opened", 24, palette.ink));
            root.addView(copy("The word list or saved progress could not be read. Your saved file has not been replaced.", 16, palette.error));
            Button retry = button("Retry", true);
            retry.setOnClickListener(v -> load());
            root.addView(retry, new LinearLayout.LayoutParams(-1, dp(56)));
        }
    }

    private LocalDate today() { return DailyWord.today(clock, testZone != null ? testZone : ZoneId.systemDefault()); }

    boolean checkDate() {
        if (game == null || today().equals(date)) return false;
        date = today();
        refresh(null);
        board.announceForAccessibility("New daily puzzle. Your previous guesses are saved.");
        return true;
    }

    private void input(char letter, boolean backspace, boolean submit) {
        if (game == null || checkDate()) return;
        if (game.finished(game.day(date))) return;
        DailyWord next = new DailyWord(game);
        DailyWord.Day day = next.day(date);
        String error = null;
        if (submit) error = next.submit(day);
        else if (!(backspace ? next.erase(day) : next.letter(day, letter))) return;
        if (error != null) { refresh(error); return; }
        if (persist(next)) { game = next; refresh(null); }
    }

    private boolean persist(DailyWord next) {
        AtomicFile file = saved();
        FileOutputStream stream = null;
        try {
            stream = file.startWrite();
            DataOutputStream out = new DataOutputStream(stream);
            next.write(out);
            out.flush();
            file.finishWrite(stream);
            return true;
        } catch (IOException ex) {
            file.failWrite(stream);
            Log.e("DailyWord", "Could not save daily word progress", ex);
            refresh("Could not save. Check device storage, then try that entry again. Your previous progress is unchanged.");
            return false;
        }
    }

    void build() {
        LinearLayout root = root();
        float fontScale = getResources().getConfiguration().fontScale;
        boolean enlarged = fontScale > 1.2f;
        boolean wide = !enlarged && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (enlarged) {
            ScrollView overflow = new ScrollView(this);
            overflow.setFillViewport(true);
            root.addView(overflow, new LinearLayout.LayoutParams(-1, -1));
            root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            overflow.addView(root, new ScrollView.LayoutParams(-1, -2));
        }
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        int keyHeight = dp(enlarged ? 52 * fontScale : wide ? 48 : 52);
        heading.addView(home(), new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = copy("Daily Word", 26, palette.ink);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setMinHeight(dp(52));
        title.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(heading);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        root.addView(body, enlarged ? new LinearLayout.LayoutParams(-1, -2)
                : new LinearLayout.LayoutParams(-1, 0, 1));
        board = new DailyWordBoard(this, palette);
        if (wide) body.addView(board, new LinearLayout.LayoutParams(0, -1, .9f));
        else if (enlarged) body.addView(board, new LinearLayout.LayoutParams(-1, dp(384)));
        else body.addView(board, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(4), 0, dp(4), dp(4));
        if (enlarged) body.addView(controls, new LinearLayout.LayoutParams(-1, -2));
        else {
            ScrollView scroll = new ScrollView(this);
            scroll.setFillViewport(true);
            if (wide) body.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1.1f));
            else body.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
            scroll.addView(controls, new ScrollView.LayoutParams(-1, -2));
        }
        status = copy("", 16, palette.ink);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        controls.addView(status);
        letters.clear();
        for (String line : new String[]{"qwertyuiop", "asdfghjkl", "zxcvbnm"}) {
            for (char letter : line.toCharArray()) {
                Button key = button(String.valueOf(letter).toUpperCase(Locale.ROOT), false);
                key.setTag(letter);
                key.setTextSize(16);
                key.setOnClickListener(v -> input(letter, false, false));
                letters.add(key);
            }
        }
        enter = button("Enter", true);
        enter.setOnClickListener(v -> input('\0', false, true));
        erase = iconButton("Delete", R.drawable.ic_backspace);
        erase.setPadding(dp(2), dp(8), dp(2), dp(8));
        erase.setBackground(surface(palette.surface));
        erase.setOnClickListener(v -> input('\0', true, false));
        controls.addView(new DailyWordKeyboard(this, letters, erase, enter, keyHeight),
                new LinearLayout.LayoutParams(-1, -2));
    }

    void refresh(String message) {
        DailyWord.Day day = game.day(date);
        board.show(game, day);
        boolean finished = game.finished(day);
        String result = finished && !game.won(day)
                ? "The word was " + game.answer(date).toUpperCase(Locale.ROOT) + "." : "";
        String feedback = message != null ? message : result;
        status.setText(feedback);
        status.setVisibility(feedback.isEmpty() ? View.GONE : View.VISIBLE);
        status.setTextColor(message != null ? palette.error : palette.ink);
        int[] states = game.keyboard(day);
        for (Button key : letters) {
            char letter = (Character) key.getTag();
            int state = states[letter - 'a'];
            key.setEnabled(!finished);
            key.setTextColor(DailyWordBoard.foreground(palette, state));
            key.setBackground(surface(DailyWordBoard.fill(palette, state)));
            key.setContentDescription(Character.toUpperCase(letter) + ", " + DailyWordBoard.stateName(state));
        }
        enter.setEnabled(!finished);
        erase.setEnabled(!finished && !day.draft().isEmpty());
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (game != null && !event.isCtrlPressed() && !event.isAltPressed()) {
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) input('\0', false, true);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DEL) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) input('\0', true, false);
                return true;
            }
            int unicode = Character.toLowerCase(event.getUnicodeChar());
            if (unicode >= 'a' && unicode <= 'z') {
                if (event.getAction() == KeyEvent.ACTION_DOWN) input((char) unicode, false, false);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private final Runnable rollover = new Runnable() {
        @Override public void run() {
            checkDate();
            long untilMidnight = today().plusDays(1).atStartOfDay(testZone != null ? testZone : ZoneId.systemDefault())
                    .toInstant().toEpochMilli() - clock.millis();
            handler.postDelayed(this, Math.max(1, Math.min(60000, untilMidnight)));
        }
    };

    private final BroadcastReceiver dateChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            handler.removeCallbacks(rollover);
            handler.post(rollover);
        }
    };

    @Override protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_DATE_CHANGED);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(dateChanged, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(dateChanged, filter);
        registered = true;
        handler.post(rollover);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(rollover);
        if (registered) { unregisterReceiver(dateChanged); registered = false; }
        super.onPause();
    }

    private LinearLayout root() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.background);
        boolean dark = android.graphics.Color.luminance(palette.background) < .5;
        getWindow().getDecorView().setSystemUiVisibility((android.os.Build.VERSION.SDK_INT < 30
                ? View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION : 0)
                | (dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int left, top, right, bottom;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left = bars.left; top = bars.top; right = bars.right; bottom = bars.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft(); top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight(); bottom = insets.getSystemWindowInsetBottom();
                android.view.DisplayCutout cutout = insets.getDisplayCutout();
                if (cutout != null) {
                    left = Math.max(left, cutout.getSafeInsetLeft());
                    top = Math.max(top, cutout.getSafeInsetTop());
                    right = Math.max(right, cutout.getSafeInsetRight());
                    bottom = Math.max(bottom, cutout.getSafeInsetBottom());
                }
            }
            view.setPadding(left + dp(8), top + dp(4), right + dp(8), bottom + dp(4));
            return insets;
        });
        setContentView(root);
        root.requestApplyInsets();
        return root;
    }

    private ImageButton home() {
        ImageButton home = iconButton(getString(R.string.back_to_puzzles), R.drawable.ic_back);
        home.setOnClickListener(v -> finish());
        home.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        return home;
    }

    private ImageButton iconButton(String name, int icon) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(icon);
        button.setImageTintList(android.content.res.ColorStateList.valueOf(palette.accent));
        button.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        button.setPadding(dp(6), dp(6), dp(6), dp(6));
        button.setContentDescription(name);
        button.setTooltipText(name);
        button.setMinimumHeight(dp(48));
        button.setMinimumWidth(dp(48));
        button.setStateListAnimator(null);
        button.setElevation(0);
        button.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf((palette.ink & 0x00FFFFFF) | 0x30000000), null,
                new android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE)));
        return button;
    }

    private TextView copy(String value, int size, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setPadding(dp(4), dp(4), dp(4), dp(4));
        return text;
    }

    private Button button(String value, boolean primary) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setMinWidth(0); button.setMinimumWidth(0);
        button.setMinHeight(dp(48)); button.setMinimumHeight(dp(48));
        button.setPadding(0, 0, 0, 0);
        button.setStateListAnimator(null);
        button.setElevation(0);
        button.setTextColor(primary ? palette.onPrimary : palette.accent);
        button.setBackground(surface(primary ? palette.primary : palette.surface));
        return button;
    }

    private android.graphics.drawable.Drawable surface(int fill) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(8));
        shape.setStroke(dp(1), palette.line);
        return new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf((palette.ink & 0x00FFFFFF) | 0x30000000), shape, null);
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
