package com.quietgrid.sudoku;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import com.quietgrid.sudoku.puzzles.*;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** Validation-only fault fixtures require the same outer durable backup as the other suites. */
@SuppressWarnings("deprecation")
public final class PersistenceDeviceTests extends InstrumentationTestCase {
    private Activity activity;
    private int failureSync = 2;
    private final Map<File, byte[]> originals = new LinkedHashMap<>();
    private Map<String, ?> preferences;
    private File file(String name) { return new File(getInstrumentation().getTargetContext().getFilesDir(), name); }
    @Override protected void setUp() throws Exception {
        super.setUp();
        assertEquals("fyi.mllab.puzzle.validation", getInstrumentation().getTargetContext().getPackageName());
        preferences = getInstrumentation().getTargetContext().getSharedPreferences("settings", 0).getAll();
        for (String name : new String[]{"current-game.bin", "daily-word-v1.bin", "puzzles/takuzu-v1.bin"})
            for (String suffix : new String[]{"", ".bak", ".new"}) {
                File target = file(name + suffix);
                originals.put(target, target.exists() ? Files.readAllBytes(target.toPath()) : null);
                if (target.exists()) Files.delete(target.toPath());
            }
        assertTrue(getInstrumentation().getTargetContext().getSharedPreferences("settings", 0).edit().clear().commit());
        clearBestTimeCache();
    }
    @Override protected void tearDown() throws Exception {
        try {
            close();
            for (Map.Entry<File, byte[]> entry : originals.entrySet()) {
                if (entry.getValue() == null) Files.deleteIfExists(entry.getKey().toPath());
                else Files.write(entry.getKey().toPath(), entry.getValue());
            }
            SharedPreferences.Editor edit = getInstrumentation().getTargetContext().getSharedPreferences("settings", 0).edit().clear();
            for (Map.Entry<String, ?> entry : preferences.entrySet()) {
                Object value = entry.getValue(); String key = entry.getKey();
                if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
                else if (value instanceof Integer) edit.putInt(key, (Integer) value);
                else if (value instanceof Long) edit.putLong(key, (Long) value);
                else if (value instanceof String) edit.putString(key, (String) value);
                else if (value instanceof Float) edit.putFloat(key, (Float) value);
                else if (value instanceof Set<?>) {
                    Set<String> strings = new HashSet<>();
                    for (Object item : (Set<?>) value) {
                        if (!(item instanceof String)) throw new AssertionError("Invalid string preference");
                        strings.add((String) item);
                    }
                    edit.putStringSet(key, strings);
                } else throw new AssertionError("Unexpected preference type; outer backup required");
            }
            assertTrue(edit.commit());
            clearBestTimeCache();
        } finally { super.tearDown(); }
    }
    public void testFinalCommitFailuresDoNotPublishAndCanRetry() throws Throwable {
        Game fixture = new Game(Sudoku.generate(Sudoku.Difficulty.FOCUSED, new Random(41000)));
        System.arraycopy(fixture.solution, 0, fixture.values, 0, 81);
        int cell = 0; while (fixture.clues[cell] != 0) cell++;
        fixture.values[cell] = 0;
        put("current-game.bin", encode(fixture));
        start(MainActivity.class, new Intent());
        runTestOnUiThread(() -> {
            Object screen = field(activity, "screen");
            for (Object value : screen.getClass().getEnumConstants())
                if ("PLAY".equals(value.toString())) setField(activity, "screen", value);
            invoke(activity, "render", new Class<?>[]{boolean.class}, false);
            activity.onWindowFocusChanged(false);
        });
        byte[] beforeBytes = Files.readAllBytes(file("current-game.bin").toPath());
        failFinalCommit();
        int target = cell;
        runTestOnUiThread(() -> {
            Game game = (Game) field(activity, "game");
            game.selected = target; assertTrue(game.enter(game.solution[target]));
            invoke(activity, "changed");
            assertFalse(((Game) field(activity, "game")).won());
        });
        NativeSaveAwaiter.await(getInstrumentation(), activity);
        assertFalse(((Game) field(activity, "game")).won());
        assertTrue(Arrays.equals(beforeBytes, Files.readAllBytes(file("current-game.bin").toPath())));
        assertFalse(getInstrumentation().getTargetContext().getSharedPreferences("settings", 0).contains("bestTime.FOCUSED"));
        restoreWriter();
        runTestOnUiThread(() -> {
            Game game = (Game) field(activity, "game");
            game.selected = target; assertTrue(game.enter(game.solution[target]));
            invoke(activity, "changed");
        });
        NativeSaveAwaiter.await(getInstrumentation(), activity);
        assertTrue(((Game) field(activity, "game")).won());
        assertTrue(getInstrumentation().getTargetContext().getSharedPreferences("settings", 0).contains("bestTime.FOCUSED"));
        close();

        Takuzu takuzu = Takuzu.generate(6, new Random(27), new Generation());
        PuzzleProgress progress = new PuzzleProgress(takuzu);
        put("puzzles/takuzu-v1.bin", PuzzleStore.encode(progress));
        start(PuzzleActivity.class, new Intent().putExtra(PuzzleActivity.MODE, "TAKUZU"));
        waitReady();
        runTestOnUiThread(() -> ((PuzzleActivity) activity).playTime.setRunning(false));
        int editable = 0; while (takuzu.clues[editable] >= 0) editable++;
        int square = editable;
        failFinalCommit();
        runTestOnUiThread(() -> invoke(activity, "cell", new Class<?>[]{int.class}, square));
        NativeSaveAwaiter.await(getInstrumentation(), activity);
        assertEquals(-1, ((Takuzu) field(activity, "game")).values[square]);
        assertEquals(0, ((PuzzleProgress) field(activity, "progress")).undoCount());
        restoreWriter();
        runTestOnUiThread(() -> invoke(activity, "cell", new Class<?>[]{int.class}, square));
        NativeSaveAwaiter.await(getInstrumentation(), activity);
        assertEquals(0, ((Takuzu) field(activity, "game")).values[square]);
        assertEquals(1, ((PuzzleProgress) field(activity, "progress")).undoCount());
        close();

        start(DailyWordActivity.class, new Intent());
        DailyWord beforeWord = new DailyWord((DailyWord) field(activity, "game"));
        runTestOnUiThread(() -> {
            ((DailyWordActivity) activity).playTime.setRunning(false);
            invoke(activity, "checkpointTime");
        });
        SaveQueue.forPath(file("daily-word-v1.bin").getAbsolutePath()).flushBlocking();
        getInstrumentation().waitForIdleSync();
        byte[] wordBytes = Files.readAllBytes(file("daily-word-v1.bin").toPath());
        failFinalCommit();
        runTestOnUiThread(() -> invoke(activity, "input", new Class<?>[]{char.class, boolean.class, boolean.class}, 'a', false, false));
        NativeSaveAwaiter.await(getInstrumentation(), activity);
        java.time.LocalDate date = (java.time.LocalDate) field(activity, "date");
        assertEquals(beforeWord.day(date).draft(), ((DailyWord) field(activity, "game")).day(date).draft());
        assertTrue(Arrays.equals(wordBytes, Files.readAllBytes(file("daily-word-v1.bin").toPath())));
        restoreWriter();
        runTestOnUiThread(() -> invoke(activity, "input", new Class<?>[]{char.class, boolean.class, boolean.class}, 'a', false, false));
        NativeSaveAwaiter.await(getInstrumentation(), activity);
        assertEquals("a", ((DailyWord) field(activity, "game")).day(date).draft());
        close();
    }
    public void testFailedRolloverBlocksOldDateInputAndRetries() throws Throwable {
        start(DailyWordActivity.class, new Intent());
        java.time.LocalDate old = (java.time.LocalDate) field(activity, "date");
        runTestOnUiThread(() -> invoke(activity, "input", new Class<?>[]{char.class, boolean.class, boolean.class}, 'a', false, false));
        NativeSaveAwaiter.await(getInstrumentation(), activity);
        failFinalCommit();
        runTestOnUiThread(() -> {
            DailyWordActivity word = (DailyWordActivity) activity;
            word.testZone = java.time.ZoneOffset.UTC;
            word.clock = java.time.Clock.fixed(old.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
                    java.time.ZoneOffset.UTC);
            invoke(activity, "input", new Class<?>[]{char.class, boolean.class, boolean.class}, 'b', false, false);
        });
        NativeSaveAwaiter.await(getInstrumentation(), activity);
        assertEquals(old, field(activity, "date"));
        assertEquals("a", ((DailyWord) field(activity, "game")).day(old).draft());
        restoreWriter();
        runTestOnUiThread(() -> assertTrue(((DailyWordActivity) activity).checkDate()));
        NativeSaveAwaiter.await(getInstrumentation(), activity);
        assertEquals(old.plusDays(1), field(activity, "date"));
        assertEquals("a", ((DailyWord) field(activity, "game")).day(old).draft());
        assertEquals("", ((DailyWord) field(activity, "game")).day(old.plusDays(1)).draft());
    }
    public void testFinalBackupRetirementFailureAcrossWriters() throws Throwable {
        failureSync = 3;
        testFinalCommitFailuresDoNotPublishAndCanRetry();
    }
    public void testOversizedRejectedSaveConfirmedReplacementRollsBackAndRetries() throws Throwable {
        File oversized = file("puzzles/takuzu-v1.bin");
        Files.createDirectories(oversized.getParentFile().toPath());
        byte[] block = new byte[8192];
        for (int i = 0; i < block.length; i++) block[i] = (byte) i;
        try (FileOutputStream out = new FileOutputStream(oversized)) {
            for (int i = 0; i < 8192; i++) out.write(block);
            out.getFD().sync();
        }
        assertEquals(64L * 1024 * 1024, oversized.length());
        byte[] originalDigest = sha256(oversized);
        start(PuzzleActivity.class, new Intent().putExtra(PuzzleActivity.MODE, "TAKUZU"));
        assertEquals(true, field(activity, "loadFailed"));
        assertNull(field(activity, "game"));
        failureSync = 3;
        failFinalCommit();
        runTestOnUiThread(() -> {
            setField(activity, "newSetting", 6);
            invoke(activity, "requestNew");
        });
        assertTrue(Arrays.equals(originalDigest, sha256(oversized)));
        confirmReplacement();
        awaitReplacementAttempt();
        assertNull("Failed replacement must not publish the generated board", field(activity, "game"));
        assertEquals(true, field(activity, "generationFailed"));
        assertEquals(true, field(activity, "newRequested"));
        assertEquals(64L * 1024 * 1024, oversized.length());
        assertTrue("Rollback restores every oversized byte", Arrays.equals(originalDigest, sha256(oversized)));
        restoreWriter();
        runTestOnUiThread(() -> assertTrue(((android.view.View) field(activity, "retry")).performClick()));
        confirmReplacement();
        awaitReplacementAttempt();
        assertNotNull(field(activity, "game"));
        assertEquals(false, field(activity, "newRequested"));
        assertEquals(false, field(activity, "loadFailed"));
        assertTrue("Accepted replacement is bounded", oversized.length() <= PuzzleStore.MAX_SAVE_BYTES);
        assertEquals(6, PuzzleActivity.readProgress(getInstrumentation().getTargetContext(), Mode.TAKUZU).game.size());
    }
    private void confirmReplacement() {
        for (int attempt = 0; attempt < 30; attempt++) {
            getInstrumentation().waitForIdleSync();
            android.view.accessibility.AccessibilityNodeInfo root =
                    getInstrumentation().getUiAutomation().getRootInActiveWindow();
            if (root != null) try {
                for (android.view.accessibility.AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText("Replace")) {
                    try {
                        if ("Replace".equalsIgnoreCase(node.getText() == null ? "" : node.getText().toString())
                                && node.isClickable()) {
                            android.graphics.Rect bounds = new android.graphics.Rect();
                            node.getBoundsInScreen(bounds);
                            long time = SystemClock.uptimeMillis();
                            android.view.MotionEvent down = android.view.MotionEvent.obtain(time, time,
                                    android.view.MotionEvent.ACTION_DOWN, bounds.centerX(), bounds.centerY(), 0);
                            android.view.MotionEvent up = android.view.MotionEvent.obtain(time, time + 60,
                                    android.view.MotionEvent.ACTION_UP, bounds.centerX(), bounds.centerY(), 0);
                            try {
                                getInstrumentation().sendPointerSync(down);
                                getInstrumentation().sendPointerSync(up);
                            } finally { down.recycle(); up.recycle(); }
                            getInstrumentation().waitForIdleSync();
                            return;
                        }
                    } finally { node.recycle(); }
                }
            } finally { root.recycle(); }
            SystemClock.sleep(100);
        }
        fail("Explicit Replace action missing");
    }
    private void awaitReplacementAttempt() {
        long deadline = SystemClock.uptimeMillis() + 20000;
        while (SystemClock.uptimeMillis() < deadline) {
            boolean[] complete = {false};
            getInstrumentation().runOnMainSync(() -> complete[0] = Boolean.FALSE.equals(field(activity, "busy"))
                    && ((SaveSession<?>) field(activity, "saveSession")).idle());
            if (complete[0]) {
                NativeSaveAwaiter.await(getInstrumentation(), activity);
                return;
            }
            SystemClock.sleep(50);
        }
        fail("Replacement attempt did not finish");
    }
    private static byte[] sha256(File file) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return digest.digest();
    }
    public void testConfirmedReplacementSurvivesRecreationWithOldBoard() throws Throwable {
        put("puzzles/takuzu-v1.bin", PuzzleStore.encode(Takuzu.generate(8, new Random(21), new Generation())));
        start(PuzzleActivity.class, new Intent().putExtra(PuzzleActivity.MODE, "TAKUZU"));
        waitReady();
        ExecutorService worker = (ExecutorService) field(activity, "worker");
        CountDownLatch blocked = new CountDownLatch(1), release = new CountDownLatch(1);
        worker.submit(() -> {
            blocked.countDown();
            try { release.await(); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        });
        assertTrue(blocked.await(5, TimeUnit.SECONDS));
        Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(PuzzleActivity.class.getName(), null, false);
        try {
            runTestOnUiThread(() -> {
                setField(activity, "newSetting", 6);
                invoke(activity, "generate");
                assertEquals(true, field(activity, "newRequested"));
                activity.recreate();
            });
            Activity replacement = getInstrumentation().waitForMonitorWithTimeout(monitor, 10000);
            assertNotNull(replacement); activity = replacement;
            release.countDown(); waitReady();
            assertEquals(6, ((Puzzle) field(activity, "game")).size());
            assertEquals(false, field(activity, "newRequested"));
            assertEquals(6, PuzzleActivity.readProgress(getInstrumentation().getTargetContext(), Mode.TAKUZU).game.size());
        } finally { release.countDown(); getInstrumentation().removeMonitor(monitor); }
    }
    public void testSettingsOnlyNeverOpensOrWritesSudoku() throws Exception {
        byte[] corrupt = {1, 2, 3};
        put("current-game.bin", corrupt);
        assertTrue(getInstrumentation().getTargetContext().getSharedPreferences("settings", 0).edit()
                .putInt("accentColor", 3).commit());
        Map<String, ?> before = getInstrumentation().getTargetContext().getSharedPreferences("settings", 0).getAll();
        start(MainActivity.class, new Intent().putExtra(MainActivity.OPEN_SETTINGS, true)
                .putExtra(MainActivity.SETTINGS_GAME, "MINESWEEPER"));
        assertNull(field(activity, "game"));
        assertEquals(before, getInstrumentation().getTargetContext().getSharedPreferences("settings", 0).getAll());
        close();
        assertTrue(Arrays.equals(corrupt, Files.readAllBytes(file("current-game.bin").toPath())));
    }
    public void testStalledSaveKeepsUiResponsiveAndQueuedEditsSurviveRecreation() throws Throwable {
        Takuzu fixture = Takuzu.generate(6, new Random(27), new Generation());
        put("puzzles/takuzu-v1.bin", PuzzleStore.encode(fixture));
        start(PuzzleActivity.class, new Intent().putExtra(PuzzleActivity.MODE, "TAKUZU"));
        int[] targets = new int[2]; int count = 0;
        for (int cell = 0; cell < fixture.values.length && count < 2; cell++)
            if (fixture.clues[cell] < 0) targets[count++] = cell;
        assertEquals(2, count);
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        setField(activity, "saveFileFactory", (java.util.function.Function<File, DurableFile>) target -> {
            DurableFile.Operations delegate = AndroidSaveFile.operations();
            return new DurableFile(target, new DurableFile.Operations() {
                @Override public void copy(File source, File destination) throws IOException {
                    started.countDown();
                    try { if (!release.await(10, TimeUnit.SECONDS)) throw new IOException("Native stall timed out"); }
                    catch (InterruptedException ex) { throw new IOException(ex); }
                    delegate.copy(source, destination);
                }
                @Override public void write(File file, byte[] bytes) throws IOException { delegate.write(file, bytes); }
                @Override public void replace(File from, File to) throws IOException { delegate.replace(from, to); }
                @Override public void delete(File file) throws IOException { delegate.delete(file); }
                @Override public void syncDirectory(File directory) throws IOException { delegate.syncDirectory(directory); }
            });
        });
        Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(PuzzleActivity.class.getName(), null, false);
        try {
            long before = SystemClock.uptimeMillis();
            runTestOnUiThread(() -> invoke(activity, "cell", new Class<?>[]{int.class}, targets[0]));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            runTestOnUiThread(() -> {
                assertEquals(-1, ((Takuzu) field(activity, "game")).values[targets[0]]);
                assertEquals(0, ((PuzzleProgress) field(activity, "progress")).undoCount());
                invoke(activity, "cell", new Class<?>[]{int.class}, targets[1]);
                invoke(activity, "undo");
                activity.recreate();
            });
            Activity recreated = getInstrumentation().waitForMonitorWithTimeout(monitor, 5000);
            assertNotNull("Recreation proceeds while disk is still stalled", recreated);
            activity = recreated;
            assertTrue("Input and lifecycle remain responsive", SystemClock.uptimeMillis() - before < 3000);
            release.countDown(); NativeSaveAwaiter.await(getInstrumentation(), activity);
            Takuzu result = (Takuzu) field(activity, "game");
            assertEquals(0, result.values[targets[0]]);
            assertEquals(-1, result.values[targets[1]]);
            assertEquals(1, ((PuzzleProgress) field(activity, "progress")).undoCount());
        } finally {
            release.countDown(); restoreWriter(); getInstrumentation().removeMonitor(monitor);
        }
    }
    private void start(Class<? extends Activity> type, Intent extras) {
        Intent intent = new Intent(getInstrumentation().getTargetContext(), type);
        intent.putExtras(extras).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity = getInstrumentation().startActivitySync(intent);
        getInstrumentation().waitForIdleSync();
        NativeSaveAwaiter.await(getInstrumentation(), activity);
    }
    private void waitReady() {
        for (int i = 0; i < 200; i++) {
            getInstrumentation().waitForIdleSync();
            if (Boolean.FALSE.equals(field(activity, "busy")) && field(activity, "game") != null
                    && ((SaveSession<?>) field(activity, "saveSession")).idle()) return;
            SystemClock.sleep(50);
        }
        fail("Puzzle did not finish opening/generating");
    }
    private void close() {
        if (activity != null && !activity.isDestroyed()) {
            NativeSaveAwaiter.await(getInstrumentation(), activity);
            getInstrumentation().runOnMainSync(activity::finish);
            getInstrumentation().waitForIdleSync();
            for (String name : new String[]{"current-game.bin", "daily-word-v1.bin", "puzzles/takuzu-v1.bin"})
                try { SaveQueue.forPath(file(name).getAbsolutePath()).flushBlocking(); }
                catch (IOException ex) { throw new AssertionError(ex); }
        }
        activity = null;
    }
    private void put(String name, byte[] bytes) throws Exception {
        Files.createDirectories(file(name).getParentFile().toPath()); Files.write(file(name).toPath(), bytes);
    }
    private byte[] encode(Game game) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); game.write(new DataOutputStream(bytes)); return bytes.toByteArray();
    }
    private void failFinalCommit() {
        setField(activity, "saveFileFactory", (java.util.function.Function<File, DurableFile>) target -> {
            DurableFile.Operations delegate = AndroidSaveFile.operations();
            return new DurableFile(target, new DurableFile.Operations() {
                private int syncs;
                @Override public void write(File file, byte[] bytes) throws IOException { delegate.write(file, bytes); }
                @Override public void copy(File source, File target) throws IOException { delegate.copy(source, target); }
                @Override public void replace(File from, File to) throws IOException { delegate.replace(from, to); }
                @Override public void delete(File file) throws IOException { delegate.delete(file); }
                @Override public void syncDirectory(File directory) throws IOException {
                    delegate.syncDirectory(directory);
                    if (++syncs == failureSync) throw new IOException("Injected directory sync failure at stage " + failureSync);
                }
            });
        });
    }
    private void restoreWriter() { setField(activity, "saveFileFactory", (java.util.function.Function<File, DurableFile>) AndroidSaveFile::at); }
    private static Object field(Object target, String name) {
        try { Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
        catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
    private static void clearBestTimeCache() {
        try {
            Field field = MainActivity.class.getDeclaredField("bestTimes"); field.setAccessible(true);
            ((Map<?, ?>) field.get(null)).clear();
        } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
    private static void setField(Object target, String name, Object value) {
        try { Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value); }
        catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
    private static Object invoke(Object target, String name) { return invoke(target, name, new Class<?>[0]); }
    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) {
        try { Method method = target.getClass().getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(target, args); }
        catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
}
