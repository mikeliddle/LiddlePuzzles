package com.quietgrid.sudoku;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.ScrollView;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Run only with an outer durable backup; never targets a player package. */
@SuppressWarnings("deprecation")
public final class DailyWordDeviceTests extends InstrumentationTestCase {
    private Activity activity, hub;
    private final Map<String, byte[]> wordFiles = new LinkedHashMap<>();
    private Map<String, ?> originalSettings;

    @Override protected void setUp() throws Exception {
        super.setUp();
        assertEquals("fyi.mllab.puzzle.validation", getInstrumentation().getTargetContext().getPackageName());
        originalSettings = settings().getAll();
        for (String name : new String[]{"daily-word-v1.bin", "daily-word-v1.bin.bak", "daily-word-v1.bin.new"}) {
            File file = file(name);
            wordFiles.put(name, file.exists() ? Files.readAllBytes(file.toPath()) : null);
            if (file.exists()) assertTrue(file.delete());
        }
        assertTrue(settings().edit().putInt("appearance", 1).putInt("accentColor", 3).commit());
    }

    @Override protected void tearDown() throws Exception {
        try {
            close();
            if (hub != null && !hub.isDestroyed()) {
                getInstrumentation().runOnMainSync(hub::finish);
                getInstrumentation().waitForIdleSync();
            }
            for (Map.Entry<String, byte[]> item : wordFiles.entrySet()) {
                File file = file(item.getKey());
                if (item.getValue() != null) Files.write(file.toPath(), item.getValue());
                else if (file.exists()) assertTrue(file.delete());
            }
            SharedPreferences.Editor editor = settings().edit().clear();
            for (Map.Entry<String, ?> item : originalSettings.entrySet()) {
                Object value = item.getValue();
                if (value instanceof Integer) editor.putInt(item.getKey(), (Integer) value);
                else if (value instanceof Long) editor.putLong(item.getKey(), (Long) value);
                else if (value instanceof Boolean) editor.putBoolean(item.getKey(), (Boolean) value);
                else if (value instanceof String) editor.putString(item.getKey(), (String) value);
                else if (value instanceof Float) editor.putFloat(item.getKey(), (Float) value);
                else throw new AssertionError("Unexpected settings type; outer backup required");
            }
            assertTrue(editor.commit());
        } finally { super.tearDown(); }
    }

    public void testHubKeyboardPersistenceAndModeIsolation() throws Throwable {
        activity = getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        hub = activity;
        getInstrumentation().waitForIdleSync();
        SystemClock.sleep(350);
        assertNotNull(find("Liddle Puzzles"));
        assertNotNull(find("Sudoku"));
        capture("liddle-puzzles-hub-light.png");
        byte[] sudoku = bytesOrNull(file("current-game.bin"));
        Map<String, ?> preferences = new LinkedHashMap<>(settings().getAll());
        Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(DailyWordActivity.class.getName(), null, false);
        tap("Daily Word");
        activity = monitor.waitForActivityWithTimeout(5000);
        getInstrumentation().removeMonitor(monitor);
        assertNotNull("Normal hub touch opens Daily Word", activity);
        fixed("2026-11-16T12:00:00Z");
        assertKeyboardLayout();
        assertCleanChrome();
        assertTrue("Back is a native icon, not a text button", find("Back to Liddle Puzzles") instanceof ImageButton);
        assertEquals("No answer shown early", 0, textCount("BLESS", activity.getWindow().getDecorView()));
        type("APPLE");
        tap("Enter");
        assertKeyboardLayout();
        DailyWord restored = restored();
        assertEquals(1, restored.day(DailyWord.EPOCH).guesses().size());
        assertEquals("E, in word, different spot", "E, " + DailyWordBoard.stateName(restored.keyboard(restored.day(DailyWord.EPOCH))['e' - 'a']));
        type("ZZZZZ");
        tap("Enter");
        assertNotNull(find("Not in the word list. Try another word."));
        assertEquals(1, restored().day(DailyWord.EPOCH).guesses().size());
        for (int i = 0; i < 5; i++) tap("Delete");
        type("BLE");
        capture("daily-word-light-in-progress.png");
        assertFalse("Software IME stays hidden", activity.getWindow().getDecorView().getRootWindowInsets()
                .getSystemWindowInsetBottom() > activity.getResources().getDisplayMetrics().heightPixels / 3);
        tap("Back to Liddle Puzzles");
        activity = hub;
        getInstrumentation().waitForIdleSync();
        assertTrue("Switching games preserves Sudoku bytes", Arrays.equals(sudoku, bytesOrNull(file("current-game.bin"))));
        assertEquals("Word mode never changes Sudoku settings/best times", preferences, settings().getAll());
        launchWord();
        fixed("2026-11-16T12:00:00Z");
        assertEquals("Draft persisted across Activity restart", "ble", restored().day(DailyWord.EPOCH).draft());
        type("SS");
        tap("Enter");
        DailyWord finished = restored();
        assertTrue(finished.won(finished.day(DailyWord.EPOCH)));
        assertCleanChrome();
        Button enter = (Button) find("Enter");
        assertFalse(enter.isEnabled());
        capture("daily-word-light-solved.png");
        String accessible = descriptions(activity.getWindow().getDecorView());
        assertTrue("Submitted row describes feedback in words", accessible.contains("B correct spot"));
        assertTrue("All keyboard labels expose feedback", accessible.contains("B, correct spot"));
        assertContrast();
        rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        fixed("2026-11-16T12:00:00Z");
        assertKeyboardLayout();
        assertCleanChrome();
        capture("daily-word-light-landscape.png");
        byte[] dayBytes = bytesOrNull(file("daily-word-v1.bin"));
        close();
        assertTrue(Arrays.equals(dayBytes, bytesOrNull(file("daily-word-v1.bin"))));
    }

    public void testActiveTimerRolloverAndCompletionMotion() throws Throwable {
        launchWord(); fixed("2026-11-16T12:00:00Z");
        TextView elapsed = activity.findViewById(android.R.id.content).findViewWithTag("elapsed-time");
        assertNotNull(elapsed);
        assertEquals(View.ACCESSIBILITY_LIVE_REGION_NONE, elapsed.getAccessibilityLiveRegion());
        View board = activity.findViewById(android.R.id.content).findViewWithTag("word-board");
        tap("Enter");
        assertNull("Rejected guess does not celebrate", effect());
        SystemClock.sleep(1200); getInstrumentation().waitForIdleSync();
        assertSame("Clock does not replace the board", board, activity.findViewById(android.R.id.content).findViewWithTag("word-board"));
        awaitElapsed(DailyWord.EPOCH, 1000);
        tap("Large controls");
        long paused = restored().day(DailyWord.EPOCH).elapsedMillis();
        SystemClock.sleep(1200);
        assertEquals("Modal pause excludes idle reading", paused, restored().day(DailyWord.EPOCH).elapsedMillis());
        accessibilityClick("Close"); getInstrumentation().waitForIdleSync();
        for (int i = 0; i < 40 && !activity.hasWindowFocus(); i++) SystemClock.sleep(50);
        type("BLE");
        close();
        long saved = restored().day(DailyWord.EPOCH).elapsedMillis();
        SystemClock.sleep(1100); launchWord(); fixed("2026-11-16T12:00:00Z");
        assertTrue("Restart keeps accumulated active time", restored().day(DailyWord.EPOCH).elapsedMillis() >= saved
                && restored().day(DailyWord.EPOCH).elapsedMillis() < saved + 2000);
        assertEquals("Timer save preserves the draft", "ble", restored().day(DailyWord.EPOCH).draft());
        type("SS"); tap("Enter");
        assertTrue(restored().won(restored().day(DailyWord.EPOCH)));
        if (android.animation.ValueAnimator.areAnimatorsEnabled()) assertNotNull(effect());
        capture("daily-word-timer-win-motion.png");
        long won = restored().day(DailyWord.EPOCH).elapsedMillis();
        SystemClock.sleep(1800);
        assertEquals(won, restored().day(DailyWord.EPOCH).elapsedMillis());
        assertNull(effect());
        getInstrumentation().runOnMainSync(() -> ((DailyWordActivity) activity).refresh(null));
        assertNull("Terminal refresh is still", effect());
        rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        fixed("2026-11-16T12:00:00Z");
        assertNull("Terminal recreation is still", effect());
        assertEquals(won, restored().day(DailyWord.EPOCH).elapsedMillis());
        fixed("2026-11-17T12:00:00Z");
        assertTrue("New date is a separate clock", restored().day(DailyWord.EPOCH.plusDays(1)).elapsedMillis() < 1500);
        type("A"); awaitElapsed(DailyWord.EPOCH.plusDays(1), 1000);
        long tomorrow = restored().day(DailyWord.EPOCH.plusDays(1)).elapsedMillis();
        assertTrue(tomorrow >= 1000);
        fixed("2026-11-16T12:00:00Z");
        assertEquals("Completed-date revisit never restarts its clock", won, restored().day(DailyWord.EPOCH).elapsedMillis());
        assertNull(effect());
        getInstrumentation().runOnMainSync(() -> {
            try {
                java.lang.reflect.Field field = DailyWordActivity.class.getDeclaredField("completion");
                field.setAccessible(true); ((CompletionFeedback) field.get(activity)).celebrate(false);
            } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
        });
        assertNull(effect());
        capture("daily-word-timer-frozen-landscape.png");
    }

    private View effect() { return activity.findViewById(android.R.id.content).findViewWithTag("completion-effect"); }

    private void awaitElapsed(java.time.LocalDate date, long minimum) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 15000;
        while (SystemClock.uptimeMillis() < deadline && restored().day(date).elapsedMillis() < minimum)
            SystemClock.sleep(100);
        assertTrue("Elapsed checkpoints reach the requested active interval", restored().day(date).elapsedMillis() >= minimum);
    }

    public void testDarkLandscapeRolloverAndLoss() throws Throwable {
        assertTrue(settings().edit().putInt("appearance", 2).putInt("accentColor", 1).commit());
        launchWord();
        fixed("2024-03-10T07:59:59Z");
        type("APP");
        String previousDraft = restored().day(java.time.LocalDate.of(2024, 3, 10)).draft();
        fixed("2024-03-11T00:00:00Z");
        assertTrue(restored().day(java.time.LocalDate.of(2024, 3, 10)).draft().equals(previousDraft));
        fixed("2024-03-10T12:00:00Z");
        assertEquals(previousDraft, restored().day(java.time.LocalDate.of(2024, 3, 10)).draft());
        for (int i = 0; i < 3; i++) tap("Delete");
        fixed("2026-11-16T12:00:00Z");
        for (String guess : new String[]{"CRANE", "GRACE", "APPLE", "BREAD", "HOUSE", "SLATE"}) {
            type(guess);
            tap("Enter");
        }
        assertNotNull("Loss still reveals the answer", find("The word was BLESS."));
        long lostElapsed = restored().day(DailyWord.EPOCH).elapsedMillis();
        SystemClock.sleep(1100);
        assertEquals("Word loss freezes elapsed too", lostElapsed, restored().day(DailyWord.EPOCH).elapsedMillis());
        assertNull("Word loss never celebrates", effect());
        assertKeyboardLayout();
        assertCleanChrome();
        capture("daily-word-dark-loss.png");
        rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        fixed("2026-11-16T12:00:00Z");
        assertEquals("Landscape retains finished guesses", 6, restored().day(DailyWord.EPOCH).guesses().size());
        assertFalse(((Button) find("Enter")).isEnabled());
        assertInside(find("Enter"));
        assertInside(find("Q"));
        assertKeyboardLayout();
        assertCleanChrome();
        capture("daily-word-dark-landscape.png");
        fixed("2026-11-17T00:00:00Z");
        assertTrue(((Button) find("Enter")).isEnabled());
        assertCleanChrome();
        capture("daily-word-dark-landscape-keyboard.png");
        for (int i = 0; i < 40 && !activity.hasWindowFocus(); i++) SystemClock.sleep(50);
        assertTrue("Recreated landscape window receives hardware input", activity.hasWindowFocus());
        getInstrumentation().sendStringSync("sense");
        getInstrumentation().waitForIdleSync();
        assertEquals("Hardware keyboard persists every letter", "sense", restored().day(DailyWord.EPOCH.plusDays(1)).draft());
        assertNotNull(activity.getCurrentFocus());
        assertFalse("Typing scopes Enter to the guess, not an earlier navigation focus", activity.getCurrentFocus().isClickable());
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER);
        getInstrumentation().waitForIdleSync();
        DailyWord finished = restored();
        assertTrue(finished.won(finished.day(DailyWord.EPOCH.plusDays(1))));
        assertCleanChrome();
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER);
        getInstrumentation().waitForIdleSync();
        assertCleanChrome();
        fixed("2026-11-16T23:59:59Z");
        assertFalse("Clock rollback cannot reset played day", ((Button) find("Enter")).isEnabled());
        assertEquals(6, restored().day(DailyWord.EPOCH).guesses().size());
        assertContrast();
    }

    public void testLargeTextControlsRemainReachable() throws Throwable {
        launchWord();
        fixed("2026-11-16T12:00:00Z");
        Configuration original = new Configuration(activity.getResources().getConfiguration());
        try {
            runTestOnUiThread(() -> {
                Configuration enlarged = new Configuration(original);
                enlarged.fontScale = 1.6f;
                activity.getResources().updateConfiguration(enlarged, activity.getResources().getDisplayMetrics());
                DailyWordActivity word = (DailyWordActivity) activity;
                word.build();
                word.refresh(null);
            });
            getInstrumentation().waitForIdleSync();
            assertNull("Normal play fits rather than scrolling", scroll(activity.getWindow().getDecorView()));
            ViewportChecks.all(activity);
            assertCleanChrome();
            type("BLESS");
            tap("Enter");
            DailyWord game = restored();
            assertTrue(game.won(game.day(DailyWord.EPOCH)));
        } finally {
            runTestOnUiThread(() -> activity.getResources().updateConfiguration(original, activity.getResources().getDisplayMetrics()));
        }
    }

    public void testAccessibleFocusLargeInputAndSingleFeedback() throws Throwable {
        launchWord(); fixed("2026-11-16T12:00:00Z");
        focusEnter(find("Q")); assertEquals("q", restored().day(DailyWord.EPOCH).draft());
        focusEnter(find("Delete")); assertEquals("", restored().day(DailyWord.EPOCH).draft());
        Configuration original = new Configuration(activity.getResources().getConfiguration());
        try {
            runTestOnUiThread(() -> {
                Configuration large = new Configuration(original); large.fontScale = 1.6f;
                activity.getResources().updateConfiguration(large, activity.getResources().getDisplayMetrics());
                ((DailyWordActivity) activity).build(); ((DailyWordActivity) activity).refresh(null);
            });
            getInstrumentation().waitForIdleSync(); SystemClock.sleep(300);
            tap("Large controls");
            java.lang.reflect.Field field = DailyWordActivity.class.getDeclaredField("largeControls"); field.setAccessible(true);
            LargeControlsDialog dialog = (LargeControlsDialog) field.get(activity);
            getInstrumentation().waitForIdleSync(); SystemClock.sleep(300);
            for (int i = 0; i < dialog.content.getChildCount(); i++) {
                View child = dialog.content.getChildAt(i);
                if (child.isClickable()) {
                    float density = child.getResources().getDisplayMetrics().density;
                    assertTrue("Alternative target width", child.getWidth() / density >= 48);
                    assertTrue("Alternative target height", child.getHeight() / density >= 48);
                }
                if (child instanceof TextView) assertTrue("Large text scales, not fitted away",
                        ((TextView) child).getTextSize() / child.getResources().getDisplayMetrics().density >= 31);
            }
            View add = find("Add letter", dialog.content); assertNotNull(add);
            focusEnter(add); assertEquals("a", restored().day(DailyWord.EPOCH).draft());
            View delete = find("Delete", dialog.content);
            runTestOnUiThread(() -> delete.requestRectangleOnScreen(new Rect(0, 0, delete.getWidth(), delete.getHeight()), true));
            getInstrumentation().waitForIdleSync();
            accessibilityClick("Delete"); assertEquals("", restored().day(DailyWord.EPOCH).draft());
            capture("puzzles-a11y-word-large.png");
            runTestOnUiThread(dialog.window::dismiss);
        } finally {
            runTestOnUiThread(() -> {
                activity.getResources().updateConfiguration(original, activity.getResources().getDisplayMetrics());
                ((DailyWordActivity) activity).build(); ((DailyWordActivity) activity).refresh(null);
            });
        }
        getInstrumentation().waitForIdleSync(); SystemClock.sleep(350);
        java.util.List<String> spoken = new java.util.concurrent.CopyOnWriteArrayList<>();
        getInstrumentation().getUiAutomation().setOnAccessibilityEventListener(event -> {
            if (event.getEventType() == android.view.accessibility.AccessibilityEvent.TYPE_ANNOUNCEMENT)
                spoken.add(event.getText().toString());
        });
        try {
            type("APPLE"); tap("Enter"); SystemClock.sleep(300);
            assertEquals("One explicit accepted-row announcement", 1, spoken.size());
            assertTrue(spoken.get(0).contains("Guess 1")); assertFalse(spoken.get(0).contains("BLESS"));
            type("BLESS"); tap("Enter"); SystemClock.sleep(300);
            assertEquals("One explicit win announcement", 2, spoken.size());
            assertTrue(spoken.get(1).contains("Puzzle complete"));
            focusEnter(find("Back to Liddle Puzzles"));
            assertTrue("Enter activates focused Back after completion", activity.isFinishing() || activity.isDestroyed());
            activity = null;
            File state = file("daily-word-v1.bin"); assertTrue(state.delete());
            launchWord(); fixed("2026-11-16T12:00:00Z"); spoken.clear();
            for (String guess : new String[]{"APPLE", "CRANE", "MOUTH", "FIGHT", "ROUND", "STOCK"}) {
                type(guess); tap("Enter"); SystemClock.sleep(200);
            }
            assertEquals("One announcement per accepted attempt", 6, spoken.size());
            for (int i = 0; i < 5; i++) assertFalse("No early answer", spoken.get(i).contains("The word was"));
            assertTrue(spoken.get(5).contains("No guesses remaining. The word was BLESS"));
        } finally { getInstrumentation().getUiAutomation().setOnAccessibilityEventListener(null); }
    }

    private void focusEnter(View view) throws Throwable {
        assertNotNull(view);
        runTestOnUiThread(() -> { view.setFocusableInTouchMode(true); assertTrue(view.requestFocus()); });
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER);
        getInstrumentation().waitForIdleSync();
    }

    private void accessibilityClick(String label) {
        for (int attempt = 0; attempt < 20; attempt++) {
            android.view.accessibility.AccessibilityNodeInfo root = getInstrumentation().getUiAutomation().getRootInActiveWindow();
            if (root != null) for (android.view.accessibility.AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(label))
                if ((node.getText() != null && label.contentEquals(node.getText())
                        || node.getContentDescription() != null && label.contentEquals(node.getContentDescription())) && node.isClickable()) {
                    assertTrue("Native accessibility click", node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK));
                    getInstrumentation().waitForIdleSync(); return;
                }
            SystemClock.sleep(100);
        }
        fail("No active accessible action: " + label);
    }

    private ScrollView scroll(View view) {
        if (view instanceof ScrollView) return (ScrollView) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            ScrollView found = scroll(((ViewGroup) view).getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private void rotate(int orientation) {
        Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(DailyWordActivity.class.getName(), null, false);
        getInstrumentation().runOnMainSync(() -> activity.setRequestedOrientation(orientation));
        Activity replacement = monitor.waitForActivityWithTimeout(4000);
        getInstrumentation().removeMonitor(monitor);
        if (replacement != null) activity = replacement;
        getInstrumentation().waitForIdleSync();
        for (int i = 0; i < 40 && !activity.hasWindowFocus(); i++) SystemClock.sleep(50);
        assertTrue("Rotated window is ready for input", activity.hasWindowFocus());
        SystemClock.sleep(650);
    }

    private void assertCleanChrome() {
        View root = activity.getWindow().getDecorView();
        for (String removed : new String[]{"How to play", "local date", "Solved in ", "Next word after local midnight", "Back"}) {
            if (removed.equals("Back")) assertNull("No text Back button", find("Back"));
            else assertEquals("Removed visible copy: " + removed, 0, textCount(removed, root));
        }
        TextView feedback = findFeedback(root);
        assertNotNull(feedback);
        if (feedback.getText().length() == 0) {
            assertEquals("Empty feedback shares the timer slot without shifting play", View.INVISIBLE, feedback.getVisibility());
        }
    }

    public void testFullWordViewportAndStableFeedback() throws Throwable {
        launchWord(); fixed("2026-11-16T12:00:00Z");
        for (int orientation : new int[]{ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}) {
            rotate(orientation); fixed("2026-11-16T12:00:00Z");
            Configuration prior = new Configuration(activity.getResources().getConfiguration());
            try {
                for (float scale : new float[]{1f, 2f}) {
                    runTestOnUiThread(() -> {
                        Configuration changed = new Configuration(prior); changed.fontScale = scale;
                        activity.getResources().updateConfiguration(changed, activity.getResources().getDisplayMetrics());
                        ((DailyWordActivity) activity).build(); ((DailyWordActivity) activity).refresh(null);
                    });
                    getInstrumentation().waitForIdleSync(); SystemClock.sleep(250);
                    ViewportChecks.polishedGameplay(activity);
                    assertKeyboardLayout();
                    View board = activity.findViewById(android.R.id.content).findViewWithTag("word-board");
                    android.graphics.RectF before = ViewportChecks.bounds(board);
                    tap("Enter");
                    assertEquals("Validation never pushes the board", before, ViewportChecks.bounds(board));
                    ViewportChecks.all(activity);
                    capture("daily-word-fit-" + orientation + "-" + (int) scale + ".png");
                }
            } finally { runTestOnUiThread(() ->
                    activity.getResources().updateConfiguration(prior, activity.getResources().getDisplayMetrics())); }
        }
    }

    private TextView findFeedback(View view) {
        if (view instanceof TextView && "word-feedback".equals(view.getTag()))
            return (TextView) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            TextView found = findFeedback(((ViewGroup) view).getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private void assertKeyboardLayout() throws Exception {
        View root = activity.findViewById(android.R.id.content);
        View board = root.findViewWithTag("word-board"), controls = root.findViewWithTag("word-controls");
        assertNotNull(board); assertNotNull(controls);
        android.graphics.RectF a = ViewportChecks.bounds(board), b = ViewportChecks.bounds(controls);
        float density = board.getResources().getDisplayMetrics().density;
        float below = (b.top - a.bottom) / density;
        float beside = (b.left - a.right) / density;
        assertTrue("Keyboard and grid stay together without a bottom spacer",
                below >= 0 && below <= 16 || beside >= 0 && beside <= 16);
        Button reference = (Button) find("Q");
        assertNotNull(reference);
        StringBuilder geometry = new StringBuilder("key,x,y,width,height,textPixels\n");
        for (char letter : "QWERTYUIOPASDFGHJKLZXCVBNM".toCharArray()) {
            Button key = (Button) find(String.valueOf(letter));
            assertNotNull(key);
            assertEquals("Uniform letter width: " + letter, reference.getWidth(), key.getWidth());
            assertEquals("Uniform letter height: " + letter, reference.getHeight(), key.getHeight());
            assertEquals("Uniform letter text: " + letter, reference.getTextSize(), key.getTextSize(), 0f);
            assertEquals(reference.getTypeface(), key.getTypeface());
            assertTrue("Letters keep real padding inside the key outline", key.getPaddingLeft() >= 3 * density
                    && key.getPaddingRight() >= 3 * density);
            ViewportChecks.textFits(key);
            assertInside(key);
            int[] position = new int[2];
            key.getLocationOnScreen(position);
            geometry.append(letter).append(',').append(position[0]).append(',').append(position[1]).append(',')
                    .append(key.getWidth()).append(',').append(key.getHeight()).append(',').append(key.getTextSize()).append('\n');
        }
        assertTrue("Middle row stagger", find("A").getLeft() > reference.getLeft());
        assertTrue("Bottom row stagger", find("Z").getLeft() > find("A").getLeft());
        assertEquals("Delete belongs to the middle row", find("A").getTop(), find("Delete").getTop());
        assertEquals("Enter belongs to the bottom row", find("Z").getTop(), find("Enter").getTop());
        assertTrue("Delete follows L", find("Delete").getLeft() >= find("L").getRight());
        assertTrue("Enter follows M", find("Enter").getLeft() >= find("M").getRight());
        assertInside(find("Delete"));
        assertInside(find("Enter"));
        File directory = new File(getInstrumentation().getTargetContext().getExternalFilesDir(null), "daily-word-evidence");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        Files.write(new File(directory, "keyboard-" + activity.getResources().getConfiguration().orientation
                + "-" + activity.getResources().getConfiguration().fontScale + ".csv").toPath(),
                geometry.toString().getBytes(StandardCharsets.UTF_8));
    }

    public void testLossKeepsFrozenTimeAndAnswerThroughResizeAndResume() throws Throwable {
        launchWord(); fixed("2026-11-16T12:00:00Z");
        for (String guess : new String[]{"CRANE", "GRACE", "APPLE", "BREAD", "HOUSE"}) {
            type(guess); tap("Enter");
        }
        View board = activity.findViewById(android.R.id.content).findViewWithTag("word-board");
        View controls = activity.findViewById(android.R.id.content).findViewWithTag("word-controls");
        android.graphics.RectF boardBefore = ViewportChecks.bounds(board), controlsBefore = ViewportChecks.bounds(controls);
        type("SLATE"); tap("Enter");
        long frozen = restored().day(DailyWord.EPOCH).elapsedMillis();
        assertEquals("Loss does not move the board", boardBefore, ViewportChecks.bounds(board));
        assertEquals("Loss does not move the keyboard", controlsBefore, ViewportChecks.bounds(controls));
        assertVisibleLossClock(frozen);
        SystemClock.sleep(1100); assertVisibleLossClock(frozen);
        for (int orientation : new int[]{ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}) {
            rotate(orientation); fixed("2026-11-16T12:00:00Z");
            Configuration prior = new Configuration(activity.getResources().getConfiguration());
            try {
                for (float scale : new float[]{1f, 2f}) {
                    runTestOnUiThread(() -> {
                        Configuration changed = new Configuration(prior); changed.fontScale = scale;
                        activity.getResources().updateConfiguration(changed, activity.getResources().getDisplayMetrics());
                        ((DailyWordActivity) activity).build(); ((DailyWordActivity) activity).refresh(null);
                    });
                    getInstrumentation().waitForIdleSync(); SystemClock.sleep(200);
                    assertVisibleLossClock(frozen); ViewportChecks.all(activity); assertKeyboardLayout();
                    capture("daily-word-loss-clock-" + orientation + "-" + (int) scale + ".png");
                }
            } finally { runTestOnUiThread(() ->
                    activity.getResources().updateConfiguration(prior, activity.getResources().getDisplayMetrics())); }
        }
        close(); launchWord(); fixed("2026-11-16T12:00:00Z");
        assertVisibleLossClock(frozen);
        assertNull("A restored loss never celebrates", effect());
    }

    private void assertVisibleLossClock(long frozen) throws Exception {
        TextView timer = activity.findViewById(android.R.id.content).findViewWithTag("elapsed-time");
        assertEquals(View.VISIBLE, timer.getVisibility());
        assertEquals(ActivePlayTimer.label(frozen) + " \u00b7 The word was BLESS.", timer.getText().toString());
        assertTrue(timer.getContentDescription().toString().contains("The word was BLESS."));
        assertEquals("The completed clock never restarts", frozen, restored().day(DailyWord.EPOCH).elapsedMillis());
        ViewportChecks.textFits(timer); assertInside(timer);
    }

    private void assertContrast() {
        for (boolean dark : new boolean[]{false, true}) {
            MainActivity.Palette palette = new MainActivity.Palette(dark, 3);
            for (int state = 0; state < 3; state++) {
                double foreground = android.graphics.Color.luminance(DailyWordBoard.foreground(palette, state));
                double background = android.graphics.Color.luminance(DailyWordBoard.fill(palette, state));
                assertTrue("Letter state contrast", (Math.max(foreground, background) + .05)
                        / (Math.min(foreground, background) + .05) >= 4.5);
            }
        }
    }

    private void assertInside(View view) {
        Rect bounds = new Rect();
        android.graphics.RectF expected = ViewportChecks.bounds(view);
        assertTrue(view.getGlobalVisibleRect(bounds));
        assertEquals(expected.width(), bounds.width(), 2f);
        assertEquals("Fully visible control: " + view.getContentDescription() + ", bounds " + bounds,
                expected.height(), bounds.height(), 2f);
    }

    public void testWordLetterAndAcceptedRowMotion() throws Throwable {
        launchWord(); fixed("2026-11-16T12:00:00Z");
        DailyWordBoard board = (DailyWordBoard) findType(activity.getWindow().getDecorView(), DailyWordBoard.class);
        assertNotNull(board); assertFalse("Opening is static", board.feedbackActive());
        tap("A"); assertTrue("Real key touch starts letter settle", board.feedbackActive());
        PuzzleDeviceTests.motionFrames(getInstrumentation(), "motion-word-letter", board);
        type("PPLE");
        assertEquals("Repeated letters target keys, not matching unsubmitted tiles", "apple", restored().day(DailyWord.EPOCH).draft());
        assertSame("Input retains the current board", board, findType(activity.getWindow().getDecorView(), DailyWordBoard.class));
        assertTrue("Accepted-row input has window focus", activity.hasWindowFocus());
        capture("polish-word-centered-draft.png");
        tap("Enter");
        assertEquals("Real Enter durably accepts the row", 1, restored().day(DailyWord.EPOCH).guesses().size());
        assertTrue("Accepted row has one staggered settle", board.feedbackActive());
        assertEquals(1, restored().day(DailyWord.EPOCH).guesses().size());
        PuzzleDeviceTests.motionFrames(getInstrumentation(), "motion-word-guess", board);
        assertFalse(board.feedbackActive());
        tap("B"); tap("L"); tap("Delete");
        assertTrue("Rapid erase replaces stale letter feedback", board.feedbackActive());
        assertEquals("b", restored().day(DailyWord.EPOCH).draft());
        DailyWord.Day before = restored().day(DailyWord.EPOCH);
        getInstrumentation().runOnMainSync(() -> board.changed(before, before, false, false));
        assertFalse("Disabled-motion branch keeps static letters", board.feedbackActive());
        getInstrumentation().runOnMainSync(() -> ((DailyWordActivity) activity).refresh(null));
        assertFalse("Repaint never replays input", board.feedbackActive());
        tap("Enter");
        assertFalse("Rejected submission never celebrates", board.feedbackActive());
        close(); launchWord(); fixed("2026-11-16T12:00:00Z");
        DailyWordBoard resumedBoard = (DailyWordBoard) findType(activity.getWindow().getDecorView(), DailyWordBoard.class);
        assertFalse("Resume never replays input", resumedBoard.feedbackActive());
        assertEquals("b", restored().day(DailyWord.EPOCH).draft());
        assertKeyboardLayout();
    }

    public void testSettingsBackReturnsToWordAndKeepsDraft() throws Throwable {
        launchWord(); fixed("2026-11-16T12:00:00Z"); type("BLE");
        Activity word = activity;
        for (boolean systemBack : new boolean[]{false, true}) {
            Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(MainActivity.class.getName(), null, false);
            tap("Settings");
            activity = monitor.waitForActivityWithTimeout(5000); getInstrumentation().removeMonitor(monitor);
            assertNotNull(activity); getInstrumentation().waitForIdleSync();
            assertFalse("Settings cancels in-progress letter feedback",
                    ((DailyWordBoard) findType(word.getWindow().getDecorView(), DailyWordBoard.class)).feedbackActive());
            assertNotNull(find("Back")); assertNull(find("Back to settings"));
            long paused = restored().day(DailyWord.EPOCH).elapsedMillis();
            SystemClock.sleep(1100);
            assertEquals(paused, restored().day(DailyWord.EPOCH).elapsedMillis());
            if (systemBack) {
                monitor = getInstrumentation().addMonitor(MainActivity.class.getName(), null, false);
                getInstrumentation().runOnMainSync(() -> activity.recreate());
                activity = monitor.waitForActivityWithTimeout(5000); getInstrumentation().removeMonitor(monitor);
                assertNotNull(activity); getInstrumentation().waitForIdleSync();
                getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            } else tap("Back");
            getInstrumentation().waitForIdleSync(); assertTrue(activity.isFinishing());
            activity = word; getInstrumentation().waitForIdleSync();
            assertEquals("ble", restored().day(DailyWord.EPOCH).draft());
            assertNotNull(find("Enter"));
        }
        capture("bugfix-settings-word-return.png");
    }

    private void fixed(String instant) throws Throwable {
        runTestOnUiThread(() -> {
            DailyWordActivity word = (DailyWordActivity) activity;
            word.clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
            word.testZone = ZoneOffset.UTC;
            word.checkDate();
        });
        awaitWordReady(Instant.parse(instant).atZone(ZoneOffset.UTC).toLocalDate());
    }

    private void awaitWordReady(java.time.LocalDate expectedDate) throws Exception {
        java.lang.reflect.Field date = DailyWordActivity.class.getDeclaredField("date");
        java.lang.reflect.Field session = DailyWordActivity.class.getDeclaredField("saveSession");
        date.setAccessible(true); session.setAccessible(true);
        boolean[] ready = {false};
        long deadline = SystemClock.uptimeMillis() + 5000;
        do {
            getInstrumentation().runOnMainSync(() -> {
                try {
                    View key = find("Q");
                    ready[0] = key != null && key.isLaidOut()
                            && (expectedDate == null || expectedDate.equals(date.get(activity)))
                            && ((SaveSession<?>) session.get(activity)).idle();
                } catch (IllegalAccessException ex) { throw new AssertionError(ex); }
            });
            if (!ready[0]) SystemClock.sleep(25);
        } while (!ready[0] && SystemClock.uptimeMillis() < deadline);
        assertTrue("Word loading and any date transition are durably ready", ready[0]);
    }

    private void launchWord() throws Exception {
        activity = getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(), DailyWordActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        awaitWordReady(null);
    }

    private void close() {
        if (activity != null && !activity.isDestroyed()) {
            getInstrumentation().runOnMainSync(activity::finish);
            getInstrumentation().waitForIdleSync();
        }
        activity = null;
    }

    private void type(String word) {
        for (char letter : word.toCharArray()) tap(String.valueOf(letter));
    }

    private void tap(String name) {
        View view = find(name);
        assertNotNull("Visible native control: " + name, view);
        android.graphics.RectF point = ViewportChecks.bounds(view);
        long time = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN,
                point.centerX(), point.centerY(), 0);
        MotionEvent up = null;
        try {
            getInstrumentation().sendPointerSync(down);
            SystemClock.sleep(60);
            up = MotionEvent.obtain(time, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, down.getX(), down.getY(), 0);
            getInstrumentation().sendPointerSync(up);
        } finally { down.recycle(); if (up != null) up.recycle(); }
        getInstrumentation().waitForIdleSync();
        ViewportChecks.awaitEdits(getInstrumentation(), activity);
        SystemClock.sleep(40);
    }

    private View findType(View view, Class<? extends View> type) {
        if (type.isInstance(view)) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View child = findType(((ViewGroup) view).getChildAt(i), type); if (child != null) return child;
        }
        return null;
    }

    private View find(String text) {
        View root = activity.getWindow().getDecorView();
        if (text.length() == 1 && text.charAt(0) >= 'A' && text.charAt(0) <= 'Z') {
            View keyboard = root.findViewWithTag("word-controls");
            if (keyboard != null) return find(text, keyboard);
        }
        return find(text, root);
    }
    private View find(String text, View view) {
        if ((view.getContentDescription() != null && text.contentEquals(view.getContentDescription()))
                || (view instanceof TextView && text.contentEquals(((TextView) view).getText()))) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = find(text, group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private int textCount(String text, View view) {
        int count = view instanceof TextView && ((TextView) view).getText().toString().contains(text) ? 1 : 0;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            count += textCount(text, ((ViewGroup) view).getChildAt(i));
        return count;
    }

    private String descriptions(View view) {
        StringBuilder text = new StringBuilder().append(view.getContentDescription()).append('\n');
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            text.append(descriptions(((ViewGroup) view).getChildAt(i)));
        return text.toString();
    }

    private void capture(String name) throws Exception {
        SystemClock.sleep(160);
        Bitmap screenshot = getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(screenshot);
        File directory = new File(getInstrumentation().getTargetContext().getExternalFilesDir(null), "daily-word-evidence");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream stream = new FileOutputStream(new File(directory, name))) {
            assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream));
        }
        screenshot.recycle();
    }

    private DailyWord restored() throws Exception {
        try (InputStreamReader guesses = new InputStreamReader(getInstrumentation().getTargetContext().getAssets()
                .open("daily-word/guesses-v1.txt"), StandardCharsets.US_ASCII);
             InputStreamReader answers = new InputStreamReader(getInstrumentation().getTargetContext().getAssets()
                     .open("daily-word/answers-v1.txt"), StandardCharsets.US_ASCII);
             DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(AndroidSaveFile.read(file("daily-word-v1.bin"))))) {
            DailyWord game = new DailyWord(guesses, answers);
            game.read(in);
            return game;
        }
    }

    private byte[] bytesOrNull(File file) throws Exception { return file.exists() ? Files.readAllBytes(file.toPath()) : null; }
    private File file(String name) { return new File(getInstrumentation().getTargetContext().getFilesDir(), name); }
    private SharedPreferences settings() { return getInstrumentation().getTargetContext().getSharedPreferences("settings", 0); }
}
