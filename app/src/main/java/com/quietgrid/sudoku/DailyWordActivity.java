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
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
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
    private LargeControlsDialog largeControls;
    private android.widget.Spinner letterPicker;
    private android.widget.ArrayAdapter<String> letterChoices;
    private Button largeAdd, largeDelete, largeEnter;
    private TextView largeFeedback;
    private final TextView[] largeGuesses = new TextView[6];
    Clock clock = Clock.systemUTC();
    ZoneId testZone;
    final ActivePlayTimer playTime = new ActivePlayTimer(android.os.SystemClock::elapsedRealtime);
    private TextView elapsed;
    private CompletionFeedback completion;
    private boolean resumed, focused;
    private SaveQueue saves;
    private SaveSession<DailyWordActivity> saveSession;
    private PuzzleSave puzzleSave;
    private final java.util.concurrent.ExecutorService loader = java.util.concurrent.Executors.newSingleThreadExecutor();
    private boolean rolloverPending;
    private static final class Retained {
        final SaveSession<DailyWordActivity> session;
        Retained(SaveSession<DailyWordActivity> session) { this.session = session; }
    }
    private volatile LocalDate durableDate;
    private volatile long durableElapsed;
    private volatile long savedRevision;
    java.util.function.Function<File, DurableFile> saveFileFactory = AndroidSaveFile::at;

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
        puzzleSave = PuzzleSave.forFile(saved());
        saves = SaveQueue.forPath(saved().getAbsolutePath());
        saveSession = getLastNonConfigurationInstance() instanceof Retained retained ? retained.session
                : new SaveSession<>(saves, new Handler(Looper.getMainLooper())::post,
                        ex -> Log.e("DailyWord", "Game commit failed", ex));
        saveSession.attach(this);
        load();
    }

    private File saved() { return new File(getFilesDir(), "daily-word-v1.bin"); }

    private void load() {
        LinearLayout opening = root(); opening.addView(home()); opening.addView(copy("Opening Daily Word...", 24, palette.ink));
        loader.execute(this::read);
    }
    private void read() {
        try (InputStreamReader guesses = new InputStreamReader(getAssets().open("daily-word/guesses-v1.txt"), StandardCharsets.US_ASCII);
             InputStreamReader answers = new InputStreamReader(getAssets().open("daily-word/answers-v1.txt"), StandardCharsets.US_ASCII)) {
            DailyWord loaded = new DailyWord(guesses, answers);
            byte[] bytes = AndroidSaveFile.read(saved());
            if (bytes != null) {
                try (DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(bytes))) { loaded.read(in); }
            }
            handler.post(() -> {
                if (isDestroyed()) return;
                game = loaded; date = today();
                playTime.restore(game.day(date).elapsedMillis());
                durableDate = bytes == null ? null : date; durableElapsed = game.day(date).elapsedMillis();
                build(); refresh(null); saveSession.ready();
            });
        } catch (IOException ex) {
            Log.e("DailyWord", "Could not open daily word data", ex);
            handler.post(() -> {
            if (isDestroyed()) return;
            game = null;
            LinearLayout root = root();
            root.addView(home());
            root.addView(copy("Daily Word could not be opened", 24, palette.ink));
            root.addView(copy("The word list or saved progress could not be read. Your saved file has not been replaced.", 16, palette.error));
            Button retry = button("Retry", true);
            retry.setOnClickListener(v -> load());
            root.addView(retry, new LinearLayout.LayoutParams(-1, dp(56)));
            saveSession.ready();
            });
        }
    }

    private LocalDate today() { return DailyWord.today(clock, testZone != null ? testZone : ZoneId.systemDefault()); }

    boolean checkDate() {
        if (game == null || today().equals(date)) return false;
        if (rolloverPending) return true;
        rolloverPending = true;
        saveSession.dispatch(DailyWordActivity::rolloverNow);
        return true;
    }
    private void rolloverNow() {
        if (board != null) board.stopFeedback();
        playTime.setRunning(false);
        persistTimed(owner -> {
            if (owner.completion != null) owner.completion.stop();
            owner.date = owner.today(); owner.rolloverPending = false;
            owner.playTime.restore(owner.game.day(owner.date).elapsedMillis());
            owner.refresh(null);
            if (owner.resumed) owner.announceFeedback("New daily puzzle. Your previous guesses are saved.");
        });
    }

    private void input(char letter, boolean backspace, boolean submit) {
        if (saveSession.defer(owner -> owner.input(letter, backspace, submit))) return;
        if (game == null || checkDate()) return;
        if (game.finished(game.day(date))) return;
        board.stopFeedback();
        playTime.setRunning(false);
        DailyWord.Day previous = game.day(date);
        LocalDate enteredDate = date;
        DailyWordActivity origin = this;
        DailyWord next = new DailyWord(game);
        DailyWord.Day day = next.day(date);
        day.setElapsed(playTime.elapsedMillis());
        String error = null;
        if (submit) error = next.submit(day);
        else if (!(backspace ? next.erase(day) : next.letter(day, letter))) { syncTime(); return; }
        if (error != null) { refresh(error); announceFeedback(error); return; }
        persist(next, next.finished(day) ? PuzzleSave.Edit.DAILY_FINISH : PuzzleSave.Edit.PLAY, owner -> {
            if (owner.date.equals(enteredDate) && next.finished(next.day(enteredDate)))
                owner.playTime.restore(next.day(enteredDate).elapsedMillis());
            owner.game = next; owner.refresh(null);
            if (!owner.date.equals(enteredDate) || !owner.today().equals(enteredDate)) { owner.checkDate(); return; }
            if (owner == origin && owner.resumed && owner.focused) owner.board.changed(previous, owner.game.day(owner.date), submit);
            if (submit) {
                DailyWord.Day accepted = owner.game.day(owner.date);
                String spoken = DailyWordBoard.rowDescription(owner.game, accepted, accepted.guesses().size() - 1) + ".";
                if (owner.game.won(accepted)) spoken += " Puzzle complete.";
                else if (owner.game.finished(accepted)) spoken += " No guesses remaining. The word was " + owner.game.answer(owner.date).toUpperCase(Locale.ROOT) + ".";
                if (owner == origin && owner.resumed) owner.announceFeedback(spoken);
                if (owner == origin && owner.game.won(accepted) && owner.resumed && owner.focused) owner.completion.celebrate();
            }
        });
    }

    private void persist(DailyWord next, java.util.function.Consumer<DailyWordActivity> success) {
        persist(next, PuzzleSave.Edit.CHECKPOINT, success);
    }
    private void persist(DailyWord next, PuzzleSave.Edit edit, java.util.function.Consumer<DailyWordActivity> success) {
        LocalDate committedDate = date;
        saveSession.commit(() -> write(next, edit, committedDate), owner -> {
            owner.durableDate = committedDate; owner.durableElapsed = next.day(committedDate).elapsedMillis();
            success.accept(owner);
        }, (owner, ex) -> { owner.rolloverPending = false; owner.saveError(ex); });
    }
    private void write(DailyWord next, PuzzleSave.Edit edit, LocalDate today) throws IOException {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        next.write(new DataOutputStream(bytes));
        puzzleSave.commit(saveFileFactory.apply(saved()), bytes.toByteArray(), edit, ResponsibleMode.enabled(this), today);
        savedRevision++;
    }
    private void saveError(IOException ex) {
        Log.e("DailyWord", "Could not save daily word progress", ex);
        String error = "Could not save. Pending changes were not applied. Check device storage, then try that entry again. Your previous progress is unchanged.";
        refresh(error); announceFeedback(error);
    }
    private void persistTimed(java.util.function.Consumer<DailyWordActivity> success) {
        DailyWord next = new DailyWord(game);
        next.day(date).setElapsed(playTime.elapsedMillis());
        persist(next, owner -> { owner.game = next; success.accept(owner); });
    }

    void build() {
        if (completion != null) completion.stop();
        LinearLayout root = root();
        float fontScale = getResources().getConfiguration().fontScale;
        boolean enlarged = fontScale > 1.2f;
        boolean wide = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.TOP);
        int keyHeight = dp(enlarged ? 52 * fontScale : wide ? 48 : 52);
        heading.addView(home(), new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = copy("Daily Word", 26, palette.ink);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true); title.setHorizontallyScrolling(false); title.setTag("puzzle-title");
        title.setAutoSizeTextTypeUniformWithConfiguration(10, 26, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        heading.addView(title, new LinearLayout.LayoutParams(0, ScreenChrome.titleHeight(title), 1));
        ImageButton large = iconButton("Large controls", R.drawable.ic_large_controls);
        large.setOnClickListener(v -> openLargeControls());
        heading.addView(large, new LinearLayout.LayoutParams(dp(48), dp(48)));
        ImageButton settings = iconButton("Settings", R.drawable.ic_settings);
        settings.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)
                .putExtra(MainActivity.OPEN_SETTINGS, true).putExtra(MainActivity.SETTINGS_GAME, "DAILY_WORD")));
        heading.addView(settings, new LinearLayout.LayoutParams(dp(48), dp(48)));
        ScreenChrome.centerTitle(heading, title);
        elapsed = copy("0:00", 18, palette.ink); elapsed.setTag("elapsed-time");
        elapsed.setGravity(Gravity.CENTER);
        elapsed.setSingleLine(true); elapsed.setHorizontallyScrolling(false);
        elapsed.setAutoSizeTextTypeUniformWithConfiguration(10, 18, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        elapsed.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_NONE);
        FrameLayout clockLine = new FrameLayout(this);
        clockLine.addView(elapsed, new FrameLayout.LayoutParams(-1, -1));
        root.addView(clockLine, new LinearLayout.LayoutParams(-1, dp(30 * Math.max(1, fontScale))));
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        body.setGravity(Gravity.TOP);
        root.addView(body, new LinearLayout.LayoutParams(-1, -2));
        board = new DailyWordBoard(this, palette);
        board.setTag("word-board");
        if (wide) body.addView(board, new LinearLayout.LayoutParams(dp(300), -2));
        else body.addView(board, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL); controls.setTag("word-controls");
        controls.setPadding(dp(4), 0, dp(4), dp(4));
        LinearLayout.LayoutParams controlsLayout = new LinearLayout.LayoutParams(wide ? 0 : -1, -2, wide ? 1.1f : 0);
        if (wide) controlsLayout.setMarginStart(dp(12)); else controlsLayout.topMargin = dp(12);
        body.addView(controls, controlsLayout);
        status = copy("", 16, palette.ink);
        status.setTag("word-feedback");
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_NONE);
        status.setGravity(Gravity.CENTER);
        status.setAutoSizeTextTypeUniformWithConfiguration(10, 16, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        clockLine.addView(status, new FrameLayout.LayoutParams(-1, -1));
        letters.clear();
        for (String line : new String[]{"qwertyuiop", "asdfghjkl", "zxcvbnm"}) {
            for (char letter : line.toCharArray()) {
                Button key = button(String.valueOf(letter).toUpperCase(Locale.ROOT), false);
                key.setTag(letter);
                key.setTextSize(16);
                key.setPadding(dp(3), dp(4), dp(3), dp(4));
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
        if (wide) {
            root.removeView(clockLine);
            controls.addView(clockLine, 0);
        }
        status.setVisibility(View.INVISIBLE);
        status.setSingleLine(true); status.setMinHeight(dp(24 * Math.max(1, fontScale)));
        root.setPadding(dp(8), dp(4), dp(8), dp(4));
        android.view.ViewGroup parent = (android.view.ViewGroup) root.getParent();
        parent.removeView(root);
        PlayViewport viewport = new PlayViewport(this, root, dp(wide ? 720 : 360), palette.background);
        parent.addView(ScreenChrome.frame(this, heading, viewport, palette.background), new FrameLayout.LayoutParams(-1, -1));
        parent.requestApplyInsets();
    }

    void refresh(String message) {
        syncTime();
        DailyWord.Day day = game.day(date);
        board.show(game, day);
        boolean finished = game.finished(day);
        String result = lossAnswer();
        String feedback = message != null ? message : result;
        status.setText(feedback);
        boolean showMessage = message != null && !message.isEmpty();
        status.setVisibility(showMessage ? View.VISIBLE : View.INVISIBLE);
        status.setTextColor(message != null ? palette.error : palette.ink);
        elapsed.setVisibility(showMessage ? View.INVISIBLE : View.VISIBLE);
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
        refreshLargeControls(feedback);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (game != null && !event.isCtrlPressed() && !event.isAltPressed()) {
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                View focused = getCurrentFocus();
                if (focused != null && focused.isClickable() && focused.isEnabled()) return super.dispatchKeyEvent(event);
                if (event.getAction() == KeyEvent.ACTION_DOWN) input('\0', false, true);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DEL) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) input('\0', true, false);
                return true;
            }
            int unicode = Character.toLowerCase(event.getUnicodeChar());
            if (unicode >= 'a' && unicode <= 'z') {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    input((char) unicode, false, false);
                    DailyWord.Day day = game.day(date);
                    if (!game.finished(day)) board.focusDraft(day.guesses().size());
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void openLargeControls() {
        largeControls = new LargeControlsDialog(this, palette, "Daily Word large controls");
        letterPicker = largeControls.picker("Letter");
        letterChoices = largeControls.choices(letterPicker);
        largeAdd = largeControls.action("Add"); largeAdd.setContentDescription("Add letter");
        largeAdd.setOnClickListener(v -> input((char) ('a' + letterPicker.getSelectedItemPosition()), false, false));
        largeDelete = largeControls.action("Delete", R.drawable.ic_backspace); largeDelete.setOnClickListener(v -> input('\0', true, false));
        largeEnter = largeControls.action("Enter"); largeEnter.setOnClickListener(v -> input('\0', false, true));
        largeFeedback = largeControls.text("");
        for (int row = 0; row < 6; row++) largeGuesses[row] = largeControls.text("");
        largeControls.window.setOnDismissListener(d -> { largeControls = null; });
        refreshLargeControls(status.getText().toString());
        largeControls.window.show();
    }

    private void refreshLargeControls(String feedback) {
        if (largeControls == null) return;
        DailyWord.Day day = game.day(date); boolean finished = game.finished(day);
        int choice = Math.max(0, letterPicker.getSelectedItemPosition());
        int[] states = game.keyboard(day);
        letterChoices.setNotifyOnChange(false); letterChoices.clear();
        for (int i = 0; i < 26; i++) letterChoices.add((char) ('A' + i) + ", " + DailyWordBoard.stateName(states[i]));
        letterChoices.notifyDataSetChanged(); letterPicker.setSelection(choice);
        largeAdd.setEnabled(!finished && day.draft().length() < 5);
        largeDelete.setEnabled(!finished && !day.draft().isEmpty()); largeEnter.setEnabled(!finished);
        letterPicker.setEnabled(!finished);
        largeFeedback.setText(feedback); largeFeedback.setVisibility(feedback.isEmpty() ? View.GONE : View.VISIBLE);
        for (int row = 0; row < 6; row++) largeGuesses[row].setText(DailyWordBoard.rowDescription(game, day, row));
    }

    private void announceFeedback(String message) {
        if (largeControls != null && largeControls.window.isShowing()) largeControls.announce(message);
        else board.announceForAccessibility(message);
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
        resumed = true; checkDate(); syncTime();
        handler.removeCallbacks(ticker); handler.post(ticker);
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
        resumed = false; syncTime(); saveSession.dispatch(DailyWordActivity::checkpointTime);
        handler.removeCallbacks(ticker);
        if (completion != null) completion.stop();
        if (board != null) board.stopFeedback();
        handler.removeCallbacks(rollover);
        if (registered) { unregisterReceiver(dateChanged); registered = false; }
        super.onPause();
    }

    @Override protected void onDestroy() {
        saveSession.detach(this, !isChangingConfigurations());
        loader.shutdownNow();
        if (largeControls != null) largeControls.window.dismiss();
        if (completion != null) completion.stop();
        if (board != null) board.stopFeedback();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void syncTime() {
        playTime.setRunning(resumed && focused && game != null && !game.finished(game.day(date)));
        if (elapsed == null) return;
        long millis = playTime.elapsedMillis();
        String answer = game == null ? "" : lossAnswer();
        String label = ActivePlayTimer.label(millis) + (answer.isEmpty() ? "" : " \u00b7 " + answer);
        if (!android.text.TextUtils.equals(elapsed.getText(), label)) elapsed.setText(label);
        elapsed.setContentDescription(ActivePlayTimer.description(millis) + (answer.isEmpty() ? "" : ". " + answer));
    }

    private String lossAnswer() {
        DailyWord.Day day = game.day(date);
        return game.finished(day) && !game.won(day)
                ? "The word was " + game.answer(date).toUpperCase(Locale.ROOT) + "." : "";
    }

    private void checkpointTime() {
        if (game == null || !saveSession.idle()) return;
        long millis = playTime.elapsedMillis();
        if (!date.equals(durableDate) || durableElapsed != millis) {
            DailyWord next = new DailyWord(game);
            next.day(date).setElapsed(millis);
            game = next;
            LocalDate checkpointDate = date;
            saves.checkpoint(() -> {
                write(next, PuzzleSave.Edit.CHECKPOINT, checkpointDate); durableDate = checkpointDate; durableElapsed = millis;
            }, ex -> {
                Log.e("DailyWord", "Timer checkpoint failed", ex);
                long failedRevision = savedRevision;
                handler.post(() -> { if (!isDestroyed() && savedRevision == failedRevision) saveError(ex); });
            });
        }
    }

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            checkDate(); syncTime();
            if (game != null && (!date.equals(durableDate) || playTime.elapsedMillis() - durableElapsed >= 10000)) checkpointTime();
            if (resumed) handler.postDelayed(this, 1000);
        }
    };

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        focused = hasFocus; syncTime();
        if (!hasFocus) {
            checkpointTime();
            if (completion != null) completion.stop();
            if (board != null) board.stopFeedback();
        }
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        saveSession.dispatch(DailyWordActivity::checkpointTime);
        super.onSaveInstanceState(out);
    }
    @Override public Object onRetainNonConfigurationInstance() { return new Retained(saveSession); }

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
        FrameLayout stage = new FrameLayout(this);
        stage.addView(root, new FrameLayout.LayoutParams(-1, -1));
        completion = new CompletionFeedback(stage, palette);
        setContentView(stage);
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
        ControlStyle.navigation(button, android.content.res.ColorStateList.valueOf((palette.ink & 0x00FFFFFF) | 0x30000000));
        return button;
    }

    private TextView copy(String value, int size, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setGravity(Gravity.CENTER);
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
