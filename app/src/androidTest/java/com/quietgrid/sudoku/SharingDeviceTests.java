package com.quietgrid.sudoku;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

@SuppressWarnings("deprecation")
public final class SharingDeviceTests extends InstrumentationTestCase {
    private Activity activity;
    private final Map<File, byte[]> originals = new LinkedHashMap<>();
    private Map<String, ?> preferences;
    private static final String DIGITS = "530070000600195000098000060800060003400803001700020006060000280000419005000080079";

    private int[] clues(boolean alternate) {
        int[] clues = DIGITS.chars().map(c -> c - '0').toArray();
        if (alternate) for (int i = 0; i < 81; i++) {
            if (clues[i] == 1) clues[i] = 2;
            else if (clues[i] == 2) clues[i] = 1;
        }
        return clues;
    }

    private String link(boolean alternate) { return SudokuShare.link(clues(alternate)); }
    private File file(String name) { return new File(getInstrumentation().getTargetContext().getFilesDir(), name); }
    private File sharedFile(boolean alternate) { return file("shared-sudoku-" + SudokuShare.token(link(alternate)) + ".bin"); }
    private SharedPreferences settings() { return getInstrumentation().getTargetContext().getSharedPreferences("settings", 0); }

    @Override protected void setUp() throws Exception {
        super.setUp();
        assertEquals("fyi.mllab.puzzle.validation", getInstrumentation().getTargetContext().getPackageName());
        preferences = settings().getAll();
        for (File base : new File[]{file("current-game.bin"), sharedFile(false), sharedFile(true)}) {
            for (String suffix : new String[]{"", ".bak", ".new"}) {
                File target = new File(base.getPath() + suffix);
                originals.put(target, target.exists() ? Files.readAllBytes(target.toPath()) : null);
                Files.deleteIfExists(target.toPath());
            }
        }
        assertTrue(settings().edit().putInt("accentColor", 3).commit());
        Game normal = SudokuShare.open(link(true));
        normal.elapsedMillis = 123456;
        Files.write(file("current-game.bin").toPath(), encode(normal));
    }

    @Override protected void tearDown() throws Exception {
        try {
            close();
            for (Map.Entry<File, byte[]> entry : originals.entrySet()) {
                if (entry.getValue() == null) Files.deleteIfExists(entry.getKey().toPath());
                else Files.write(entry.getKey().toPath(), entry.getValue());
            }
            SharedPreferences.Editor edit = settings().edit().clear();
            for (Map.Entry<String, ?> entry : preferences.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Boolean v) edit.putBoolean(entry.getKey(), v);
                else if (value instanceof Integer v) edit.putInt(entry.getKey(), v);
                else if (value instanceof Long v) edit.putLong(entry.getKey(), v);
                else if (value instanceof String v) edit.putString(entry.getKey(), v);
                else if (value instanceof Float v) edit.putFloat(entry.getKey(), v);
                else if (value instanceof Set<?> values) {
                    Set<String> strings = new HashSet<>();
                    for (Object item : values) {
                        if (!(item instanceof String v)) throw new AssertionError("Invalid preference");
                        strings.add(v);
                    }
                    edit.putStringSet(entry.getKey(), strings);
                } else throw new AssertionError("Unexpected preference type");
            }
            assertTrue(edit.commit());
        } finally { super.tearDown(); }
    }

    public void testColdOpenSeparateProgressRotationAndNoRecords() throws Exception {
        byte[] normal = Files.readAllBytes(file("current-game.bin").toPath());
        Map<String, ?> before = settings().getAll();
        startLink(link(false));
        assertTrue(Arrays.equals(clues(false), game().clues));
        assertTrue(Arrays.equals(game().clues, game().values));
        assertTrue(sharedFile(false).isFile());
        capture("shared-sudoku-portrait.png");
        getInstrumentation().runOnMainSync(() -> ViewportChecks.polishedGameplay(activity));
        getInstrumentation().runOnMainSync(() -> {
            Game game = game();
            int target = 0; while (game.clues[target] != 0) target++;
            game.selected = target;
            assertTrue(game.enter(game.solution[target]));
            invoke("changed");
        });
        NativeSaveAwaiter.await(getInstrumentation(), activity);
        int[] moved = game().values.clone();
        Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(MainActivity.class.getName(), null, false);
        getInstrumentation().runOnMainSync(() ->
                activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
        activity = monitor.waitForActivityWithTimeout(10000);
        getInstrumentation().removeMonitor(monitor);
        assertNotNull(activity);
        NativeSaveAwaiter.await(getInstrumentation(), activity);
        assertTrue(Arrays.equals(moved, game().values));
        capture("shared-sudoku-landscape.png");
        getInstrumentation().runOnMainSync(() -> ViewportChecks.polishedGameplay(activity));
        close();
        startLink(link(false));
        assertTrue(Arrays.equals(moved, game().values));
        getInstrumentation().runOnMainSync(() -> {
            Game game = game();
            System.arraycopy(game.solution, 0, game.values, 0, 81);
            invoke("changed");
        });
        NativeSaveAwaiter.await(getInstrumentation(), activity);
        assertTrue(game().won());
        assertEquals(before, settings().getAll());
        assertTrue(Arrays.equals(normal, Files.readAllBytes(file("current-game.bin").toPath())));
    }

    public void testWarmIntentOpensDifferentPuzzleWithoutReplacingNormal() throws Exception {
        Files.write(sharedFile(false).toPath(), encode(SudokuShare.open(link(false))));
        activity = getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        NativeSaveAwaiter.await(getInstrumentation(), activity);
        byte[] normal = Files.readAllBytes(file("current-game.bin").toPath());
        Activity previous = activity;
        Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(MainActivity.class.getName(), null, false);
        getInstrumentation().runOnMainSync(() -> {
            try {
                Method method = MainActivity.class.getDeclaredMethod("onNewIntent", Intent.class);
                method.setAccessible(true);
                method.invoke(previous, viewIntent(link(true)));
            } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
        });
        activity = monitor.waitForActivityWithTimeout(10000);
        getInstrumentation().removeMonitor(monitor);
        assertNotNull(activity);
        assertNotSame(previous, activity);
        NativeSaveAwaiter.await(getInstrumentation(), activity);
        assertTrue(Arrays.equals(clues(true), game().clues));
        assertTrue(sharedFile(false).isFile());
        assertTrue(sharedFile(true).isFile());
        assertTrue(Arrays.equals(normal, Files.readAllBytes(file("current-game.bin").toPath())));
    }

    public void testSharedSudokuUsesNormalQuotaWithoutChangingNormalSave() throws Exception {
        File normal = file("current-game.bin");
        PuzzleSave quota = PuzzleSave.forFile(normal);
        var today = ResponsibleMode.today();
        byte[] payload = encode(SudokuShare.open(link(true)));
        quota.read(AndroidSaveFile.at(normal), 16384);
        quota.queue().writeBlocking(() -> {
            for (int i = 0; i < 9; i++) {
                quota.commit(AndroidSaveFile.at(normal), payload, PuzzleSave.Edit.REPLACE, false, today);
                quota.commit(AndroidSaveFile.at(normal), payload, PuzzleSave.Edit.FINISH, false, today);
            }
        });
        byte[] normalBefore = Files.readAllBytes(normal.toPath());
        assertTrue(settings().edit().putLong("responsibleModeSince", today.toEpochDay()).commit());
        Map<String, ?> before = settings().getAll();
        startLink(link(false));
        assertTrue(sharedFile(false).isFile());
        assertEquals(9, quota.finishedToday(today));
        getInstrumentation().runOnMainSync(() -> {
            Game game = game();
            System.arraycopy(game.solution, 0, game.values, 0, 81);
            invoke("changed");
        });
        NativeSaveAwaiter.await(getInstrumentation(), activity);
        assertTrue(game().won());
        assertEquals(10, quota.finishedToday(today));
        close();
        startLink(link(false));
        assertTrue(game().won());
        assertEquals(10, quota.finishedToday(today));
        close();
        startLink(link(true));
        assertFalse(game().won());
        assertFalse(sharedFile(true).exists());
        close();
        assertFalse(sharedFile(true).exists());
        assertEquals(before, settings().getAll());
        assertTrue(Arrays.equals(normalBefore, Files.readAllBytes(normal.toPath())));
    }

    public void testSharesheetUsesStartingGivensOnly() throws Exception {
        Game edited = SudokuShare.open(link(true));
        int target = 0; while (edited.clues[target] != 0) target++;
        edited.selected = target;
        edited.enter(edited.solution[target]);
        edited.notes[target] = Sudoku.ALL;
        edited.elapsedMillis = 987654;
        Files.write(file("current-game.bin").toPath(), encode(edited));
        activity = getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        NativeSaveAwaiter.await(getInstrumentation(), activity);
        getInstrumentation().runOnMainSync(() -> {
            View sudoku = findText(activity.getWindow().getDecorView(), "Sudoku");
            assertNotNull(sudoku); assertTrue(sudoku.performClick());
        });
        NativeSaveAwaiter.await(getInstrumentation(), activity);
        assertEquals(activity.getString(R.string.share_puzzle_message, "Sudoku", link(true)), shareText());
    }

    public void testCompletedSudokuSharesRecordedTimeOutsideLink() throws Exception {
        startCompletedSharedSudoku(154321);

        String text = shareText();
        assertEquals("This puzzle took me " + ActivePlayTimer.label(154321) + "!  " + link(false), text);
        String pasted = LocalShareLink.manualTransport(text, BuildConfig.PUZZLE_LINK_SCHEME);
        Game recipient = SudokuShare.open(LocalShareLink.fromTransport(pasted, BuildConfig.PUZZLE_LINK_SCHEME));
        assertTrue(Arrays.equals(clues(false), recipient.values));
        assertEquals(0L, recipient.elapsedMillis);
    }

    public void testCompletedSudokuWithoutRecordedTimeKeepsInvitation() throws Exception {
        startCompletedSharedSudoku(0);
        assertEquals(activity.getString(R.string.share_puzzle_message, "Sudoku", link(false)), shareText());
    }

    private void startCompletedSharedSudoku(long elapsedMillis) throws Exception {
        Game solved = SudokuShare.open(link(false));
        System.arraycopy(solved.solution, 0, solved.values, 0, solved.values.length);
        solved.elapsedMillis = elapsedMillis;
        Files.write(sharedFile(false).toPath(), encode(solved));
        startLink(link(false));
        assertTrue(game().won());
    }

    private String shareText() {
        Intent[] chooser = {null};
        Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor() {
            @Override public Instrumentation.ActivityResult onStartActivity(Intent intent) {
                // Some Android vendors substitute their own Sharesheet action.
                Intent nested = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (nested == null || !Intent.ACTION_SEND.equals(nested.getAction())) return null;
                chooser[0] = intent;
                return new Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null);
            }
        };
        getInstrumentation().addMonitor(monitor);
        try {
            getInstrumentation().runOnMainSync(() -> {
                assertNotNull("The sender must have a committed game", game());
                View share = findText(activity.getWindow().getDecorView(), activity.getString(R.string.share_puzzle_local));
                assertNotNull(share); assertTrue(share.performClick());
            });
            NativeSaveAwaiter.await(getInstrumentation(), activity);
            assertNotNull("Sharing must launch the Android chooser", chooser[0]);
            Intent send = chooser[0].getParcelableExtra(Intent.EXTRA_INTENT);
            assertNotNull(send);
            assertEquals(Intent.ACTION_SEND, send.getAction());
            assertEquals("text/plain", send.getType());
            return send.getStringExtra(Intent.EXTRA_TEXT);
        } finally { getInstrumentation().removeMonitor(monitor); }
    }

    public void testInvalidLinkHasNoGameAndDoesNotTouchNormalSave() throws Exception {
        byte[] normal = Files.readAllBytes(file("current-game.bin").toPath());
        assertNull("Unsupported versions are not advertised", getInstrumentation().getTargetContext()
                .getPackageManager().resolveActivity(viewIntent(link(false).replace("/v1/", "/v9/")), 0));
        startLink(link(false) + "?invalid");
        assertNull(game());
        assertFalse(sharedFile(false).exists());
        assertTrue(Arrays.equals(normal, Files.readAllBytes(file("current-game.bin").toPath())));
        close();
        byte[] wrongPuzzle = encode(SudokuShare.open(link(true)));
        Files.write(sharedFile(false).toPath(), wrongPuzzle);
        startLink(link(false));
        assertNull("A mismatched shared save must not become a playable game", game());
        assertTrue(Arrays.equals(wrongPuzzle, Files.readAllBytes(sharedFile(false).toPath())));
        assertTrue(Arrays.equals(normal, Files.readAllBytes(file("current-game.bin").toPath())));
    }

    private Intent viewIntent(String link) {
        return new Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(getInstrumentation().getTargetContext().getPackageName());
    }
    private void startLink(String link) {
        Intent intent = viewIntent(link).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        assertEquals(MainActivity.class.getName(),
                getInstrumentation().getTargetContext().getPackageManager().resolveActivity(intent, 0).activityInfo.name);
        activity = getInstrumentation().startActivitySync(intent);
        NativeSaveAwaiter.await(getInstrumentation(), activity);
    }
    private void close() {
        if (activity == null || activity.isDestroyed()) return;
        NativeSaveAwaiter.await(getInstrumentation(), activity);
        getInstrumentation().runOnMainSync(() -> activity.finish());
        getInstrumentation().waitForIdleSync();
        try { SaveQueue.forPath(sharedFile(false).getAbsolutePath()).flushBlocking();
            SaveQueue.forPath(sharedFile(true).getAbsolutePath()).flushBlocking();
            SaveQueue.forPath(file("current-game.bin").getAbsolutePath()).flushBlocking();
        } catch (java.io.IOException ex) { throw new AssertionError(ex); }
        activity = null;
    }
    private Game game() {
        try {
            Field field = MainActivity.class.getDeclaredField("game"); field.setAccessible(true);
            return (Game) field.get(activity);
        } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
    private void invoke(String name) {
        try {
            Method method = MainActivity.class.getDeclaredMethod(name); method.setAccessible(true); method.invoke(activity);
        } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
    private static byte[] encode(Game game) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        game.write(new DataOutputStream(bytes));
        return bytes.toByteArray();
    }
    private void capture(String name) throws Exception {
        android.os.SystemClock.sleep(500);
        getInstrumentation().waitForIdleSync();
        android.graphics.Bitmap screenshot = getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(screenshot);
        try (java.io.FileOutputStream stream = new java.io.FileOutputStream(
                new File(getInstrumentation().getTargetContext().getExternalFilesDir(null), name))) {
            assertTrue(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream));
        }
        screenshot.recycle();
    }
    private static View findText(View view, String text) {
        CharSequence description = view.getContentDescription();
        if (description != null && text.contentEquals(description)) return view;
        if (view instanceof TextView label && text.equals(label.getText().toString())) return view;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            View found = findText(group.getChildAt(i), text);
            if (found != null) return found;
        }
        return null;
    }
}
