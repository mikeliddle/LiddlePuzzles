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
import java.io.FileInputStream;
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
        assertEquals("com.quietgrid.sudoku.validation", getInstrumentation().getTargetContext().getPackageName());
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
            ScrollView overflow = scroll(activity.getWindow().getDecorView());
            assertNotNull("Large text gets a scrollable page, not clipped controls", overflow);
            runTestOnUiThread(() -> overflow.scrollTo(0, overflow.getChildAt(0).getHeight()));
            getInstrumentation().waitForIdleSync();
            assertInside(find("Enter"));
            assertInside(find("Q"));
            assertKeyboardLayout();
            assertCleanChrome();
            type("BLESS");
            tap("Enter");
            DailyWord game = restored();
            assertTrue(game.won(game.day(DailyWord.EPOCH)));
        } finally {
            runTestOnUiThread(() -> activity.getResources().updateConfiguration(original, activity.getResources().getDisplayMetrics()));
        }
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
            assertEquals("Empty feedback must not reserve space", View.GONE, feedback.getVisibility());
        }
    }

    private TextView findFeedback(View view) {
        if (view instanceof TextView && view.getAccessibilityLiveRegion() == View.ACCESSIBILITY_LIVE_REGION_POLITE)
            return (TextView) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            TextView found = findFeedback(((ViewGroup) view).getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private void assertKeyboardLayout() throws Exception {
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
        assertTrue(view.getGlobalVisibleRect(bounds));
        assertEquals(view.getWidth(), bounds.width());
        assertEquals("Fully visible control: " + view.getContentDescription() + ", bounds " + bounds,
                view.getHeight(), bounds.height());
    }

    private void fixed(String instant) throws Throwable {
        runTestOnUiThread(() -> {
            DailyWordActivity word = (DailyWordActivity) activity;
            word.clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
            word.testZone = ZoneOffset.UTC;
            word.checkDate();
        });
        getInstrumentation().waitForIdleSync();
    }

    private void launchWord() {
        activity = getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(), DailyWordActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().waitForIdleSync();
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
        int[] point = new int[2];
        view.getLocationOnScreen(point);
        long time = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN,
                point[0] + view.getWidth() / 2f, point[1] + view.getHeight() / 2f, 0);
        MotionEvent up = MotionEvent.obtain(time, time + 50, MotionEvent.ACTION_UP, down.getX(), down.getY(), 0);
        try {
            getInstrumentation().sendPointerSync(down);
            getInstrumentation().sendPointerSync(up);
        } finally { down.recycle(); up.recycle(); }
        getInstrumentation().waitForIdleSync();
        SystemClock.sleep(40);
    }

    private View find(String text) { return find(text, activity.getWindow().getDecorView()); }
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
             DataInputStream in = new DataInputStream(new FileInputStream(file("daily-word-v1.bin")))) {
            DailyWord game = new DailyWord(guesses, answers);
            game.read(in);
            return game;
        }
    }

    private byte[] bytesOrNull(File file) throws Exception { return file.exists() ? Files.readAllBytes(file.toPath()) : null; }
    private File file(String name) { return new File(getInstrumentation().getTargetContext().getFilesDir(), name); }
    private SharedPreferences settings() { return getInstrumentation().getTargetContext().getSharedPreferences("settings", 0); }
}
