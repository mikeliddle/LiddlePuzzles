package com.quietgrid.sudoku;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.net.Uri;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import com.quietgrid.sudoku.puzzles.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.*;
import java.util.*;
import java.util.function.Function;

/** Exercises native Share -> exported VIEW router -> separate durable recipient state. */
@SuppressWarnings("deprecation")
public final class MultiSharingDeviceTests extends InstrumentationTestCase {
    private Activity activity;
    private final Map<File, byte[]> originals = new LinkedHashMap<>();
    private Map<String, ?> settings;

    @Override protected void setUp() throws Exception {
        super.setUp();
        assertEquals("fyi.mllab.puzzle.validation", getInstrumentation().getTargetContext().getPackageName());
        settings = getInstrumentation().getTargetContext().getSharedPreferences("settings", 0).getAll();
    }
    @Override protected void tearDown() throws Exception {
        try {
            close();
            for (Map.Entry<File, byte[]> entry : originals.entrySet()) {
                if (entry.getValue() == null) Files.deleteIfExists(entry.getKey().toPath());
                else Files.write(entry.getKey().toPath(), entry.getValue());
            }
            assertEquals("Sharing never changes normal preferences or records", settings,
                    getInstrumentation().getTargetContext().getSharedPreferences("settings", 0).getAll());
        } finally { super.tearDown(); }
    }

    public void testTakuzuShareOpenResume() throws Exception { puzzle(Mode.TAKUZU); }
    public void testNonogramShareOpenResume() throws Exception { puzzle(Mode.NONOGRAM); }
    public void testFutoshikiShareOpenResume() throws Exception { puzzle(Mode.FUTOSHIKI); }
    public void testNumberlinkShareOpenResume() throws Exception { puzzle(Mode.NUMBERLINK); }
    public void testSlitherlinkShareOpenResume() throws Exception { puzzle(Mode.SLITHERLINK); }
    public void testNonogramLargeShareOpenResume() throws Exception { puzzle(Mode.NONOGRAM, 10); }
    public void testSlitherlinkLargeShareOpenResume() throws Exception { puzzle(Mode.SLITHERLINK, 8); }

    private void puzzle(Mode mode) throws Exception {
        puzzle(mode, mode.defaultSetting());
    }
    private void puzzle(Mode mode, int setting) throws Exception {
        Puzzle fixture = mode.generate(setting, new Random(8400 + mode.ordinal()), new Generation());
        String link = PuzzleShare.link(fixture);
        PuzzleProgress sender = new PuzzleProgress(fixture);
        Puzzle moved = fixture.copy(); move(moved);
        sender = sender.edited(moved).withElapsed(43210);
        File normalFile = file("puzzles/" + PuzzleShare.kind(mode) + "-v1.bin");
        prepare(normalFile);
        if (!normalFile.getParentFile().exists()) assertTrue(normalFile.getParentFile().mkdirs());
        byte[] normal = PuzzleStore.encode(sender);
        Files.write(normalFile.toPath(), normal);
        File recipient = sharedFile(link, mode);
        prepare(recipient);
        start(PuzzleActivity.class, new Intent().putExtra(PuzzleActivity.MODE, mode.name()).putExtra(PuzzleActivity.FROM_MENU, true));
        String sent = share();
        assertEquals("Share transmits only starting rules", link, sent);
        close();
        normal = Files.readAllBytes(normalFile.toPath());
        route(sent, PuzzleActivity.class);
        assertTrue((Boolean) field("shared"));
        assertEquals(link, PuzzleShare.link((Puzzle) field("game")));
        assertTrue(Arrays.equals(((UndoablePuzzle) fixture).entries(), ((UndoablePuzzle) field("game")).entries()));
        assertEquals(0, ((PuzzleProgress) field("progress")).undoCount());
        capture("shared-" + PuzzleShare.kind(mode) + ".png");
        editPuzzle();
        int[] entries = ((UndoablePuzzle) field("game")).entries().clone();
        assertEquals(1, ((PuzzleProgress) field("progress")).undoCount());
        recreate();
        assertTrue(Arrays.equals(entries, ((UndoablePuzzle) field("game")).entries()));
        capture("shared-" + PuzzleShare.kind(mode) + "-rotated.png");
        getInstrumentation().runOnMainSync(() -> ViewportChecks.all(activity));
        close();
        route(sent, PuzzleActivity.class);
        assertTrue(Arrays.equals(entries, ((UndoablePuzzle) field("game")).entries()));
        assertEquals(1, ((PuzzleProgress) field("progress")).undoCount());
        getInstrumentation().runOnMainSync(() -> invoke("undo", new Class<?>[0]));
        NativeSaveAwaiter.await(getInstrumentation(), activity);
        assertTrue(Arrays.equals(((UndoablePuzzle) fixture).entries(), ((UndoablePuzzle) field("game")).entries()));
        assertTrue(Arrays.equals(normal, Files.readAllBytes(normalFile.toPath())));
        assertTrue(recipient.isFile());

        Puzzle other = mode.generate(setting, new Random(8450 + mode.ordinal()), new Generation());
        String otherLink = PuzzleShare.link(other);
        prepare(sharedFile(otherLink, mode));
        warm(new Intent(Intent.ACTION_VIEW, Uri.parse(otherLink)).putExtra(PuzzleActivity.MODE, mode.name()), PuzzleActivity.class);
        assertEquals(otherLink, PuzzleShare.link((Puzzle) field("game")));
        assertTrue(Arrays.equals(normal, Files.readAllBytes(normalFile.toPath())));
        close();
        byte[] wrong = PuzzleStore.encode(other);
        Files.write(recipient.toPath(), wrong);
        route(link, PuzzleActivity.class);
        assertNull("Mismatched shared progress is not opened or overwritten", field("game"));
        assertTrue((Boolean) field("loadFailed"));
        assertTrue(Arrays.equals(wrong, Files.readAllBytes(recipient.toPath())));
        assertTrue(Arrays.equals(normal, Files.readAllBytes(normalFile.toPath())));
    }

    public void testDailyWordSharePinnedDateAndSeparateHistory() throws Exception {
        DailyWord fixture = word();
        LocalDate today = LocalDate.now();
        fixture.letter(fixture.day(today), 'a');
        fixture.day(today).setElapsed(43210);
        fixture.letter(fixture.day(today.minusDays(1)), 'b');
        File normalFile = file("daily-word-v1.bin");
        prepare(normalFile); Files.write(normalFile.toPath(), encode(fixture));
        start(DailyWordActivity.class, new Intent());
        String sent = share();
        assertEquals(WordShare.link(fixture, today), sent);
        close();
        byte[] normal = Files.readAllBytes(normalFile.toPath());
        File recipient = wordFile(sent); prepare(recipient);
        route(sent, DailyWordActivity.class);
        assertEquals(today, field("date"));
        DailyWord imported = (DailyWord) field("game");
        assertEquals("", imported.day(today).draft());
        assertEquals(0, imported.day(today).guesses().size());
        capture("shared-daily-word.png");
        getInstrumentation().runOnMainSync(() -> {
            DailyWordActivity wordActivity = (DailyWordActivity) activity;
            wordActivity.clock = Clock.fixed(today.plusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
            wordActivity.testZone = ZoneOffset.UTC;
            assertFalse("Shared Word does not roll over at midnight", wordActivity.checkDate());
            invoke("input", new Class<?>[]{char.class, boolean.class, boolean.class}, 'z', false, false);
        });
        NativeSaveAwaiter.await(getInstrumentation(), activity);
        assertEquals(today, field("date"));
        assertEquals("z", ((DailyWord) field("game")).day(today).draft());
        recreate();
        assertEquals(today, field("date"));
        assertEquals("z", ((DailyWord) field("game")).day(today).draft());
        capture("shared-daily-word-rotated.png");
        getInstrumentation().runOnMainSync(() -> ViewportChecks.all(activity));
        close();
        route(sent, DailyWordActivity.class);
        assertEquals("z", ((DailyWord) field("game")).day(today).draft());
        assertTrue(Arrays.equals(normal, Files.readAllBytes(normalFile.toPath())));
        String other = WordShare.link(word(), today.minusDays(20));
        prepare(wordFile(other));
        warm(new Intent(Intent.ACTION_VIEW, Uri.parse(other)), DailyWordActivity.class);
        assertEquals(today.minusDays(20), field("date"));
        assertEquals("", ((DailyWord) field("game")).day(today.minusDays(20)).draft());
        assertTrue(Arrays.equals(normal, Files.readAllBytes(normalFile.toPath())));
        close();
        byte[] payload = LocalShareLink.decode(sent, WordShare.KIND); payload[8] ^= 1;
        String unknown = LocalShareLink.encode(WordShare.KIND, payload);
        File unknownFile = wordFile(unknown); prepare(unknownFile);
        route(unknown, DailyWordActivity.class);
        assertNull("An unknown frozen schedule is not silently replaced", field("game"));
        assertFalse(unknownFile.exists());
        assertTrue(Arrays.equals(normal, Files.readAllBytes(normalFile.toPath())));
        close();
        DailyWord wrongDate = word();
        wrongDate.day(today.plusDays(1));
        byte[] wrongBytes = encode(wrongDate);
        Files.write(recipient.toPath(), wrongBytes);
        route(sent, DailyWordActivity.class);
        assertNull("A different date's shared progress is not adopted", field("game"));
        assertTrue(Arrays.equals(wrongBytes, Files.readAllBytes(recipient.toPath())));
        assertTrue(Arrays.equals(normal, Files.readAllBytes(normalFile.toPath())));
    }

    private File file(String name) { return new File(getInstrumentation().getTargetContext().getFilesDir(), name); }
    private File sharedFile(String link, Mode mode) {
        return file("shared-" + PuzzleShare.kind(mode) + "-" + LocalShareLink.saveKey(link, PuzzleShare.kind(mode)) + ".bin");
    }
    private File wordFile(String link) { return file("shared-word-" + LocalShareLink.saveKey(link, WordShare.KIND) + ".bin"); }
    private void prepare(File file) throws Exception {
        for (String suffix : new String[]{"", ".bak", ".new"}) {
            File target = new File(file.getPath() + suffix);
            if (!originals.containsKey(target)) originals.put(target, target.exists() ? Files.readAllBytes(target.toPath()) : null);
            Files.deleteIfExists(target.toPath());
        }
    }
    private void start(Class<? extends Activity> type, Intent intent) {
        activity = getInstrumentation().startActivitySync(intent.setClass(getInstrumentation().getTargetContext(), type)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        NativeSaveAwaiter.await(getInstrumentation(), activity);
        assertNotNull(field("game"));
    }
    private String share() {
        Intent[] chooser = {null};
        Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor() {
            @Override public Instrumentation.ActivityResult onStartActivity(Intent intent) {
                Intent send = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (send == null || !Intent.ACTION_SEND.equals(send.getAction())) return null;
                chooser[0] = intent;
                return new Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null);
            }
        };
        getInstrumentation().addMonitor(monitor);
        try {
            getInstrumentation().runOnMainSync(() -> {
                View share = named(activity.getWindow().getDecorView(), activity.getString(R.string.share_puzzle_local));
                assertNotNull("Native share control is present", share); assertTrue(share.performClick());
            });
            NativeSaveAwaiter.await(getInstrumentation(), activity);
            assertNotNull("Native share opens the Sharesheet", chooser[0]);
            Intent send = chooser[0].getParcelableExtra(Intent.EXTRA_INTENT);
            assertEquals("text/plain", send.getType());
            String text = send.getStringExtra(Intent.EXTRA_TEXT);
            assertNotNull(text);
            return text.substring(text.lastIndexOf('\n') + 1);
        } finally { getInstrumentation().removeMonitor(monitor); }
    }
    private void route(String link, Class<? extends Activity> type) {
        Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(getInstrumentation().getTargetContext().getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        assertEquals(ShareActivity.class.getName(),
                getInstrumentation().getTargetContext().getPackageManager().resolveActivity(view, 0).activityInfo.name);
        Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(type.getName(), null, false);
        getInstrumentation().getTargetContext().startActivity(view);
        activity = monitor.waitForActivityWithTimeout(10000); getInstrumentation().removeMonitor(monitor);
        assertNotNull("Link reaches the native puzzle activity", activity);
        NativeSaveAwaiter.await(getInstrumentation(), activity);
    }
    private void recreate() {
        Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(activity.getClass().getName(), null, false);
        getInstrumentation().runOnMainSync(() -> activity.setRequestedOrientation(
                activity.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                        ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
        activity = monitor.waitForActivityWithTimeout(10000); getInstrumentation().removeMonitor(monitor);
        assertNotNull(activity); NativeSaveAwaiter.await(getInstrumentation(), activity);
    }
    private void warm(Intent view, Class<? extends Activity> type) {
        Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(type.getName(), null, false);
        getInstrumentation().runOnMainSync(() -> invoke("onNewIntent", new Class<?>[]{Intent.class}, view));
        activity = monitor.waitForActivityWithTimeout(10000); getInstrumentation().removeMonitor(monitor);
        assertNotNull(activity); NativeSaveAwaiter.await(getInstrumentation(), activity);
    }
    private void editPuzzle() {
        getInstrumentation().runOnMainSync(() -> {
            Function<Puzzle, String> action = next -> { move(next); return null; };
            invoke("change", new Class<?>[]{Function.class}, action);
        });
        NativeSaveAwaiter.await(getInstrumentation(), activity);
    }
    private static void move(Puzzle puzzle) {
        if (puzzle instanceof Takuzu p) { int i = 0; while (p.clues[i] >= 0) i++; p.cycle(i); }
        else if (puzzle instanceof Futoshiki p) { int i = 0; while (p.clues[i] != 0) i++; p.enter(i, 1); }
        else if (puzzle instanceof Nonogram p) p.cycle(0);
        else if (puzzle instanceof Numberlink p) p.edges[0] = 1;
        else if (puzzle instanceof Slitherlink p) p.cycle(0);
        else throw new AssertionError("Unsupported fixture");
    }
    private Object field(String name) {
        try { Field field = activity.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(activity); }
        catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
    private void invoke(String name, Class<?>[] types, Object... args) {
        try { Method method = activity.getClass().getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(activity, args); }
        catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
    private void close() {
        if (activity == null) return;
        NativeSaveAwaiter.await(getInstrumentation(), activity);
        File path = (File) callSaved();
        getInstrumentation().runOnMainSync(() -> activity.finish());
        getInstrumentation().waitForIdleSync();
        try { SaveQueue.forPath(path.getAbsolutePath()).flushBlocking(); }
        catch (IOException ex) { throw new AssertionError(ex); }
        activity = null;
    }
    private Object callSaved() {
        try { Method method = activity.getClass().getDeclaredMethod("saved"); method.setAccessible(true); return method.invoke(activity); }
        catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
    private DailyWord word() throws IOException {
        try (Reader guesses = new InputStreamReader(getInstrumentation().getTargetContext().getAssets()
                    .open("daily-word/guesses-v1.txt"), StandardCharsets.US_ASCII);
             Reader answers = new InputStreamReader(getInstrumentation().getTargetContext().getAssets()
                    .open("daily-word/answers-v1.txt"), StandardCharsets.US_ASCII)) { return new DailyWord(guesses, answers); }
    }
    private static byte[] encode(DailyWord game) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); game.write(new DataOutputStream(bytes)); return bytes.toByteArray();
    }
    private static View named(View view, String name) {
        if (name.equals(String.valueOf(view.getContentDescription()))) return view;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            View result = named(group.getChildAt(i), name); if (result != null) return result;
        }
        return null;
    }
    private void capture(String name) throws Exception {
        android.os.SystemClock.sleep(500); getInstrumentation().waitForIdleSync();
        android.graphics.Bitmap screenshot = getInstrumentation().getUiAutomation().takeScreenshot(); assertNotNull(screenshot);
        try (FileOutputStream out = new FileOutputStream(new File(
                getInstrumentation().getTargetContext().getExternalFilesDir(null), name))) {
            assertTrue(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out));
        }
        screenshot.recycle();
    }
}
