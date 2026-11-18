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
import android.os.Bundle;
import android.test.InstrumentationTestCase;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.TextView;

import com.quietgrid.sudoku.puzzles.*;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/** Run only with an external durable backup. Fixtures are produced by independent JVM oracles. */
@SuppressWarnings("deprecation")
public final class PuzzleDeviceTests extends InstrumentationTestCase {
    private Activity activity;
    private final Map<String, byte[]> originals = new LinkedHashMap<>();
    private boolean hadAppearance;
    private int appearance;
    private boolean hadTakuzuSize;
    private int takuzuSize;

    @Override protected void setUp() throws Exception {
        super.setUp();
        assertEquals("Never run puzzle fixtures against the player", "fyi.mllab.puzzle.validation",
                getInstrumentation().getTargetContext().getPackageName());
        for (Mode mode : Mode.values()) for (String suffix : new String[]{"", ".bak", ".new"}) {
            File file = new File(save(mode).toString() + suffix);
            originals.put(file.toString(), file.exists() ? Files.readAllBytes(file.toPath()) : null);
        }
        SharedPreferences settings = settings();
        hadAppearance = settings.contains("appearance"); appearance = settings.getInt("appearance", 0);
        hadTakuzuSize = settings.contains("takuzuSize"); takuzuSize = settings.getInt("takuzuSize", 8);
    }

    @Override protected void tearDown() throws Exception {
        try {
            close();
            for (Map.Entry<String, byte[]> entry : originals.entrySet()) {
                File file = new File(entry.getKey());
                if (entry.getValue() == null) { if (file.exists()) assertTrue(file.delete()); }
                else Files.write(file.toPath(), entry.getValue());
            }
            SharedPreferences.Editor edit = settings().edit();
            if (hadAppearance) edit.putInt("appearance", appearance); else edit.remove("appearance");
            if (hadTakuzuSize) edit.putInt("takuzuSize", takuzuSize); else edit.remove("takuzuSize");
            assertTrue(edit.commit());
        } finally { super.tearDown(); }
    }

    public void testHubHelpAndAllEightModes() throws Exception {
        activity = getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        settle();
        assertNotNull(findText("Sudoku")); assertNotNull(findText("Daily Word"));
        capture("puzzles-hub.png");
        for (Mode mode : Mode.values()) {
            write(mode, fixture(mode, "initial"));
            Activity hub = activity;
            Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(PuzzleMenuActivity.class.getName(), null, false);
            tap(require(findText(mode.title)));
            activity = getInstrumentation().waitForMonitorWithTimeout(monitor, 5000);
            getInstrumentation().removeMonitor(monitor); assertNotNull(activity); menuReady(mode);
            Activity menu = activity;
            monitor = getInstrumentation().addMonitor(PuzzleActivity.class.getName(), null, false);
            tap(require(tagged("puzzle-resume")));
            activity = monitor.waitForActivityWithTimeout(5000); getInstrumentation().removeMonitor(monitor); ready(mode);
            tap(require(findDescription("How to play " + mode.title)));
            assertTrue("Optional rules are visible", windowText("Close"));
            tapWindow("Close");
            close(); activity = menu; close(); activity = hub; settle();
        }
        capture("puzzles-hub-more.png");
    }

    public void testRealInputResumeAndDiscardConfirmation() throws Exception {
        for (Mode mode : Mode.values()) {
            write(mode, fixture(mode, "initial")); launch(mode);
            byte[] before = bytes(mode);
            Puzzle original = read(mode), complete = decodeFixture(mode, "complete");
            if (original instanceof Takuzu) {
                Takuzu p = (Takuzu) original; int target = 0; while (p.clues[target] >= 0) target++;
                tap(binary(0)); tap(square(target));
            } else if (original instanceof Nonogram) tap(square(0));
            else if (original instanceof Futoshiki) {
                Futoshiki p = (Futoshiki) original; int target = 0; while (p.clues[target] != 0) target++;
                tap(square(target)); tap(require(activity.findViewById(android.R.id.content).findViewWithTag("digit-1")));
            } else if (original instanceof Minesweeper) {
                tap(require(tagged("puzzle-input"))); tap(square(0));
                assertEquals(1, ((Minesweeper) read(mode)).flags());
                tap(square(0)); tap(require(tagged("puzzle-input"))); tap(square(27));
                assertEquals(27, ((Minesweeper) read(mode)).first);
                assertEquals(0, ((Minesweeper) read(mode)).adjacent(27));
            } else if (original instanceof Numberlink) {
                int[] edges = ((Numberlink) complete).edges; int edge = 0; while (edges[edge] == 0) edge++;
                tap(pathSquare(Numberlink.GRID.a[edge])); tap(pathSquare(Numberlink.GRID.b[edge]));
            } else {
                tapSlitherEdge(0);
                assertEquals(1, ((Slitherlink) read(mode)).edges[0]);
                tap(require(findText("Edge controls"))); tapWindow("Cycle selected edge");
                assertEquals(2, ((Slitherlink) read(mode)).edges[0]);
                tapWindow("Close");
            }
            byte[] edited = bytes(mode); assertFalse("Real touch changed durable " + mode, Arrays.equals(before, edited));
            capture("puzzles-" + mode.name().toLowerCase(java.util.Locale.ROOT) + ".png");
            close(); launch(mode); assertTrue("Resume is exact", Arrays.equals(edited, bytes(mode)));
            chooseNewPuzzle(mode, mode.defaultSetting(), false);
            assertTrue("Cancel retains exact progress", Arrays.equals(edited, bytes(mode))); close();
        }
    }

    public void testRealLastMovesWinAndLoss() throws Exception {
        for (Mode mode : Mode.values()) {
            write(mode, fixture(mode, "ready")); launch(mode);
            int target = Integer.parseInt(new String(Files.readAllBytes(fixtureFile(mode + "-target.txt").toPath()),
                    java.nio.charset.StandardCharsets.US_ASCII).trim());
            Puzzle completed = decodeFixture(mode, "complete");
            lastMove(completed, target);
            assertTrue("Actual native move completes " + mode, read(mode).solved());
            TextView completion = (TextView) require(findText("Puzzle complete!"));
            assertTrue("Every new mode has prominent completion", completion.getTextSize()
                    / completion.getResources().getDisplayMetrics().scaledDensity >= 24);
            capture("puzzles-" + mode.name().toLowerCase(java.util.Locale.ROOT) + "-complete.png");
            if (completed instanceof UndoablePuzzle) {
                close(); launch(mode);
                assertTrue("Completion history survives restart", progress(mode).canUndo());
                tap(require(findDescription("Undo last move")));
                assertFalse("Undo returns completion to active play", read(mode).solved());
                assertNull(findText("Puzzle complete!"));
                assertTrue("Completion Undo is exact", Arrays.equals(fixture(mode, "ready"), bytes(mode)));
                lastMove(completed, target); assertTrue(read(mode).solved());
            }
            chooseNewPuzzle(mode, mode.defaultSetting(), true);
            assertFalse("New after completion is playable", read(mode).solved()); close();
        }
        Minesweeper loss = new Minesweeper(77); loss.tap(27, false);
        int mine = 0; while (loss.mines[mine] == 0) mine++;
        write(Mode.MINESWEEPER, PuzzleStore.encode(loss)); launch(Mode.MINESWEEPER); tap(square(mine));
        assertTrue(read(Mode.MINESWEEPER).lost());
        assertNotNull(findText("Mine hit"));
        capture("puzzles-minesweeper-loss.png");
    }

    public void testActiveTimersAndCompletionAcrossModes() throws Exception {
        for (Mode mode : Mode.values()) {
            assertTrue(settings().edit().putInt("appearance", mode.ordinal() % 2 + 1).commit());
            PuzzleProgress saved = PuzzleStore.decodeProgress(mode, fixture(mode, "ready")).withElapsed(62000);
            write(mode, PuzzleStore.encode(saved)); launch(mode);
            assertNull("Saved active boards do not celebrate", tagged("completion-effect"));
            TextView time = (TextView) require(tagged("elapsed-time"));
            assertEquals(View.ACCESSIBILITY_LIVE_REGION_NONE, time.getAccessibilityLiveRegion());
            View board = tagged("puzzle-board");
            SystemClock.sleep(1200); getInstrumentation().waitForIdleSync();
            assertSame("A clock tick must not rebuild the grid", board, tagged("puzzle-board"));
            assertTrue("Active time is checkpointed: " + mode, progress(mode).elapsedMillis > 62000);
            tap(require(findDescription("How to play " + mode.title)));
            assertTrue(windowText("Close"));
            long paused = progress(mode).elapsedMillis;
            SystemClock.sleep(1200);
            assertEquals("Help excludes elapsed time: " + mode, paused, progress(mode).elapsedMillis);
            tapWindow("Close"); settle();
            long beforeClose = progress(mode).elapsedMillis;
            close(); long closed = progress(mode).elapsedMillis;
            assertTrue(closed >= beforeClose); SystemClock.sleep(1100);
            launch(mode);
            assertTrue("Restart keeps elapsed without background time", progress(mode).elapsedMillis >= closed
                    && progress(mode).elapsedMillis < closed + 1500);
            int target = Integer.parseInt(new String(Files.readAllBytes(fixtureFile(mode + "-target.txt").toPath()),
                    java.nio.charset.StandardCharsets.US_ASCII).trim());
            Puzzle complete = decodeFixture(mode, "complete");
            lastMove(complete, target);
            assertTrue(read(mode).solved());
            if (android.animation.ValueAnimator.areAnimatorsEnabled())
                assertNotNull("Real win starts one cascade: " + mode, tagged("completion-effect"));
            capture("puzzles-timer-" + mode.name().toLowerCase(java.util.Locale.ROOT) + "-win.png");
            if (mode == Mode.NONOGRAM) {
                StringBuilder times = new StringBuilder("file,capturedUptimeMillis\n");
                for (int frame = 0; frame < 5; frame++) {
                    String name = "puzzles-timer-nonogram-motion-" + frame + ".png";
                    times.append(name).append(',').append(SystemClock.uptimeMillis()).append('\n');
                    capture(name); SystemClock.sleep(120);
                }
                Files.write(new File(getInstrumentation().getTargetContext().getExternalFilesDir(null),
                        "puzzles-timer-nonogram-motion.csv").toPath(), times.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            }
            long won = progress(mode).elapsedMillis;
            SystemClock.sleep(1800);
            assertEquals("Terminal timer is frozen: " + mode, won, progress(mode).elapsedMillis);
            assertNull("Cascade cleans up after itself", tagged("completion-effect"));
            getInstrumentation().runOnMainSync(() -> {
                try {
                    Method refresh = PuzzleActivity.class.getDeclaredMethod("refresh", String.class);
                    refresh.setAccessible(true); refresh.invoke(activity, (String) null);
                } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
            });
            assertNull("Terminal redraw never repeats celebration", tagged("completion-effect"));
            close(); launch(mode);
            assertEquals(won, progress(mode).elapsedMillis);
            assertNull("Restored wins stay still", tagged("completion-effect"));
            if (complete instanceof UndoablePuzzle) {
                tap(require(findDescription("Undo last move"))); SystemClock.sleep(1200);
                assertFalse(read(mode).solved());
                assertTrue("Undo resumes accumulated active time", progress(mode).elapsedMillis > won);
                lastMove(complete, target);
                if (android.animation.ValueAnimator.areAnimatorsEnabled())
                    assertNotNull("Genuine recompletion can celebrate again", tagged("completion-effect"));
            }
            getInstrumentation().runOnMainSync(() -> {
                try {
                    java.lang.reflect.Field field = PuzzleActivity.class.getDeclaredField("completion");
                    field.setAccessible(true);
                    ((CompletionFeedback) field.get(activity)).celebrate(false);
                } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
            });
            assertNull("Disabled-motion branch creates no effect", tagged("completion-effect"));
            assertNotNull("Result is visible without motion", findText("Puzzle complete!"));
            chooseNewPuzzle(mode, mode.defaultSetting(), true);
            assertTrue("Successful replacement gets its own timer", progress(mode).elapsedMillis < 10000);
            capture("puzzles-timer-" + mode.name().toLowerCase(java.util.Locale.ROOT) + "-active.png");
            close();
        }
        Minesweeper loss = new Minesweeper(77); loss.tap(27, false);
        int mine = 0; while (loss.mines[mine] == 0) mine++;
        write(Mode.MINESWEEPER, PuzzleStore.encode(new PuzzleProgress(loss).withElapsed(40000)));
        launch(Mode.MINESWEEPER); tap(square(mine));
        long lost = progress(Mode.MINESWEEPER).elapsedMillis;
        SystemClock.sleep(1200);
        assertTrue(read(Mode.MINESWEEPER).lost());
        assertEquals(lost, progress(Mode.MINESWEEPER).elapsedMillis);
        assertNull("Loss is not a win celebration", tagged("completion-effect")); close();
    }

    private View tagged(String tag) { return activity.findViewById(android.R.id.content).findViewWithTag(tag); }

    public void testCorruptionIsExplicitAndOtherModesIsolated() throws Exception {
        Map<Mode, byte[]> before = new LinkedHashMap<>();
        for (Mode mode : Mode.values()) { byte[] fixture = fixture(mode, "initial"); write(mode, fixture); before.put(mode, fixture); }
        byte[] bad = {1, 2, 3}; write(Mode.TAKUZU, bad); launch(Mode.TAKUZU);
        assertNotNull(findText("Saved progress could not be read. It has not been replaced. Retry, or choose New puzzle to replace it."));
        assertTrue(Arrays.equals(bad, Files.readAllBytes(save(Mode.TAKUZU).toPath())));
        tap(require(findText("Retry"))); ready(Mode.TAKUZU);
        assertTrue(Arrays.equals(bad, Files.readAllBytes(save(Mode.TAKUZU).toPath())));
        chooseNewPuzzle(Mode.TAKUZU, 8, false); assertTrue(Arrays.equals(bad, Files.readAllBytes(save(Mode.TAKUZU).toPath())));
        chooseNewPuzzle(Mode.TAKUZU, 8, true);
        assertFalse(read(Mode.TAKUZU).solved());
        for (Mode mode : Mode.values()) if (mode != Mode.TAKUZU) assertTrue(Arrays.equals(before.get(mode), bytes(mode)));
    }

    public void testGenerationRetryKeepsDiscardConfirmation() throws Exception {
        write(Mode.TAKUZU, fixture(Mode.TAKUZU, "initial")); launch(Mode.TAKUZU);
        byte[] before = bytes(Mode.TAKUZU);
        getInstrumentation().runOnMainSync(() -> {
            try {
                java.lang.reflect.Field failed = PuzzleActivity.class.getDeclaredField("generationFailed");
                failed.setAccessible(true); failed.setBoolean(activity, true);
                Method refresh = PuzzleActivity.class.getDeclaredMethod("refresh", String.class);
                refresh.setAccessible(true); refresh.invoke(activity, "Generation reached its safety limit. Please retry.");
            } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
        });
        getInstrumentation().waitForIdleSync();
        tap(require(findText("Retry"))); assertTrue(windowText("Keep")); tapWindow("Keep");
        assertTrue("A stale generation retry never bypasses discard confirmation", Arrays.equals(before, bytes(Mode.TAKUZU)));
    }

    public void testLightDarkLandscapeAndLargeType() throws Exception {
        for (int look : new int[]{1, 2}) for (Mode mode : Mode.values()) {
            assertTrue(settings().edit().putInt("appearance", look).commit());
            write(mode, fixture(mode, "initial")); launch(mode);
            assertBoardControlAdjacency();
            assertEquals(look == 2 ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO,
                    activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK);
            capture("puzzles-" + mode.name().toLowerCase(java.util.Locale.ROOT) + (look == 1 ? "-light" : "-dark") + ".png");
            if (look == 2) {
                rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                assertBoardControlAdjacency();
                assertTrue(Arrays.equals(fixture(mode, "initial"), bytes(mode)));
                capture("puzzles-" + mode.name().toLowerCase(java.util.Locale.ROOT) + "-landscape.png");
                Configuration prior = new Configuration(activity.getResources().getConfiguration());
                try {
                    getInstrumentation().runOnMainSync(() -> {
                        Configuration large = new Configuration(prior); large.fontScale = 1.6f;
                        activity.getResources().updateConfiguration(large, activity.getResources().getDisplayMetrics());
                        try {
                            for (String name : new String[]{"build", "showBoard"}) {
                                Method method = PuzzleActivity.class.getDeclaredMethod(name); method.setAccessible(true); method.invoke(activity);
                            }
                            Method refresh = PuzzleActivity.class.getDeclaredMethod("refresh", String.class);
                            refresh.setAccessible(true); refresh.invoke(activity, (String) null);
                        } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
                    });
                    getInstrumentation().waitForIdleSync();
                    assertEquals(1.6f, activity.getResources().getConfiguration().fontScale);
                    if (mode == Mode.FUTOSHIKI) {
                        Futoshiki current = (Futoshiki) read(mode);
                        int target = 0; while (current.clues[target] != 0) target++;
                        tap(square(target));
                    }
                    ViewportChecks.all(activity);
                    assertBoardControlAdjacency();
                    View input = activity.findViewById(android.R.id.content).findViewWithTag("puzzle-input");
                    getInstrumentation().runOnMainSync(() ->
                            input.requestRectangleOnScreen(new Rect(0, 0, input.getWidth(), input.getHeight()), true));
                    getInstrumentation().waitForIdleSync(); SystemClock.sleep(150);
                    Rect visible = new Rect(); assertTrue("Fitted controls remain visible without scrolling", input.getGlobalVisibleRect(visible));
                    assertEquals("The full transformed control height is visible", ViewportChecks.bounds(input).height(), visible.height(), 2f);
                    View target = input instanceof ViewGroup ? ((ViewGroup) input).getChildAt(0) : input;
                    getInstrumentation().runOnMainSync(() -> { target.setFocusableInTouchMode(true); target.requestFocus(); });
                    getInstrumentation().waitForIdleSync();
                    assertTrue("Native enlarged control focus is reachable", target.isFocused());
                    capture("puzzles-" + mode.name().toLowerCase(java.util.Locale.ROOT) + "-large-type.png");
                } finally {
                    getInstrumentation().runOnMainSync(() -> activity.getResources().updateConfiguration(prior, activity.getResources().getDisplayMetrics()));
                }
            }
            close();
        }
    }

    public void testAccessibleCluesSelectionTypographyAndNoncolorMarks() throws Exception {
        for (boolean dark : new boolean[]{false, true}) for (int accent = 0; accent < 8; accent++) {
            MainActivity.Palette palette = new MainActivity.Palette(dark, accent);
            double a = android.graphics.Color.luminance(palette.ink), b = android.graphics.Color.luminance(palette.surface);
            assertTrue("Filled/unknown and mark contrast for palette " + dark + "/" + accent,
                    (Math.max(a, b) + .05) / (Math.min(a, b) + .05) >= 4.5);
        }
        write(Mode.NONOGRAM, fixture(Mode.NONOGRAM, "initial")); launch(Mode.NONOGRAM); tap(square(0));
        assertEquals("Non-color filled marker", "\u2713", ((TextView) square(0)).getText().toString());
        assertTrue(square(0).getContentDescription().toString().contains("filled"));
        capture("puzzles-a11y-nonogram.png"); close();

        write(Mode.FUTOSHIKI, fixture(Mode.FUTOSHIKI, "initial")); launch(Mode.FUTOSHIKI);
        for (int orientation : new int[]{ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}) {
            rotate(orientation);
            Configuration prior = new Configuration(activity.getResources().getConfiguration());
            try {
                getInstrumentation().runOnMainSync(() -> {
                    Configuration large = new Configuration(prior); large.fontScale = 2;
                    activity.getResources().updateConfiguration(large, activity.getResources().getDisplayMetrics());
                    try {
                        for (String name : new String[]{"build", "showBoard"}) {
                            Method method = PuzzleActivity.class.getDeclaredMethod(name); method.setAccessible(true); method.invoke(activity);
                        }
                        Method refresh = PuzzleActivity.class.getDeclaredMethod("refresh", String.class);
                        refresh.setAccessible(true); refresh.invoke(activity, (String) null);
                    } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
                });
                getInstrumentation().waitForIdleSync(); SystemClock.sleep(300);
                assertInequalities(activity.getWindow().getDecorView());
                Futoshiki p = (Futoshiki) read(Mode.FUTOSHIKI); int target = 0; while (p.clues[target] != 0) target++;
                tap(square(target));
                assertTrue("Futoshiki native selected property", square(target).isSelected());
                String description = square(target).getContentDescription().toString();
                assertTrue(description.contains("selected entry target"));
                assertTrue(activeNode(description).isSelected());
                String coordinates = "row " + (target / 4 + 1) + " column " + (target % 4 + 1);
                View key = activity.findViewById(android.R.id.content).findViewWithTag("digit-1");
                assertTrue(key.getContentDescription().toString().contains(coordinates));
                capture("puzzles-a11y-futoshiki-" + (orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT ? "portrait" : "landscape") + ".png");
            } finally {
                getInstrumentation().runOnMainSync(() ->
                        activity.getResources().updateConfiguration(prior, activity.getResources().getDisplayMetrics()));
            }
        }
        close();
        write(Mode.SLITHERLINK, fixture(Mode.SLITHERLINK, "initial")); launch(Mode.SLITHERLINK);
        tap(require(findText("Cell clues")));
        Slitherlink p = (Slitherlink) read(Mode.SLITHERLINK); boolean blank = false, numbered = false;
        for (int cell = 0; cell < 16; cell++) {
            TextView clue = loopClue(cell);
            String text = clue.getText().toString();
            assertTrue(text.contains("Row " + (cell / 4 + 1) + " column " + (cell % 4 + 1)));
            assertTrue(text.contains(p.clues[cell] < 0 ? "blank, no count constraint" : "clue " + p.clues[cell]));
            boolean inspect = p.clues[cell] < 0 ? !blank : !numbered;
            if (p.clues[cell] < 0) blank = true; else numbered = true;
            if (inspect) {
                tap(clue);
                getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_TAB);
                AccessibilityNodeInfo node = activeNode(text);
                assertTrue("Actual native clue keyboard focus", node.isFocused() || node.performAction(AccessibilityNodeInfo.ACTION_FOCUS));
                node.refresh(); assertTrue(node.isFocused());
            }
        }
        assertTrue("Fixture covers numbered and blank constraints", blank && numbered);
        capture("puzzles-a11y-slitherlink-clues.png");
        tapWindow("Close"); tap(require(findText("Edge controls")));
        tapWindow("Cycle selected edge");
        int first = ((Slitherlink) read(Mode.SLITHERLINK)).edges[0];
        assertTrue(activeNode("Cycle selected edge").performAction(AccessibilityNodeInfo.ACTION_CLICK));
        getInstrumentation().waitForIdleSync(); SystemClock.sleep(200);
        assertEquals("Native accessibility edge action persists", (first + 1) % 3, ((Slitherlink) read(Mode.SLITHERLINK)).edges[0]);
    }

    public void testTakuzuSizesErrorsAndMarkings() throws Exception {
        assertTrue(settings().edit().putInt("takuzuSize", 8).commit());
        byte[] legacy = Files.readAllBytes(fixtureFile("TAKUZU6-initial.bin").toPath());
        write(Mode.TAKUZU, legacy); launch(Mode.TAKUZU);
        assertEquals(6, read(Mode.TAKUZU).size());
        chooseNewPuzzle(Mode.TAKUZU, 8, false);
        assertTrue("Choosing another size never replaces progress without confirmation", Arrays.equals(legacy, bytes(Mode.TAKUZU)));
        close(); launch(Mode.TAKUZU);
        chooseNewPuzzle(Mode.TAKUZU, 6, false);
        assertTrue(Arrays.equals(legacy, bytes(Mode.TAKUZU)));
        chooseNewPuzzle(Mode.TAKUZU, 6, true);
        assertEquals(6, read(Mode.TAKUZU).size());
        chooseNewPuzzle(Mode.TAKUZU, 8, true);
        Takuzu generated = (Takuzu) read(Mode.TAKUZU); assertEquals(8, generated.size());
        assertEquals(1, Takuzu.count(generated.clues, 2, new Generation()));
        for (int value = 0; value < 2; value++) {
            int clue = 0; while (generated.clues[clue] != value) clue++;
            TextView cell = (TextView) square(clue);
            assertTrue("Starting clue underlined", (cell.getPaintFlags() & android.graphics.Paint.UNDERLINE_TEXT_FLAG) != 0);
            int fill = ((android.graphics.drawable.GradientDrawable)
                    ((android.graphics.drawable.RippleDrawable) cell.getBackground()).getDrawable(0)).getColor().getDefaultColor();
            double a = android.graphics.Color.luminance(fill), b = android.graphics.Color.luminance(cell.getCurrentTextColor());
            assertTrue("Actual digit/fill contrast", (Math.max(a, b) + .05) / (Math.min(a, b) + .05) >= 4.5);
            tap(cell); assertEquals("Given remains immutable", value, ((Takuzu) read(Mode.TAKUZU)).values[clue]);
        }
        capture("puzzles-takuzu-refined.png"); close();
        Takuzu wrong = generated.copy(); Arrays.fill(wrong.clues, -1); Arrays.fill(wrong.values, -1);
        wrong.values[0] = wrong.values[1] = wrong.values[2] = 0;
        write(Mode.TAKUZU, PuzzleStore.encode(wrong)); launch(Mode.TAKUZU);
        for (int cell = 0; cell < 3; cell++) {
            assertEquals("Non-color rule error mark", "0!", ((TextView) square(cell)).getText().toString());
            assertTrue(square(cell).getContentDescription().toString().contains("Three consecutive 0s in row 1"));
        }
        capture("puzzles-takuzu-errors.png");
        tap(square(2));
        for (int cell = 0; cell < 3; cell++) assertFalse(square(cell).getContentDescription().toString().contains("error:"));
        assertFalse("Cycling never leaves a stale entry target", square(2).isSelected()); close();
        for (String prefix : new String[]{"TAKUZU6", "TAKUZU"}) {
            write(Mode.TAKUZU, Files.readAllBytes(fixtureFile(prefix + "-ready.bin").toPath())); launch(Mode.TAKUZU);
            int target = Integer.parseInt(new String(Files.readAllBytes(fixtureFile(prefix + "-target.txt").toPath()),
                    java.nio.charset.StandardCharsets.US_ASCII).trim());
            Takuzu solved = (Takuzu) PuzzleStore.decode(Mode.TAKUZU, Files.readAllBytes(fixtureFile(prefix + "-complete.bin").toPath()));
            tap(binary(solved.values[target])); tap(square(target));
            assertTrue("Real completion at both sizes", read(Mode.TAKUZU).solved());
            TextView banner = (TextView) require(findText("Puzzle complete!"));
            assertTrue("Prominent completion banner", banner.getTextSize() / banner.getResources().getDisplayMetrics().density >= 24);
            capture("puzzles-takuzu-" + solved.size() + "-complete.png"); close();
        }
    }

    public void testTakuzuDualInputAndPersistentUndo() throws Exception {
        Mode mode = Mode.TAKUZU;
        write(mode, fixture(mode, "initial")); launch(mode);
        Takuzu original = (Takuzu) read(mode);
        int[] empty = new int[4]; int count = 0, fixed = -1;
        for (int cell = 0; cell < original.values.length; cell++) {
            if (original.clues[cell] >= 0) fixed = cell;
            else if (count < empty.length) empty[count++] = cell;
        }
        assertEquals(4, count); assertTrue(fixed >= 0);
        assertFalse(require(findDescription("Undo last move")).isEnabled());
        tap(square(empty[0])); assertEquals(0, ((Takuzu) read(mode)).values[empty[0]]);
        holdSquare(square(empty[0]), false); assertEquals(1, ((Takuzu) read(mode)).values[empty[0]]);
        holdSquare(square(empty[0]), false); assertEquals(-1, ((Takuzu) read(mode)).values[empty[0]]);
        assertEquals("Tap 0, hold 1 and repeat-to-clear are real edits", 3, progress(mode).undoCount());
        tap(binary(0)); assertTrue(binary(0).isSelected());
        assertEquals("Arming is not an edit", 3, progress(mode).undoCount());
        tap(square(empty[0])); tap(square(empty[1]));
        assertEquals(0, ((Takuzu) read(mode)).values[empty[0]]);
        assertEquals(0, ((Takuzu) read(mode)).values[empty[1]]);
        assertTrue("Digit-first remains armed", binary(0).isSelected());
        tap(binary(1));
        assertEquals("Switching never overwrites the last square", 0, ((Takuzu) read(mode)).values[empty[1]]);
        assertEquals(5, progress(mode).undoCount());
        tap(square(empty[0])); tap(square(empty[0]));
        assertEquals("Same-value input is a no-op", 6, progress(mode).undoCount());
        tap(binary(1)); assertFalse("Same tool cancels", binary(1).isSelected());
        holdSquare(square(empty[0]), false); assertEquals("Deselecting keeps secondary 1/clear available", -1, ((Takuzu) read(mode)).values[empty[0]]);
        tap(binary(0)); tap(require(findText("Takuzu")));
        assertFalse("Outside tap cancels", binary(0).isSelected());
        tap(square(empty[2]));
        assertEquals("Outside cancellation re-enters default tap 0, not selection", 0, ((Takuzu) read(mode)).values[empty[2]]);
        assertFalse(square(empty[2]).isSelected());
        tap(binary(0)); tap(square(empty[3])); tap(square(empty[3]));
        assertEquals("Repeated armed entry is not cycling", 0, ((Takuzu) read(mode)).values[empty[3]]);
        tap(binary(2)); tap(square(empty[0]));
        assertTrue("Local erasure uses a named native icon", binary(2) instanceof android.widget.ImageButton);
        assertTrue(binary(2).getContentDescription().toString().startsWith("Erase,"));
        assertEquals("Erase", binary(2).getTooltipText());
        assertEquals("Clearing an empty square is a no-op", 9, progress(mode).undoCount());
        byte[] beforeClear = PuzzleStore.encode(read(mode));
        tap(square(empty[1]));
        assertEquals("Erase removes only the target", -1, ((Takuzu) read(mode)).values[empty[1]]);
        assertEquals(10, progress(mode).undoCount());
        tap(binary(2)); assertFalse(binary(2).isSelected());
        tap(binary(0)); tap(square(fixed)); tap(binary(1));
        assertEquals("Fixed clues never create history", 10, progress(mode).undoCount());
        assertEquals(original.clues[fixed], ((Takuzu) read(mode)).values[fixed]);
        assertEquals("Switch after given never changes the prior entry", -1, ((Takuzu) read(mode)).values[empty[0]]);
        View zero = binary(0);
        getInstrumentation().runOnMainSync(() -> { zero.setFocusableInTouchMode(true); zero.requestFocus(); });
        getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_ENTER);
        getInstrumentation().waitForIdleSync();
        assertTrue("Focused Enter activates the native digit", binary(0).isSelected());
        assertTrue(activeNode(zero.getContentDescription().toString()).isSelected());
        assertEquals(10, progress(mode).undoCount());
        capture("puzzles-takuzu-input-undo.png");
        close(); launch(mode); assertEquals(10, progress(mode).undoCount());
        View undo = require(findDescription("Undo last move")); tap(undo);
        assertTrue("Undo restores the exact state before Clear", Arrays.equals(beforeClear, PuzzleStore.encode(read(mode))));
        assertEquals(9, progress(mode).undoCount()); assertFalse(binary(0).isSelected());
        ViewportChecks.all(activity);
        close();

        Takuzu wrong = original.copy(); Arrays.fill(wrong.clues, -1); Arrays.fill(wrong.values, -1);
        wrong.values[0] = wrong.values[1] = wrong.values[2] = 0;
        write(mode, PuzzleStore.encode(wrong)); launch(mode);
        tap(square(2));
        assertFalse(square(0).getContentDescription().toString().contains("error:"));
        tap(require(findDescription("Undo last move")));
        assertTrue("Undo restores rule feedback", square(0).getContentDescription().toString().contains("error:"));
        capture("puzzles-takuzu-undo-errors.png");
    }

    public void testEditableModeUndoAndIsolation() throws Exception {
        Map<Mode, byte[]> initial = new LinkedHashMap<>();
        for (Mode mode : Mode.values()) { byte[] data = fixture(mode, "initial"); write(mode, data); initial.put(mode, data); }
        for (Mode mode : Mode.values()) {
            launch(mode);
            if (mode == Mode.MINESWEEPER) {
                assertNull("No undo for revealed information or flags", findDescription("Undo last move")); close(); continue;
            }

            assertFalse(require(findDescription("Undo last move")).isEnabled());
            Puzzle p = read(mode);
            if (p instanceof Takuzu) {
                int cell = 0; while (((Takuzu) p).clues[cell] >= 0) cell++;
                tap(binary(0)); tap(square(cell));
            } else if (p instanceof Nonogram) tap(square(0));
            else if (p instanceof Futoshiki) {
                int cell = 0; while (((Futoshiki) p).clues[cell] != 0) cell++;
                tap(square(cell)); tap(require(activity.findViewById(android.R.id.content).findViewWithTag("digit-1")));
            } else if (p instanceof Numberlink) {
                tap(pathSquare(0)); tap(pathSquare(15));
                assertEquals("Rejected non-neighbor edge adds no history", 0, progress(mode).undoCount());
                Numberlink complete = (Numberlink) decodeFixture(mode, "complete");
                int edge = 0; while (complete.edges[edge] == 0) edge++;
                tap(pathSquare(Numberlink.GRID.a[edge]));
                assertEquals("Path selection adds no history", 0, progress(mode).undoCount());
                tap(pathSquare(Numberlink.GRID.b[edge]));
            } else tapSlitherEdge(0);
            assertEquals(1, progress(mode).undoCount());
            close(); launch(mode);
            View undo = require(findDescription("Undo last move")); tap(undo);
            assertTrue("Exact independent history for " + mode, Arrays.equals(initial.get(mode), bytes(mode)));
            assertFalse(undo.isEnabled());
            for (Mode other : Mode.values()) assertTrue("Undo never changes " + other, Arrays.equals(initial.get(other), bytes(other)));
            close();
        }
    }

    public void testDesignRuleFeedbackAndLocalErase() throws Exception {
        Futoshiki futo = (Futoshiki) decodeFixture(Mode.FUTOSHIKI, "initial");
        Arrays.fill(futo.clues, 0); Arrays.fill(futo.values, 0);
        Arrays.fill(futo.horizontal, 0); Arrays.fill(futo.vertical, 0);
        futo.horizontal[0] = -1; futo.values[0] = 3; futo.values[1] = 2;
        write(Mode.FUTOSHIKI, PuzzleStore.encode(futo)); launch(Mode.FUTOSHIKI);
        assertEquals("3!", ((TextView) square(0)).getText().toString());
        assertTrue(square(1).getContentDescription().toString().contains("must be smaller"));
        assertTrue(square(0).getBackground() instanceof android.graphics.drawable.RippleDrawable);
        tap(square(1));
        View erase = activity.findViewById(android.R.id.content).findViewWithTag("digit-0");
        assertTrue("Local Erase uses an eraser icon", erase instanceof android.widget.ImageButton);
        assertTrue(erase.getContentDescription().toString().startsWith("Erase, row 1 column 2"));
        tap(require(activity.findViewById(android.R.id.content).findViewWithTag("digit-4")));
        assertFalse("Correction removes the old error", square(0).getContentDescription().toString().contains("error:"));
        assertTrue("Selection stays distinguishable from an error", square(1).isSelected());
        assertTrue((((TextView) square(1)).getPaintFlags() & android.graphics.Paint.UNDERLINE_TEXT_FLAG) != 0);
        tap(require(findDescription("Undo last move")));
        assertTrue(square(0).getContentDescription().toString().contains("error:"));
        tap(square(1)); tap(erase);
        Futoshiki erased = (Futoshiki) read(Mode.FUTOSHIKI);
        assertEquals(0, erased.values[1]); assertEquals(3, erased.values[0]);
        assertFalse(square(0).getContentDescription().toString().contains("error:"));
        tap(require(findDescription("Undo last move")));
        assertTrue(square(0).getContentDescription().toString().contains("error:"));
        capture("puzzles-design-futoshiki-errors.png"); close();

        Slitherlink slither = (Slitherlink) decodeFixture(Mode.SLITHERLINK, "initial");
        Arrays.fill(slither.clues, -1); Arrays.fill(slither.edges, 0); slither.clues[0] = 1;
        int top = Slitherlink.SIDES[0][0], left = Slitherlink.SIDES[0][1];
        slither.edges[top] = slither.edges[left] = 1;
        write(Mode.SLITHERLINK, PuzzleStore.encode(slither)); launch(Mode.SLITHERLINK);
        tap(require(findText("Cell clues")));
        TextView clue = loopClue(0);
        assertTrue(clue.getContentDescription().toString().contains("Row 1 column 1 has 2 drawn sides"));
        assertFalse("Reason stays nonvisual", clue.getText().toString().contains("has 2 drawn sides"));
        capture("puzzles-design-slitherlink-errors.png");
        tapWindow("Close"); tapSlitherEdge(top);
        assertFalse("Crossing a side corrects overcount", ((Slitherlink) read(Mode.SLITHERLINK)).errors().any());
        tap(require(findDescription("Undo last move")));
        assertTrue("Undo refreshes loop errors", ((Slitherlink) read(Mode.SLITHERLINK)).errors().any());
        tap(require(findText("Edge controls")));
        assertTrue("Native edge option carries a localized non-color reason",
                edgeAlternativeReason(top).contains("Row 1 column 1"));
        close();
        Arrays.fill(slither.clues, -1); Arrays.fill(slither.edges, 0);
        for (int edge : Slitherlink.GRID.at[6]) slither.edges[edge] = 1;
        write(Mode.SLITHERLINK, PuzzleStore.encode(slither)); launch(Mode.SLITHERLINK);
        tap(require(findText("Edge controls")));
        int branch = Slitherlink.GRID.at[6][0];
        assertTrue(edgeAlternativeReason(branch).contains("Branch at dot row 2 column 2"));
        tapWindow("Close");
        tapSlitherEdge(branch); tapSlitherEdge(Slitherlink.GRID.at[6][1]);
        assertFalse("Reducing branch degree corrects it", ((Slitherlink) read(Mode.SLITHERLINK)).errors().any());
        close();
        Arrays.fill(slither.edges, 0); slither.clues[15] = 1;
        for (int edge : Slitherlink.SIDES[0]) slither.edges[edge] = 1;
        write(Mode.SLITHERLINK, PuzzleStore.encode(slither)); launch(Mode.SLITHERLINK);
        tap(require(findText("Edge controls")));
        assertTrue(edgeAlternativeReason(top).contains("Separate closed loop"));
        tapWindow("Close");
        tapSlitherEdge(top);
        assertFalse("Opening a premature loop removes the contradiction", ((Slitherlink) read(Mode.SLITHERLINK)).errors().any());
    }

    private void lastMove(Puzzle complete, int target) throws Exception {
        if (complete instanceof Takuzu) { tap(binary(((Takuzu) complete).values[target])); tap(square(target)); }
        else if (complete instanceof Nonogram || complete instanceof Minesweeper) tap(square(target));
        else if (complete instanceof Futoshiki) {
            tap(square(target)); tap(require(activity.findViewById(android.R.id.content).findViewWithTag("digit-" + ((Futoshiki) complete).values[target])));
        } else if (complete instanceof Numberlink) {
            tap(pathSquare(Numberlink.GRID.a[target])); tap(pathSquare(Numberlink.GRID.b[target]));
        } else tapSlitherEdge(target);
    }

    public void testNonogramContradictionsCorrectionAndUndo() throws Exception {
        Mode mode = Mode.NONOGRAM;
        for (int look : new int[]{1, 2}) {
            assertTrue(settings().edit().putInt("appearance", look).commit());
            Nonogram answer = (Nonogram) decodeFixture(mode, "complete");
            Nonogram legal = (Nonogram) decodeFixture(mode, "initial");
            int wrong = -1;
            for (int cell = 0; cell < 25; cell++) if (answer.values[cell] != 1) {
                legal.values[cell] = 1; boolean error = false;
                for (boolean invalid : legal.contradictions()) error |= invalid;
                legal.values[cell] = 0;
                if (!error) { wrong = cell; break; }
            }
            assertTrue("Fixture provides a legal hidden-solution mistake", wrong >= 0);
            write(mode, fixture(mode, "initial")); launch(mode);
            tap(square(wrong));
            for (int line = 0; line < 10; line++) {
                TextView quiet = (TextView) require(tagged("nonogram-" + (line < 5 ? "row-" : "column-") + line % 5));
                assertFalse("Legal-but-wrong does not become an error", quiet.getContentDescription().toString().contains("contradiction:"));
            }
            tap(require(findDescription("Undo last move")));
            Nonogram initial = (Nonogram) read(mode); int row = 0;
            while (row < 5 && (Arrays.stream(initial.rows[row]).sum() == 0 || Arrays.stream(initial.rows[row]).sum() == 5)) row++;
            assertTrue("Fixture includes a nonempty non-full run clue", row < 5);
            TextView clue = activity.findViewById(android.R.id.content).findViewWithTag("nonogram-row-" + row);
            int normalColor = clue.getCurrentTextColor();
            String originalClue = clue.getText().toString();
            tap(square(row * 5));
            assertEquals("A feasible incomplete line is not marked wrong", normalColor, clue.getCurrentTextColor());
            for (int col = 1; col < 5; col++) tap(square(row * 5 + col));
            assertFalse("Impossible fills mark the clue, not individual culprits", normalColor == clue.getCurrentTextColor());
            assertEquals("No error text or symbol is added to the clue", originalClue, clue.getText().toString());
            TextView feedback = (TextView) require(tagged("puzzle-feedback"));
            assertEquals("No visible error explanation", View.GONE, feedback.getVisibility());
            assertEquals("", feedback.getText().toString());
            assertFalse(square(row * 5).getContentDescription().toString().contains("error"));
            assertFalse(read(mode).solved());
            String description = clue.getContentDescription().toString();
            assertTrue(description.contains("Row " + (row + 1)) && description.contains("no placement"));
            assertTrue(clue.isFocusable());
            tap(clue); getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_TAB);
            AccessibilityNodeInfo node = activeNode(description);
            assertTrue(node.isFocused() || node.performAction(AccessibilityNodeInfo.ACTION_FOCUS));
            node.refresh(); assertTrue(node.isFocused());
            int fill = ((android.graphics.drawable.GradientDrawable) clue.getBackground()).getColor().getDefaultColor();
            double a = android.graphics.Color.luminance(fill), b = android.graphics.Color.luminance(clue.getCurrentTextColor());
            assertTrue("Actual contradiction text contrast", (Math.max(a, b) + .05) / (Math.min(a, b) + .05) >= 4.5);
            capture("puzzles-nonogram-errors-" + (look == 1 ? "light" : "dark") + ".png");
            for (int col = 0; col < 5; col++) tap(square(row * 5 + col));
            assertTrue("Crossing all cells cannot satisfy a nonempty clue", ((Nonogram) read(mode)).contradictions()[row]);
            int corrected = -1;
            for (int col = 0; col < 5 && ((Nonogram) read(mode)).contradictions()[row]; col++) {
                corrected = row * 5 + col; tap(square(corrected));
            }
            assertTrue(corrected >= 0);
            assertEquals("Correction immediately clears clue feedback", normalColor, clue.getCurrentTextColor());
            tap(require(findDescription("Undo last move")));
            assertFalse("Undo restores the contradiction marking", normalColor == clue.getCurrentTextColor());
            tap(square(corrected));
            assertEquals(normalColor, clue.getCurrentTextColor());
            assertEquals(View.GONE, tagged("puzzle-feedback").getVisibility());
            close();
        }
    }

    public void testNumberlinkDragColorsAndStableFeedback() throws Exception {
        Mode mode = Mode.NUMBERLINK;
        Numberlink board = (Numberlink) decodeFixture(mode, "initial");
        Arrays.fill(board.endpoints, 0); Arrays.fill(board.edges, 0);
        for (int pair = 1; pair <= 4; pair++) {
            board.endpoints[(pair - 1) * 4] = pair; board.endpoints[(pair - 1) * 4 + 3] = pair;
        }
        for (int look : new int[]{1, 2}) {
            assertTrue(settings().edit().putInt("appearance", look).commit());
            write(mode, PuzzleStore.encode(board)); launch(mode);
            View grid = tagged("puzzle-board");
            int[] position = new int[2]; grid.getLocationOnScreen(position);
            int top = position[1];
            tap(pathSquare(0));
            TextView feedback = (TextView) require(tagged("puzzle-feedback"));
            assertEquals(View.GONE, feedback.getVisibility());
            assertEquals("", feedback.getText().toString());
            grid.getLocationOnScreen(position); assertEquals("Selection never shifts grid", top, position[1]);
            int[] colors = new int[3];
            for (int pair = 0; pair < 3; pair++) {
                colors[pair] = ((TextView) pathSquare(pair * 4)).getCurrentTextColor();
                assertEquals(colors[pair], ((TextView) pathSquare(pair * 4 + 3)).getCurrentTextColor());
                assertTrue(pathSquare(pair * 4).getContentDescription().toString().contains("pair " + (pair + 1)));
            }
            assertTrue(colors[0] != colors[1] && colors[0] != colors[2] && colors[1] != colors[2]);
            interruptedDrag(false); interruptedDrag(true);
            assertEquals("Interrupted gestures do not create history", 0, progress(mode).undoCount());
            dragPath(new int[]{0, 3}, false);
            assertEquals("Fast motion interpolates each intervening edge", 3, Arrays.stream(((Numberlink) read(mode)).edges).sum());
            assertEquals("One drag is one history entry", 1, progress(mode).undoCount());
            capture("puzzles-numberlink-drag-" + (look == 1 ? "light" : "dark") + ".png");
            tap(require(findDescription("Undo last move")));
            assertEquals(0, Arrays.stream(((Numberlink) read(mode)).edges).sum());
            dragPath(new int[]{0, 1, 2, 1}, false);
            assertEquals("Backtracking retracts own route", 1, Arrays.stream(((Numberlink) read(mode)).edges).sum());
            int[] kept = ((Numberlink) read(mode)).edges.clone();
            dragPath(new int[]{1, 5, 6}, true);
            assertTrue("Canceled preview is not saved", Arrays.equals(kept, ((Numberlink) read(mode)).edges));
            assertEquals(1, progress(mode).undoCount());
            dragPath(new int[]{0, 4}, false);
            assertTrue("Different pair endpoint is immutable to drag", Arrays.equals(kept, ((Numberlink) read(mode)).edges));
            close(); launch(mode);
            for (int pair = 0; pair < 3; pair++) assertEquals("Pair colors stable across restart", colors[pair],
                    ((TextView) pathSquare(pair * 4)).getCurrentTextColor());
            tap(pathSquare(4)); tap(pathSquare(5));
            assertEquals("Native tap alternative still works", 1, ((Numberlink) read(mode)).edges[Numberlink.GRID.between(4, 5)]);
            assertEquals(View.GONE, tagged("puzzle-feedback").getVisibility());
            close();
        }
        for (boolean dark : new boolean[]{false, true}) for (int accent = 0; accent < 8; accent++) {
            MainActivity.Palette palette = new MainActivity.Palette(dark, accent);
            for (int pair = 1; pair <= Numberlink.MAX_PAIRS; pair++) {
                double ink = android.graphics.Color.luminance(NumberlinkBoard.pairColor(palette, pair));
                for (int surface : new int[]{palette.background, palette.surface}) {
                    double fill = android.graphics.Color.luminance(surface);
                    assertTrue("Stable pair colors have readable contrast in every accent",
                            (Math.max(ink, fill) + .05) / (Math.min(ink, fill) + .05) >= 4.5);
                }
            }
        }
    }

    private void interruptedDrag(boolean multitouch) {
        android.graphics.RectF bounds = ViewportChecks.bounds(tagged("puzzle-board"));
        float side = bounds.width() / 4f, x = bounds.left + side / 2, y = bounds.top + side / 2;
        long start = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(start, start, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent move = MotionEvent.obtain(start, start + 60, MotionEvent.ACTION_MOVE, x + side, y, 0);
        getInstrumentation().sendPointerSync(down); getInstrumentation().sendPointerSync(move); down.recycle(); move.recycle();
        if (multitouch) {
            MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[2];
            MotionEvent.PointerCoords[] positions = new MotionEvent.PointerCoords[2];
            for (int i = 0; i < 2; i++) {
                properties[i] = new MotionEvent.PointerProperties(); properties[i].id = i;
                properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
                positions[i] = new MotionEvent.PointerCoords(); positions[i].x = x + side;
                positions[i].y = y + i * side; positions[i].pressure = 1; positions[i].size = 1;
            }
            for (int action : new int[]{MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP}) {
                MotionEvent event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                        2, properties, positions, 0, 0, 1, 1, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0);
                getInstrumentation().sendPointerSync(event); event.recycle();
            }
        } else getInstrumentation().runOnMainSync(() -> activity.onWindowFocusChanged(false));
        MotionEvent up = MotionEvent.obtain(start, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x + side, y, 0);
        getInstrumentation().sendPointerSync(up); up.recycle();
        if (!multitouch) getInstrumentation().runOnMainSync(() -> activity.onWindowFocusChanged(true));
        getInstrumentation().waitForIdleSync();
    }

    private void dragPath(int[] cells, boolean cancel) {
        View grid = require(tagged("puzzle-board"));
        getInstrumentation().runOnMainSync(() -> grid.requestRectangleOnScreen(new Rect(0, 0, grid.getWidth(), grid.getHeight()), true));
        getInstrumentation().waitForIdleSync(); SystemClock.sleep(120);
        android.graphics.RectF p = ViewportChecks.bounds(grid);
        int n = (int) Math.round(Math.sqrt(((ViewGroup) grid).getChildCount()));
        float side = p.width() / n;
        long start = SystemClock.uptimeMillis();
        for (int step = 0; step < cells.length; step++) {
            int cell = cells[step];
            MotionEvent event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), step == 0 ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_MOVE,
                    p.left + (cell % n + .5f) * side, p.top + (cell / n + .5f) * side, 0);
            getInstrumentation().sendPointerSync(event); event.recycle(); SystemClock.sleep(60);
        }
        int last = cells[cells.length - 1];
        MotionEvent end = MotionEvent.obtain(start, SystemClock.uptimeMillis(), cancel ? MotionEvent.ACTION_CANCEL : MotionEvent.ACTION_UP,
                p.left + (last % n + .5f) * side, p.top + (last / n + .5f) * side, 0);
        getInstrumentation().sendPointerSync(end); end.recycle();
        getInstrumentation().waitForIdleSync(); SystemClock.sleep(100);
    }
    public void testGameplayHoldTiming() throws Exception {
        assertEquals(400, BinaryPress.timeout(500));
        assertEquals(320, BinaryPress.timeout(400));
        assertEquals(1000, BinaryPress.timeout(1000));
        assertEquals(1500, BinaryPress.timeout(1500));
        Mode mode = Mode.MINESWEEPER;
        write(mode, PuzzleStore.encode(new Minesweeper(2026112001))); launch(mode);
        android.graphics.RectF bounds = ViewportChecks.bounds(square(0));
        float x = bounds.centerX(), y = bounds.centerY();
        long start = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(start, start, MotionEvent.ACTION_DOWN, x, y, 0);
        getInstrumentation().sendPointerSync(down); down.recycle();
        int timeout = BinaryPress.timeout();
        SystemClock.sleep(Math.max(0, start + timeout - 100 - SystemClock.uptimeMillis()));
        assertEquals("Secondary does not fire before the shorter deadline", 0, ((Minesweeper) read(mode)).flags());
        assertEquals(-1, ((Minesweeper) read(mode)).first);
        SystemClock.sleep(Math.max(0, start + timeout + 40 - SystemClock.uptimeMillis()));
        assertEquals("Secondary fires without waiting for the old deadline", 1, ((Minesweeper) read(mode)).flags());
        SystemClock.sleep(android.view.ViewConfiguration.getLongPressTimeout() + 80);
        assertEquals("Native timeout cannot trigger the secondary twice", 1, ((Minesweeper) read(mode)).flags());
        MotionEvent up = MotionEvent.obtain(start, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0);
        getInstrumentation().sendPointerSync(up); up.recycle();
        getInstrumentation().waitForIdleSync();
        assertEquals(1, ((Minesweeper) read(mode)).flags()); assertEquals(-1, ((Minesweeper) read(mode)).first);
        bounds = ViewportChecks.bounds(square(27)); x = bounds.centerX(); y = bounds.centerY();
        start = SystemClock.uptimeMillis();
        down = MotionEvent.obtain(start, start, MotionEvent.ACTION_DOWN, x, y, 0);
        getInstrumentation().sendPointerSync(down); down.recycle();
        SystemClock.sleep(Math.max(0, start + timeout - 100 - SystemClock.uptimeMillis()));
        up = MotionEvent.obtain(start, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0);
        getInstrumentation().sendPointerSync(up); up.recycle(); settle();
        assertEquals("Release before deadline is still a reveal", 27, ((Minesweeper) read(mode)).first);
    }

    public void testMinesNumberChording() throws Exception {
        Mode mode = Mode.MINESWEEPER;
        int target = 18;
        Minesweeper initial = null;
        for (int seed = 0; seed < 1000; seed++) {
            Minesweeper candidate = new Minesweeper(seed); candidate.tap(63, false);
            if (candidate.mines[target] == 0 && candidate.adjacent(target) > 0) { initial = candidate; break; }
        }
        assertNotNull(initial);
        Arrays.fill(initial.cells, 0); initial.cells[initial.first] = initial.cells[target] = 1;
        int safe = -1, mine = -1;
        for (int i = 0; i < 64; i++) if (i != target && Math.abs(i / 8 - target / 8) <= 1
                && Math.abs(i % 8 - target % 8) <= 1) {
            if (initial.mines[i] == 1) { initial.cells[i] = 2; mine = i; }
            else safe = i;
        }
        assertTrue(safe >= 0 && mine >= 0);
        for (int scenario = 0; scenario < 3; scenario++) {
            Minesweeper fixture = initial.copy();
            if (scenario == 1) { fixture.cells[mine] = 0; fixture.cells[safe] = 2; }
            if (scenario == 2) {
                for (int i = 0; i < 64; i++) if (fixture.mines[i] == 0) fixture.cells[i] = 1;
                fixture.cells[safe] = 0;
            }
            write(mode, PuzzleStore.encode(new PuzzleProgress(fixture).withElapsed(40000))); launch(mode);
            java.util.Map<Integer, android.graphics.RectF> bounds = mineBounds();
            byte[] unchanged = bytes(mode);
            assertTrue("Revealed numbers retain native click accessibility", square(target).isEnabled());
            assertTrue(square(target).createAccessibilityNodeInfo().isClickable());
            holdSquare(square(target), false); assertTrue(Arrays.equals(unchanged, bytes(mode)));
            holdSquare(square(target), true); assertTrue(Arrays.equals(unchanged, bytes(mode)));
            holdSquare(square(target), 2); assertTrue(Arrays.equals(unchanged, bytes(mode)));
            if (scenario == 0) tap(require(tagged("puzzle-input")));
            if (scenario == 2) {
                getInstrumentation().runOnMainSync(() ->
                        assertTrue(square(target).performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null)));
                settle();
            } else tap(square(target));
            Minesweeper expected = fixture.copy(); expected.tap(target, false);
            Minesweeper actual = (Minesweeper) read(mode);
            assertTrue(Arrays.equals(expected.cells, actual.cells));
            assertEquals(scenario == 1, actual.lost()); assertEquals(scenario == 2, actual.solved());
            assertEquals("Chording never moves cells or controls", bounds, mineBounds());
            ViewportChecks.all(activity);
            capture("puzzles-mines-chord-" + scenario + ".png");
            if (scenario > 0) {
                long elapsed = progress(mode).elapsedMillis; SystemClock.sleep(1200);
                assertEquals("Terminal chord freezes timer", elapsed, progress(mode).elapsedMillis);
            }
            byte[] saved = bytes(mode); close(); launch(mode);
            assertTrue("Chord state resumes exactly", Arrays.equals(saved, bytes(mode)));
            java.lang.reflect.Field field = PuzzleActivity.class.getDeclaredField("minesFeedback"); field.setAccessible(true);
            assertFalse("Resume never replays chord animation", ((MinesFeedback) field.get(activity)).active());
            close();
        }
    }

    public void testMinesHoldFlagsAndRevealedColors() throws Exception {
        Mode mode = Mode.MINESWEEPER;
        for (int look : new int[]{1, 2}) {
            assertTrue(settings().edit().putInt("appearance", look).commit());
            write(mode, PuzzleStore.encode(new Minesweeper(2026112001))); launch(mode);
            java.util.Map<Integer, android.graphics.RectF> bounds = mineBounds();
            holdSquare(square(0), true);
            assertEquals("Canceled hold never flags or reveals", 0, ((Minesweeper) read(mode)).flags());
            assertEquals(-1, ((Minesweeper) read(mode)).first);
            holdSquare(square(0), 2);
            assertEquals("Multitouch cancels secondary and default input", 0, ((Minesweeper) read(mode)).flags());
            assertEquals(-1, ((Minesweeper) read(mode)).first);
            holdSquare(square(0), false);
            assertEquals(1, ((Minesweeper) read(mode)).flags());
            assertEquals("Release after hold never reveals", -1, ((Minesweeper) read(mode)).first);
            assertFalse("Hold does not arm the explicit flag tool", tagged("puzzle-input").isSelected());
            AccessibilityNodeInfo node = square(0).createAccessibilityNodeInfo();
            assertTrue(node.getActionList().stream().anyMatch(action -> action.getId() == AccessibilityNodeInfo.ACTION_LONG_CLICK
                    && "Toggle flag".contentEquals(action.getLabel())));
            holdSquare(square(0), false);
            assertEquals(0, ((Minesweeper) read(mode)).flags()); assertEquals(-1, ((Minesweeper) read(mode)).first);
            tap(square(27));
            Minesweeper game = (Minesweeper) read(mode);
            int covered = 0; while (game.cells[covered] != 0) covered++;
            assertFalse("Revealed and covered fills remain visibly different", squareFill(square(27)) == squareFill(square(covered)));
            assertTrue(square(27).getContentDescription().toString().contains("neighboring mines"));
            assertTrue(square(covered).getContentDescription().toString().contains("covered"));
            holdSquare(square(covered), false);
            assertEquals(2, ((Minesweeper) read(mode)).cells[covered]);
            assertEquals("Reveal/flag never alter the grid", bounds, mineBounds());
            capture("puzzles-mines-hold-colors-" + look + ".png");
            byte[] state = bytes(mode); close(); launch(mode);
            assertTrue(Arrays.equals(state, bytes(mode)));
            assertEquals(2, ((Minesweeper) read(mode)).cells[covered]);
            close();
        }
    }

    private int squareFill(View square) {
        android.graphics.drawable.RippleDrawable ripple = (android.graphics.drawable.RippleDrawable) square.getBackground();
        return ((android.graphics.drawable.GradientDrawable) ripple.getDrawable(0)).getColor().getDefaultColor();
    }

    private void holdSquare(View view, boolean cancel) {
        holdSquare(view, cancel ? 1 : 0);
    }

    private void holdSquare(View view, int interruption) {
        android.graphics.RectF bounds = ViewportChecks.bounds(view);
        float x = bounds.centerX(), y = bounds.centerY();
        long start = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(start, start, MotionEvent.ACTION_DOWN, x, y, 0);
        getInstrumentation().sendPointerSync(down); down.recycle();
        if (interruption == 1) {
            MotionEvent event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, x, y, 0);
            getInstrumentation().sendPointerSync(event); event.recycle();
        }
        if (interruption == 2) {
            MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[2];
            MotionEvent.PointerCoords[] positions = new MotionEvent.PointerCoords[2];
            for (int i = 0; i < 2; i++) {
                properties[i] = new MotionEvent.PointerProperties(); properties[i].id = i;
                properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
                positions[i] = new MotionEvent.PointerCoords(); positions[i].x = x + i; positions[i].y = y;
                positions[i].pressure = 1; positions[i].size = 1;
            }
            for (int action : new int[]{MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP}) {
                MotionEvent event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action | 1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT,
                        2, properties, positions, 0, 0, 1, 1, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0);
                getInstrumentation().sendPointerSync(event); event.recycle();
            }
        }
        SystemClock.sleep(BinaryPress.timeout() + 60);
        MotionEvent up = MotionEvent.obtain(start, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0);
        getInstrumentation().sendPointerSync(up); up.recycle();
        getInstrumentation().waitForIdleSync(); SystemClock.sleep(120);
    }

    public void testOrganizedGameSettingsAndPinnedMenus() throws Exception {
        java.util.Map<Mode, Integer> prior = new java.util.EnumMap<>(Mode.class);
        for (Mode mode : Mode.values()) prior.put(mode, settings().contains(mode.settingKey()) ? settings().getInt(mode.settingKey(), 0) : null);
        try {
            activity = getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(), MainActivity.class)
                    .putExtra(MainActivity.OPEN_SETTINGS, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            settle();
            ViewportChecks.navigation(activity, require(findDescription("Close settings")), null);
            View export = require(findDescription("Export puzzle records")), imports = require(findDescription("Import curated collection"));
            ViewGroup list = (ViewGroup) export.getParent();
            assertEquals("Import/export end the entire settings list", list.getChildCount() - 1, list.indexOfChild(imports));
            assertEquals(list.indexOfChild(imports) - 1, list.indexOfChild(export));
            assertNotNull(tagged("game-settings-SUDOKU")); assertNotNull(tagged("game-settings-DAILY_WORD"));
            for (Mode mode : Mode.values()) {
                write(mode, fixture(mode, "initial")); byte[] original = bytes(mode);
                tap(require(tagged("game-settings-" + mode.name()))); settle();
                ViewportChecks.navigation(activity, require(findDescription("Back to settings")), null);
                for (int value : mode.settings()) {
                    Button choice = (Button) require(tagged("setting-choice-" + value));
                    assertEquals(14f, choice.getTextSize() / activity.getResources().getDisplayMetrics().scaledDensity, .1f);
                    if (mode.settings().length > 1) {
                        tap(choice); assertEquals(value, settings().getInt(mode.settingKey(), -1));
                        assertTrue(tagged("setting-choice-" + value).isSelected());
                    } else assertFalse("Fixed configurations do not invent a setting toggle", choice.isEnabled());
                    assertTrue("Preference selection never replaces current puzzle", Arrays.equals(original, bytes(mode)));
                }
                capture("puzzles-settings-" + mode.name().toLowerCase(java.util.Locale.ROOT) + ".png");
                tap(require(findDescription("Back to settings"))); settle();
            }
            close();
            for (Mode mode : Mode.values()) {
                activity = getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(), PuzzleMenuActivity.class)
                        .putExtra(PuzzleActivity.MODE, mode.name()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                menuReady(mode);
                ViewportChecks.navigation(activity, require(findDescription("Back to Liddle Puzzles")), require(findDescription("Settings")));
                for (int value : mode.settings()) {
                    Button choice = (Button) require(tagged("new-setting-" + value));
                    assertEquals(14f, choice.getTextSize() / activity.getResources().getDisplayMetrics().scaledDensity, .1f);
                }
                if (mode == Mode.NONOGRAM) capture("puzzles-settings-menu.png");
                close();
            }
        } finally {
            SharedPreferences.Editor edit = settings().edit();
            for (Mode mode : Mode.values()) {
                if (prior.get(mode) == null) edit.remove(mode.settingKey()); else edit.putInt(mode.settingKey(), prior.get(mode));
            }
            assertTrue(edit.commit());
        }
    }

    public void testNumberlinkSixOnlyMenuAndLegacyResume() throws Exception {
        Mode mode = Mode.NUMBERLINK;
        boolean hadSetting = settings().contains(mode.settingKey());
        int setting = settings().getInt(mode.settingKey(), 6);
        try {
            for (int pairs : new int[]{3, 4}) {
                Numberlink legacy = Numberlink.generate(pairs, new Random(20261121 + pairs), new Generation());
                Numberlink edited = legacy.copy();
                for (int edge = 0; edge < edited.edges.length; edge++)
                    if (edited.toggle(edited.grid.a[edge], edited.grid.b[edge]) == null && edited.edges[edge] == 1) break;
                PuzzleProgress old = new PuzzleProgress(legacy).edited(edited).withElapsed(60000);
                assertEquals(1, old.undoCount()); write(mode, PuzzleStore.encode(old));
                assertTrue(settings().edit().putInt(mode.settingKey(), pairs).commit());
                activity = getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(), PuzzleMenuActivity.class)
                        .putExtra(PuzzleActivity.MODE, mode.name()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                menuReady(mode);
                assertNull(tagged("new-setting-3")); assertNull(tagged("new-setting-4"));
                assertNull(tagged("new-setting-8")); assertNull(tagged("new-setting-10"));
                assertTrue("Old preferences select the sole new size", tagged("new-setting-6").isSelected());
                assertTrue(tagged("puzzle-resume").getContentDescription().toString().contains("4 \u00d7 4"));
                capture("puzzles-numberlink-sixonly-menu-" + pairs + ".png");
                tap(require(tagged("new-setting-6"))); assertTrue(windowText("Keep")); tapWindow("Keep");
                assertTrue(Arrays.equals(PuzzleStore.encode(old.withElapsed(0)), bytes(mode)));
                Activity menu = activity;
                Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(PuzzleActivity.class.getName(), null, false);
                tap(require(tagged("puzzle-resume"))); activity = monitor.waitForActivityWithTimeout(5000);
                getInstrumentation().removeMonitor(monitor); assertNotNull(activity); ready(mode);
                assertEquals(4, read(mode).size()); assertEquals(pairs, ((Numberlink) read(mode)).pairs());
                assertEquals(1, progress(mode).undoCount());
                tap(require(findDescription("Undo last move")));
                assertTrue(Arrays.equals(PuzzleStore.encode(legacy), bytes(mode)));
                tap(require(findDescription("Back to " + mode.title + " menu"))); activity = menu; menuReady(mode);
                monitor = getInstrumentation().addMonitor(PuzzleActivity.class.getName(), null, false);
                tap(require(tagged("new-setting-6"))); tapWindow("Replace");
                activity = monitor.waitForActivityWithTimeout(5000); getInstrumentation().removeMonitor(monitor);
                assertNotNull(activity); ready(mode); assertEquals(6, read(mode).size());
                assertEquals(5, ((Numberlink) read(mode)).pairs()); assertEquals(0, progress(mode).undoCount());
                assertEquals(6, settings().getInt(mode.settingKey(), -1));
                capture("puzzles-numberlink-sixonly-" + pairs + ".png"); close();
            }
        } finally {
            SharedPreferences.Editor edit = settings().edit();
            if (hadSetting) edit.putInt(mode.settingKey(), setting); else edit.remove(mode.settingKey());
            assertTrue(edit.commit());
        }
    }

    public void testLargerNumberlinkDragFitAndPersistence() throws Exception {
        Mode mode = Mode.NUMBERLINK;
        Numberlink board = (Numberlink) PuzzleStore.decode(mode, Files.readAllBytes(fixtureFile("NUMBERLINK6-initial.bin").toPath()));
        Numberlink solution = (Numberlink) PuzzleStore.decode(mode, Files.readAllBytes(fixtureFile("NUMBERLINK6-complete.bin").toPath()));
        int[] trail = new int[4]; while (board.endpoints[trail[0]] == 0) trail[0]++;
        int previous = -1;
        for (int i = 1; i < trail.length; i++) {
            int next = -1;
            for (int edge : solution.grid.at[trail[i - 1]]) if (solution.edges[edge] == 1
                    && solution.grid.other(edge, trail[i - 1]) != previous) next = solution.grid.other(edge, trail[i - 1]);
            assertTrue(next >= 0); previous = trail[i - 1]; trail[i] = next;
        }
        StringBuilder times = new StringBuilder("seed,generation_ms\n");
        for (int seed = 0; seed < 3; seed++) {
            long start = SystemClock.elapsedRealtime();
            Numberlink generated = Numberlink.generate(6, new Random(2026111800L + seed), new Generation());
            times.append(seed).append(',').append(SystemClock.elapsedRealtime() - start).append('\n');
            assertEquals(6, generated.size()); assertEquals(1, Numberlink.count(generated.endpoints, 2, new Generation()));
        }
        Files.write(new File(getInstrumentation().getTargetContext().getExternalFilesDir(null),
                "puzzles-numberlink-six-generation.csv").toPath(), times.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        for (int orientation : new int[]{ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE})
            for (float scale : new float[]{1f, 2f}) {
                assertTrue(settings().edit().putInt("appearance", scale == 1 ? 1 : 2).commit());
                write(mode, PuzzleStore.encode(board)); launch(mode); rotate(orientation);
                Configuration prior = new Configuration(activity.getResources().getConfiguration());
                try {
                    getInstrumentation().runOnMainSync(() -> {
                        Configuration config = new Configuration(prior); config.fontScale = scale;
                        activity.getResources().updateConfiguration(config, activity.getResources().getDisplayMetrics());
                        try {
                            for (String name : new String[]{"build", "showBoard"}) {
                                Method method = PuzzleActivity.class.getDeclaredMethod(name); method.setAccessible(true); method.invoke(activity);
                            }
                            Method refresh = PuzzleActivity.class.getDeclaredMethod("refresh", String.class);
                            refresh.setAccessible(true); refresh.invoke(activity, (String) null);
                        } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
                    });
                    getInstrumentation().waitForIdleSync(); ViewportChecks.all(activity); assertCentered();
                    assertEquals(36, ((ViewGroup) tagged("puzzle-board")).getChildCount());
                    android.graphics.RectF bounds = ViewportChecks.bounds(tagged("puzzle-board"));
                    dragPath(trail, false);
                    assertEquals(3, Arrays.stream(((Numberlink) read(mode)).edges).sum()); assertEquals(1, progress(mode).undoCount());
                    assertEquals(bounds, ViewportChecks.bounds(tagged("puzzle-board")));
                    capture("puzzles-numberlink-six-" + orientation + "-" + (int) scale + ".png");
                    dragPath(new int[]{trail[0], trail[1]}, true);
                    assertEquals(1, progress(mode).undoCount()); assertEquals(3, Arrays.stream(((Numberlink) read(mode)).edges).sum());
                    tap(require(findDescription("Undo last move")));
                    assertEquals(0, Arrays.stream(((Numberlink) read(mode)).edges).sum());
                    tap(pathSquare(trail[0])); tap(pathSquare(trail[1]));
                    assertEquals(1, progress(mode).undoCount());
                    assertEquals(1, ((Numberlink) read(mode)).edges[board.grid.between(trail[0], trail[1])]);
                } finally {
                    getInstrumentation().runOnMainSync(() -> activity.getResources().updateConfiguration(prior, activity.getResources().getDisplayMetrics()));
                    close();
                }
                launch(mode); assertEquals(6, read(mode).size()); assertEquals(1, progress(mode).undoCount());
                tap(require(findDescription("Undo last move"))); assertEquals(0, Arrays.stream(((Numberlink) read(mode)).edges).sum()); close();
            }
    }

    public void testMinesStableLossAndMotion() throws Exception {
        Mode mode = Mode.MINESWEEPER;
        for (int orientation : new int[]{ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE})
            for (float scale : new float[]{1f, 2f}) {
                assertTrue(settings().edit().putInt("appearance", scale == 1 ? 1 : 2).commit());
                Minesweeper initial = new Minesweeper(2026111901L, scale == 1 ? 10 : 14);
                write(mode, PuzzleStore.encode(initial)); launch(mode); rotate(orientation);
                Configuration prior = new Configuration(activity.getResources().getConfiguration());
                try {
                    getInstrumentation().runOnMainSync(() -> {
                        Configuration changed = new Configuration(prior); changed.fontScale = scale;
                        activity.getResources().updateConfiguration(changed, activity.getResources().getDisplayMetrics());
                        try {
                            for (String name : new String[]{"build", "showBoard"}) {
                                Method method = PuzzleActivity.class.getDeclaredMethod(name); method.setAccessible(true); method.invoke(activity);
                            }
                            Method refresh = PuzzleActivity.class.getDeclaredMethod("refresh", String.class);
                            refresh.setAccessible(true); refresh.invoke(activity, (String) null);
                        } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
                    });
                    getInstrumentation().waitForIdleSync(); ViewportChecks.all(activity);
                    java.util.Map<Integer, android.graphics.RectF> bounds = mineBounds();
                    assertFalse(tagged("puzzle-input").isSelected());
                    tap(tagged("puzzle-input")); assertTrue(tagged("puzzle-input").isSelected());
                    tap(square(0)); assertEquals(1, ((Minesweeper) read(mode)).flags());
                    assertEquals(bounds, mineBounds());
                    tap(square(0)); tap(tagged("puzzle-input")); assertFalse(tagged("puzzle-input").isSelected()); tap(square(27));
                    Minesweeper revealed = (Minesweeper) read(mode);
                    assertEquals(0, revealed.adjacent(27)); assertFalse(revealed.lost());
                    assertEquals(bounds, mineBounds());
                    int target = 0; while (revealed.mines[target] == 0) target++;
                    tap(square(target)); assertTrue(read(mode).lost());
                    if (orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT && scale == 1) {
                        StringBuilder times = new StringBuilder("file,capturedUptimeMillis\n");
                        for (int frame = 0; frame < 5; frame++) {
                            String name = "puzzles-mines-motion-" + frame + ".png";
                            times.append(name).append(',').append(SystemClock.uptimeMillis()).append('\n');
                            Bitmap image = getInstrumentation().getUiAutomation().takeScreenshot(); assertNotNull(image);
                            try (FileOutputStream stream = new FileOutputStream(new File(
                                    getInstrumentation().getTargetContext().getExternalFilesDir(null), name))) {
                                assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, stream));
                            }
                            image.recycle(); SystemClock.sleep(40);
                        }
                        Files.write(new File(getInstrumentation().getTargetContext().getExternalFilesDir(null),
                                "puzzles-mines-motion.csv").toPath(), times.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                    }
                    assertEquals("Loss never changes board, cell, clock or controls geometry", bounds, mineBounds());
                    assertEquals("Mine hit", ((TextView) tagged("puzzle-feedback")).getText().toString());
                    assertTrue(square(target).getContentDescription().toString().contains("detonated mine, game lost"));
                    assertEquals("*!", ((TextView) square(target)).getText().toString());
                    boolean dark = (activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
                    MainActivity.Palette palette = new MainActivity.Palette(dark, settings().getInt("accentColor", 3));
                    assertEquals(palette.error, ((TextView) square(target)).getCurrentTextColor());
                    assertEquals(palette.error, ((TextView) tagged("puzzle-feedback")).getCurrentTextColor());
                    double errorInk = android.graphics.Color.luminance(palette.error);
                    double errorFill = android.graphics.Color.luminance(palette.errorFill);
                    assertTrue("Loss text contrast", (Math.max(errorInk, errorFill) + .05)
                            / (Math.min(errorInk, errorFill) + .05) >= 4.5);
                    assertNull("Loss never celebrates a win", tagged("completion-effect"));
                    capture("puzzles-mines-loss-" + orientation + "-" + (int) scale + ".png");
                    long frozen = progress(mode).elapsedMillis; SystemClock.sleep(1100);
                    assertEquals(frozen, progress(mode).elapsedMillis);
                    java.lang.reflect.Field field = PuzzleActivity.class.getDeclaredField("minesFeedback"); field.setAccessible(true);
                    MinesFeedback feedback = (MinesFeedback) field.get(activity);
                    java.util.List<Button> buttons = new java.util.ArrayList<>();
                    for (int cell = 0; cell < 64; cell++) buttons.add((Button) square(cell));
                    Minesweeper loss = (Minesweeper) read(mode);
                    getInstrumentation().runOnMainSync(() -> feedback.changed(revealed, loss, buttons, false));
                    assertFalse("Disabled-motion branch leaves a steady result", feedback.active());
                    assertEquals(bounds, mineBounds());
                    for (Button button : buttons) assertEquals(1f, button.getAlpha(), .001f);
                    close(); launch(mode);
                    field = PuzzleActivity.class.getDeclaredField("minesFeedback"); field.setAccessible(true);
                    assertFalse("Restored loss never replays motion", ((MinesFeedback) field.get(activity)).active());
                    assertEquals(frozen, progress(mode).elapsedMillis);
                    assertNull(tagged("completion-effect"));
                } finally {
                    if (activity != null) getInstrumentation().runOnMainSync(() ->
                            activity.getResources().updateConfiguration(prior, activity.getResources().getDisplayMetrics()));
                    close();
                }
            }
    }

    private java.util.Map<Integer, android.graphics.RectF> mineBounds() {
        java.util.Map<Integer, android.graphics.RectF> result = new java.util.HashMap<>();
        for (int cell = 0; cell < 64; cell++) result.put(cell, ViewportChecks.bounds(square(cell)));
        int index = 64;
        for (String tag : new String[]{"puzzle-board", "puzzle-input", "elapsed-time"}) result.put(index++, ViewportChecks.bounds(tagged(tag)));
        return result;
    }

    public void testNonogramSizesFitAndKeepExactProgress() throws Exception {
        Mode mode = Mode.NONOGRAM;
        for (int size : mode.settings()) {
            long maximum = 0, total = 0; Nonogram picture = null;
            for (int seed = 0; seed < 3; seed++) {
                long start = SystemClock.elapsedRealtime();
                picture = Nonogram.generate(size, new java.util.Random(2026111900L + seed), new com.quietgrid.sudoku.puzzles.Generation());
                long elapsed = SystemClock.elapsedRealtime() - start; total += elapsed; maximum = Math.max(maximum, elapsed);
                assertEquals(1, Nonogram.count(picture.rows, picture.columns, 2, new com.quietgrid.sudoku.puzzles.Generation()));
            }
            Bundle metrics = new Bundle();
            metrics.putString("stream", "\nNative Nonogram " + size + ": 3 distinct-seed samples, mean " + (total / 3) + " ms, max " + maximum + " ms\n");
            getInstrumentation().sendStatus(2, metrics);
            assertTrue(maximum < 10_000);
            write(mode, PuzzleStore.encode(new PuzzleProgress(picture).withElapsed(12_345))); launch(mode);
            for (int orientation : new int[]{ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}) {
                rotate(orientation);
                Configuration prior = new Configuration(activity.getResources().getConfiguration());
                try {
                    for (float scale : new float[]{1f, 2f}) {
                        getInstrumentation().runOnMainSync(() -> {
                            Configuration changed = new Configuration(prior); changed.fontScale = scale;
                            activity.getResources().updateConfiguration(changed, activity.getResources().getDisplayMetrics());
                            try {
                                for (String name : new String[]{"build", "showBoard"}) {
                                    Method method = PuzzleActivity.class.getDeclaredMethod(name); method.setAccessible(true); method.invoke(activity);
                                }
                                Method refresh = PuzzleActivity.class.getDeclaredMethod("refresh", String.class);
                                refresh.setAccessible(true); refresh.invoke(activity, (String) null);
                            } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
                        });
                        getInstrumentation().waitForIdleSync(); SystemClock.sleep(200);
                        ViewportChecks.all(activity); assertCentered();
                        for (String axis : new String[]{"row", "column"}) for (int line = 0; line < size; line++) {
                            TextView clue = (TextView) tagged("nonogram-" + axis + "-" + line);
                            assertNotNull(clue); assertNotNull(clue.getLayout());
                            assertTrue("Every clue line is fully measured", clue.getLayout().getHeight()
                                    <= clue.getHeight() - clue.getCompoundPaddingTop() - clue.getCompoundPaddingBottom());
                            for (int textLine = 0; textLine < clue.getLineCount(); textLine++)
                                assertTrue(clue.getLayout().getLineWidth(textLine) <= clue.getWidth() - clue.getCompoundPaddingLeft() - clue.getCompoundPaddingRight() + 1);
                        }
                        byte[] before = bytes(mode);
                        android.graphics.RectF bounds = ViewportChecks.bounds(tagged("puzzle-board"));
                        tap(square(size * size - 1));
                        assertEquals(1, ((Nonogram) read(mode)).values[size * size - 1]);
                        assertEquals(bounds, ViewportChecks.bounds(tagged("puzzle-board")));
                        tap(require(findDescription("Undo last move")));
                        assertTrue(Arrays.equals(before, bytes(mode)));
                        capture("puzzles-nonogram-size-" + size + "-" + orientation + "-" + (int) scale + ".png");
                    }
                } finally { getInstrumentation().runOnMainSync(() ->
                        activity.getResources().updateConfiguration(prior, activity.getResources().getDisplayMetrics())); }
            }
            close();
        }
    }

    public void testHubPuzzleIconsColorsAndRouting() throws Exception {
        boolean hadAccent = settings().contains("accentColor");
        int accent = settings().getInt("accentColor", 3);
        try {
            for (int look : new int[]{1, 2}) {
                assertTrue(settings().edit().putInt("appearance", look).commit());
                activity = getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(), MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                settle();
                for (int color = 0; color < 8; color++) {
                    assertTrue(settings().edit().putInt("accentColor", color).commit());
                    getInstrumentation().runOnMainSync(() -> {
                        try {
                            Method method = MainActivity.class.getDeclaredMethod("render", boolean.class);
                            method.setAccessible(true); method.invoke(activity, false);
                        } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
                    });
                    getInstrumentation().waitForIdleSync();
                    java.util.Set<Integer> fills = new java.util.HashSet<>();
                    int height = -1;
                    for (String name : new String[]{"Sudoku", "Daily Word", "Takuzu", "Nonograms", "Futoshiki", "Minesweeper", "Numberlink", "Slitherlink"}) {
                        Button button = (Button) require(findText(name));
                        assertEquals(name, button.getContentDescription().toString());
                        assertEquals("hub-puzzle", button.getTag());
                        assertNotNull(button.getCompoundDrawablesRelative()[0]);
                        assertEquals(24 * button.getResources().getDisplayMetrics().density,
                                button.getCompoundDrawablesRelative()[0].getBounds().width(), 1);
                        if (height < 0) height = button.getHeight();
                        assertEquals("All game choices share geometry", height, button.getHeight());
                        int fill = squareFill(button); fills.add(fill);
                        double a = android.graphics.Color.luminance(button.getCurrentTextColor());
                        double b = android.graphics.Color.luminance(fill);
                        assertTrue("Hub text/icon contrast " + name, (Math.max(a, b) + .05) / (Math.min(a, b) + .05) >= 4.5);
                    }
                    assertEquals("Eight stable, distinct game fills", 8, fills.size());
                    ViewportChecks.controls(activity.getWindow().getDecorView());
                }
                capture("puzzles-hub-icons-" + look + ".png");
                assertOnlyHeadings(getInstrumentation().getTargetContext().getString(R.string.app_name));
                close();
            }
            activity = getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(), MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)); settle();
            Activity hub = activity;
            tap(require(findText("Sudoku"))); settle(); assertNotNull(findText("Difficulty"));
            tap(require(findDescription(getInstrumentation().getTargetContext().getString(R.string.back_to_puzzles)))); settle();
            Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(DailyWordActivity.class.getName(), null, false);
            tap(require(findText("Daily Word"))); activity = monitor.waitForActivityWithTimeout(5000);
            getInstrumentation().removeMonitor(monitor); assertNotNull(activity); close(); activity = hub; settle();
            for (Mode mode : Mode.values()) {
                monitor = getInstrumentation().addMonitor(PuzzleMenuActivity.class.getName(), null, false);
                tap(require(findText(mode.title))); activity = monitor.waitForActivityWithTimeout(5000);
                getInstrumentation().removeMonitor(monitor); assertNotNull(activity); menuReady(mode);
                close(); activity = hub; settle();
            }
        } finally {
            SharedPreferences.Editor edit = settings().edit();
            if (hadAccent) edit.putInt("accentColor", accent); else edit.remove("accentColor");
            assertTrue(edit.commit());
        }
    }

    public void testMenusHaveNoButtonSubtitles() throws Exception {
        activity = getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        settle();
        Activity hub = activity;
        assertOnlyHeadings(getInstrumentation().getTargetContext().getString(R.string.app_name));
        assertNotNull(findDescription("Daily Word"));
        ViewportChecks.controls(activity.getWindow().getDecorView());
        capture("puzzles-no-subtitles-hub.png");
        tap(require(findText("Sudoku"))); settle();
        assertOnlyHeadings("Sudoku", "Difficulty");
        ViewportChecks.controls(activity.getWindow().getDecorView());
        capture("puzzles-no-subtitles-sudoku.png");
        tap(require(findDescription("Settings"))); settle();
        assertOnlyHeadings("Sudoku", "Auto notes", "Feedback");
        ViewportChecks.controls(activity.getWindow().getDecorView());
        assertNotNull(findDescription("Show mistakes").getTooltipText());
        capture("puzzles-no-subtitles-settings.png");
        tap(require(findDescription("Back to settings"))); settle();
        tap(require(findDescription("Close settings"))); settle();
        tap(require(findDescription(getInstrumentation().getTargetContext().getString(R.string.back_to_puzzles)))); settle();
        for (Mode mode : Mode.values()) {
            write(mode, fixture(mode, "initial"));
            Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(PuzzleMenuActivity.class.getName(), null, false);
            tap(require(findText(mode.title)));
            activity = monitor.waitForActivityWithTimeout(5000); getInstrumentation().removeMonitor(monitor);
            assertNotNull(activity); menuReady(mode);
            assertOnlyHeadings(mode.title, "New puzzle");
            ViewportChecks.controls(activity.getWindow().getDecorView());
            Button resume = (Button) require(tagged("puzzle-resume"));
            assertTrue("Saved setting remains accessible", resume.getContentDescription().toString()
                    .contains(mode.settingLabel(mode.settingOf(read(mode)))));
            for (int setting : mode.settings()) {
                Button choice = (Button) require(tagged("new-setting-" + setting));
                if (mode == Mode.NUMBERLINK) assertEquals(setting == 6 ? "6\u00d76" : setting + " pairs", choice.getText().toString());
                if (mode == Mode.MINESWEEPER) assertEquals(setting + " mines", choice.getText().toString());
            }
            capture("puzzles-no-subtitles-" + mode.name().toLowerCase(java.util.Locale.ROOT) + ".png");
            tap(require(findDescription("Back to Liddle Puzzles"))); activity = hub; settle();
        }
    }

    private void assertOnlyHeadings(String... allowed) {
        java.util.Set<String> headings = new java.util.HashSet<>(Arrays.asList(allowed));
        View unexpected = find(activity.findViewById(android.R.id.content), view -> view instanceof TextView
                && !(view instanceof Button) && view.getVisibility() == View.VISIBLE
                && ((TextView) view).getText().length() > 0
                && !headings.contains(((TextView) view).getText().toString()));
        assertNull("Only genuine headings remain outside controls, not button subtitles"
                + (unexpected instanceof TextView ? ": " + ((TextView) unexpected).getText() : ""), unexpected);
    }

    public void testModeMenusSettingsAndCenteredBoards() throws Exception {
        java.util.Map<Mode, byte[]> baselines = new java.util.EnumMap<>(Mode.class);
        for (Mode mode : Mode.values()) {
            write(mode, PuzzleStore.encode(PuzzleStore.decodeProgress(mode, fixture(mode, "initial")).withElapsed(60_000)));
            baselines.put(mode, bytes(mode));
        }
        for (Mode mode : Mode.values()) {
            activity = getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(), MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            getInstrumentation().waitForIdleSync();
            android.app.Instrumentation.ActivityMonitor menuMonitor = getInstrumentation().addMonitor(PuzzleMenuActivity.class.getName(), null, false);
            tap(require(findText(mode.title)));
            Activity menu = menuMonitor.waitForActivityWithTimeout(5000); getInstrumentation().removeMonitor(menuMonitor);
            assertNotNull("Hub opens the mode submenu", menu); activity = menu; menuReady(mode);
            ViewportChecks.controls(activity.getWindow().getDecorView());
            assertNotNull(tagged("puzzle-resume"));
            int choice = mode.settings()[mode.settings().length - 1];
            tap(require(tagged("new-setting-" + choice)));
            assertTrue(windowText("Keep")); tapWindow("Keep");
            assertTrue("Cancel leaves old board/history intact", Arrays.equals(baselines.get(mode), bytes(mode)));
            capture("puzzles-menu-" + mode.name().toLowerCase(java.util.Locale.ROOT) + ".png");
            if (mode == Mode.NUMBERLINK) {
                android.app.Instrumentation.ActivityMonitor settingsMonitor = getInstrumentation().addMonitor(MainActivity.class.getName(), null, false);
                tap(require(findDescription("Settings")));
                Activity settingsActivity = settingsMonitor.waitForActivityWithTimeout(5000); getInstrumentation().removeMonitor(settingsMonitor);
                assertNotNull(settingsActivity); activity = settingsActivity; getInstrumentation().waitForIdleSync();
                ViewportChecks.controls(activity.getWindow().getDecorView());
                tap(require(findDescription("Back to settings")));
                tap(require(findDescription("Close settings"))); activity = menu; menuReady(mode);
            }
            android.app.Instrumentation.ActivityMonitor gameMonitor = getInstrumentation().addMonitor(PuzzleActivity.class.getName(), null, false);
            tap(require(tagged("puzzle-resume")));
            activity = gameMonitor.waitForActivityWithTimeout(5000); getInstrumentation().removeMonitor(gameMonitor);
            assertNotNull(activity); ready(mode);
            assertTrue("Resume keeps legacy settings and entries", Arrays.equals(baselines.get(mode), bytes(mode)));
            ViewportChecks.all(activity); assertCentered();
            tap(require(findDescription("Back to " + mode.title + " menu"))); activity = menu; menuReady(mode);
            long frozen = progress(mode).elapsedMillis;
            assertTrue(frozen >= 60_000); SystemClock.sleep(1100);
            assertEquals("Mode menu time never counts as active play", frozen, progress(mode).elapsedMillis);
            gameMonitor = getInstrumentation().addMonitor(PuzzleActivity.class.getName(), null, false);
            tap(require(tagged("new-setting-" + choice))); tapWindow("Replace");
            activity = gameMonitor.waitForActivityWithTimeout(5000); getInstrumentation().removeMonitor(gameMonitor);
            assertNotNull(activity); ready(mode);
            assertEquals(choice, mode.settingOf(read(mode)));
            assertEquals("Chosen new setting persists", choice, settings().getInt(mode.settingKey(), -1));
            assertEquals(0, progress(mode).undoCount());
            assertTrue("A confirmed successful replacement resets elapsed", progress(mode).elapsedMillis < 10_000);
            ViewportChecks.all(activity); assertCentered();
            if (mode == Mode.NUMBERLINK) {
                Numberlink link = (Numberlink) read(mode);
                assertEquals(6, link.size()); assertEquals(5, link.pairs());
                java.util.Set<Integer> colors = new java.util.HashSet<>();
                for (int pair = 1; pair <= link.pairs(); pair++) {
                    int target = 0; while (((Numberlink) read(mode)).endpoints[target] != pair) target++;
                    colors.add(((TextView) pathSquare(target)).getCurrentTextColor());
                }
                assertEquals(5, colors.size());
            }
            if (mode == Mode.MINESWEEPER) {
                tap(square(27)); Minesweeper mines = (Minesweeper) read(mode);
                assertEquals(14, Arrays.stream(mines.mines).sum()); assertEquals(0, mines.adjacent(27)); assertFalse(mines.lost());
            }
            capture("puzzles-setting-" + mode.name().toLowerCase(java.util.Locale.ROOT) + ".png");
            baselines.put(mode, bytes(mode));
            for (Mode other : Mode.values()) if (other != mode) assertTrue("Other modes remain isolated", Arrays.equals(baselines.get(other), bytes(other)));
            tap(require(findDescription("Back to " + mode.title + " menu"))); activity = menu; menuReady(mode);
            tap(require(findDescription("Back to Liddle Puzzles"))); close();
        }
    }

    private void menuReady(Mode mode) {
        for (int attempt = 0; attempt < 100; attempt++) {
            getInstrumentation().waitForIdleSync();
            View choice = tagged("new-setting-" + mode.settings()[0]);
            if (choice != null && choice.isEnabled() && activity.hasWindowFocus()) return;
            SystemClock.sleep(50);
        }
        fail("Mode submenu did not finish its bounded save read");
    }

    private void assertCentered() {
        View board = tagged("puzzle-board");
        android.graphics.RectF bounds = ViewportChecks.bounds(board), group = ViewportChecks.bounds((View) board.getParent());
        assertEquals("Complete board/clue group is centered, not just Canvas ink", group.centerX(), bounds.centerX(), 2f);
    }

    public void testQuietSlitherlinkFeedbackAndUndo() throws Exception {
        Mode mode = Mode.SLITHERLINK;
        Slitherlink initial = (Slitherlink) decodeFixture(mode, "initial");
        Arrays.fill(initial.clues, -1); Arrays.fill(initial.edges, 0); initial.clues[0] = 0;
        int edge = Slitherlink.SIDES[0][0];
        for (int look : new int[]{1, 2}) {
            assertTrue(settings().edit().putInt("appearance", look).commit());
            write(mode, PuzzleStore.encode(initial)); launch(mode);
            android.graphics.RectF before = ViewportChecks.bounds(tagged("puzzle-board"));
            tapSlitherEdge(edge);
            assertTrue(((Slitherlink) read(mode)).errors().any());
            assertEquals(View.GONE, tagged("puzzle-feedback").getVisibility());
            assertEquals("", ((TextView) tagged("puzzle-feedback")).getText().toString());
            assertEquals(before, ViewportChecks.bounds(tagged("puzzle-board")));
            java.lang.reflect.Field field = PuzzleActivity.class.getDeclaredField("loopClues"); field.setAccessible(true);
            TextView clue = (TextView) ((java.util.List<?>) field.get(activity)).get(0);
            assertFalse("No visible error prose or !", clue.getText().toString().contains("!") || clue.getText().toString().contains("Error"));
            assertTrue("Accessible localized reason remains", clue.getContentDescription().toString().contains("Error:"));
            capture("puzzles-slitherlink-quiet-error-" + look + ".png");
            tapSlitherEdge(edge);
            assertFalse(((Slitherlink) read(mode)).errors().any());
            tap(require(findDescription("Undo last move")));
            assertTrue(((Slitherlink) read(mode)).errors().any());
            assertEquals(View.GONE, tagged("puzzle-feedback").getVisibility());
            tap(require(findText("Cell clues")));
            AccessibilityNodeInfo node = activeNode(clue.getContentDescription().toString());
            assertNotNull(node);
            close();
        }
    }

    public void testFutoshikiInputSpacingAndFit() throws Exception {
        write(Mode.FUTOSHIKI, fixture(Mode.FUTOSHIKI, "initial")); launch(Mode.FUTOSHIKI);
        for (int orientation : new int[]{ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}) {
            rotate(orientation);
            for (float scale : new float[]{1f, 2f}) {
                Configuration prior = new Configuration(activity.getResources().getConfiguration());
                try {
                    getInstrumentation().runOnMainSync(() -> {
                        Configuration changed = new Configuration(prior); changed.fontScale = scale;
                        activity.getResources().updateConfiguration(changed, activity.getResources().getDisplayMetrics());
                        try {
                            for (String name : new String[]{"build", "showBoard"}) {
                                Method method = PuzzleActivity.class.getDeclaredMethod(name); method.setAccessible(true); method.invoke(activity);
                            }
                            Method refresh = PuzzleActivity.class.getDeclaredMethod("refresh", String.class);
                            refresh.setAccessible(true); refresh.invoke(activity, (String) null);
                        } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
                    });
                    getInstrumentation().waitForIdleSync(); SystemClock.sleep(250);
                    ViewportChecks.all(activity); assertCentered();
                    View key = require(tagged("digit-1"));
                    android.graphics.RectF board = ViewportChecks.bounds(tagged("puzzle-board"));
                    android.graphics.RectF bank = ViewportChecks.bounds((View) key.getParent());
                    float composedScale = board.width() / require(tagged("puzzle-board")).getWidth();
                    float minimumGap = 16 * activity.getResources().getDisplayMetrics().density * composedScale;
                    float actualGap = orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            ? bank.top - board.bottom : bank.left - board.right;
                    assertTrue("Grid/inequalities separated from inputs: " + actualGap + " < " + minimumGap,
                            actualGap >= minimumGap - 1);
                    Futoshiki puzzle = (Futoshiki) read(Mode.FUTOSHIKI);
                    int target = 0; while (puzzle.clues[target] != 0) target++;
                    tap(square(target)); tap(key);
                    assertEquals(board, ViewportChecks.bounds(tagged("puzzle-board")));
                    assertEquals(bank, ViewportChecks.bounds((View) key.getParent()));
                    tap(require(findDescription("Undo last move")));
                    assertEquals(board, ViewportChecks.bounds(tagged("puzzle-board")));
                    assertEquals(bank, ViewportChecks.bounds((View) key.getParent()));
                    ViewportChecks.all(activity);
                    capture("puzzles-futoshiki-spacing-" + orientation + "-" + (int) scale + ".png");
                } finally {
                    getInstrumentation().runOnMainSync(() ->
                            activity.getResources().updateConfiguration(prior, activity.getResources().getDisplayMetrics()));
                }
            }
        }
    }

    public void testLargerSlitherlinkFitInputAndPersistence() throws Exception {
        Mode mode = Mode.SLITHERLINK;
        for (int n : new int[]{6, 8}) {
            byte[] initial = Files.readAllBytes(fixtureFile("SLITHERLINK" + n + "-initial.bin").toPath());
            write(mode, initial); launch(mode);
            for (int orientation : new int[]{ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}) {
                rotate(orientation);
                for (float scale : new float[]{1f, 2f}) {
                    Configuration prior = new Configuration(activity.getResources().getConfiguration());
                    try {
                        getInstrumentation().runOnMainSync(() -> {
                            Configuration changed = new Configuration(prior); changed.fontScale = scale;
                            activity.getResources().updateConfiguration(changed, activity.getResources().getDisplayMetrics());
                            try {
                                for (String name : new String[]{"build", "showBoard"}) {
                                    Method method = PuzzleActivity.class.getDeclaredMethod(name); method.setAccessible(true); method.invoke(activity);
                                }
                                Method refresh = PuzzleActivity.class.getDeclaredMethod("refresh", String.class);
                                refresh.setAccessible(true); refresh.invoke(activity, (String) null);
                            } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
                        });
                        getInstrumentation().waitForIdleSync(); SystemClock.sleep(200);
                        ViewportChecks.all(activity); assertCentered();
                        android.graphics.RectF board = ViewportChecks.bounds(tagged("puzzle-board"));
                        int edge = ((Slitherlink) read(mode)).edges.length - 1;
                        tapSlitherEdge(edge); assertEquals(1, ((Slitherlink) read(mode)).edges[edge]);
                        tapSlitherEdge(edge); assertEquals(2, ((Slitherlink) read(mode)).edges[edge]);
                        tap(require(findDescription("Undo last move"))); assertEquals(1, ((Slitherlink) read(mode)).edges[edge]);
                        assertEquals(board, ViewportChecks.bounds(tagged("puzzle-board")));
                        capture("puzzles-slitherlink-larger-" + n + "-" + orientation + "-" + (int) scale + ".png");
                        tap(require(findDescription("Undo last move"))); assertEquals(0, ((Slitherlink) read(mode)).edges[edge]);
                    } finally {
                        getInstrumentation().runOnMainSync(() ->
                                activity.getResources().updateConfiguration(prior, activity.getResources().getDisplayMetrics()));
                    }
                }
            }
            Slitherlink game = (Slitherlink) read(mode);
            tap(require(findDescription("Cell clues")));
            TextView last = loopClue(n * n - 1);
            assertTrue(last.getContentDescription().toString().contains("Row " + n + " column " + n));
            tapWindow("Close");
            tap(require(findDescription("Edge controls")));
            assertTrue(edgeAlternativeReason(game.edges.length - 1).contains("dot row " + n + " column " + (n + 1)));
            assertTrue(activeNode("Cycle selected edge").performAction(AccessibilityNodeInfo.ACTION_CLICK));
            getInstrumentation().waitForIdleSync(); tapWindow("Close");
            assertEquals(1, ((Slitherlink) read(mode)).edges[game.edges.length - 1]);
            byte[] saved = bytes(mode); close(); launch(mode);
            assertTrue("Larger loop resumes exact entries/history", Arrays.equals(saved, bytes(mode)));
            tap(require(findDescription("Undo last move"))); assertTrue(Arrays.equals(initial, PuzzleStore.encode(read(mode))));
            close();
        }
    }

    public void testFullPuzzleViewportAndStableMoves() throws Exception {
        for (Mode mode : Mode.values()) {
            write(mode, fixture(mode, "ready")); launch(mode);
            for (int orientation : new int[]{ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}) {
                rotate(orientation);
                for (float scale : new float[]{1f, 2f}) {
                    Configuration prior = new Configuration(activity.getResources().getConfiguration());
                    try {
                        getInstrumentation().runOnMainSync(() -> {
                            Configuration changed = new Configuration(prior); changed.fontScale = scale;
                            activity.getResources().updateConfiguration(changed, activity.getResources().getDisplayMetrics());
                            try {
                                for (String name : new String[]{"build", "showBoard"}) {
                                    Method method = PuzzleActivity.class.getDeclaredMethod(name); method.setAccessible(true); method.invoke(activity);
                                }
                                Method refresh = PuzzleActivity.class.getDeclaredMethod("refresh", String.class);
                                refresh.setAccessible(true); refresh.invoke(activity, (String) null);
                            } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
                        });
                        getInstrumentation().waitForIdleSync(); SystemClock.sleep(250);
                        ViewportChecks.all(activity);
                        assertCentered();
                        capture("puzzles-fit-" + mode.name().toLowerCase(java.util.Locale.ROOT) + "-"
                                + orientation + "-" + (int) scale + ".png");
                    } finally {
                        getInstrumentation().runOnMainSync(() ->
                                activity.getResources().updateConfiguration(prior, activity.getResources().getDisplayMetrics()));
                    }
                }
            }
            android.graphics.RectF before = ViewportChecks.bounds(tagged("puzzle-board"));
            int target = Integer.parseInt(new String(Files.readAllBytes(fixtureFile(mode + "-target.txt").toPath()),
                    java.nio.charset.StandardCharsets.US_ASCII).trim());
            lastMove(decodeFixture(mode, "complete"), target);
            assertTrue(read(mode).solved());
            assertEquals("Completion doesn't move or resize the board", before, ViewportChecks.bounds(tagged("puzzle-board")));
            ViewportChecks.all(activity); close();
        }
    }

    private AccessibilityNodeInfo activeNode(String text) {
        for (int attempt = 0; attempt < 20; attempt++) {
            AccessibilityNodeInfo root = getInstrumentation().getUiAutomation().getRootInActiveWindow();
            if (root != null) for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text))
                if (node.getText() != null && text.contentEquals(node.getText())
                        || node.getContentDescription() != null && text.contentEquals(node.getContentDescription())) return node;
            SystemClock.sleep(100);
        }
        throw new AssertionError("Native accessibility node missing: " + text);
    }

    private void assertInequalities(View view) {
        if (view instanceof TextView && !(view instanceof Button)) {
            TextView clue = (TextView) view; String text = clue.getText().toString();
            if (text.length() == 1 && "<>^v".contains(text)) {
                android.graphics.Paint.FontMetricsInt metrics = clue.getPaint().getFontMetricsInt();
                assertTrue("Complete inequality height at 2x: " + text, clue.getHeight() >= metrics.bottom - metrics.top);
                assertTrue("Complete inequality width at 2x: " + text, clue.getWidth() >= clue.getPaint().measureText(text));
                assertTrue("Text actually scales", clue.getTextSize() / clue.getResources().getDisplayMetrics().density >= 37);
            }
        }
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            assertInequalities(((ViewGroup) view).getChildAt(i));
    }

    private SharedPreferences settings() { return getInstrumentation().getTargetContext().getSharedPreferences("settings", 0); }
    @SuppressWarnings("unchecked")
    private TextView loopClue(int cell) throws Exception {
        java.lang.reflect.Field field = PuzzleActivity.class.getDeclaredField("loopClues"); field.setAccessible(true);
        return ((java.util.List<TextView>) field.get(activity)).get(cell);
    }

    private String edgeAlternativeReason(int position) throws Exception {
        java.lang.reflect.Field field = PuzzleActivity.class.getDeclaredField("edgeChoice"); field.setAccessible(true);
        android.widget.Spinner choices = (android.widget.Spinner) field.get(activity);
        getInstrumentation().runOnMainSync(() -> choices.setSelection(position));
        getInstrumentation().waitForIdleSync();
        return require(choices.getSelectedView()).getContentDescription().toString();
    }

    private void chooseNewPuzzle(Mode mode, int setting, boolean confirm) {
        if (!(activity instanceof PuzzleMenuActivity)) {
            Instrumentation.ActivityMonitor menu = getInstrumentation().addMonitor(PuzzleMenuActivity.class.getName(), null, false);
            tap(require(findText("New puzzle"))); activity = menu.waitForActivityWithTimeout(5000);
            getInstrumentation().removeMonitor(menu); assertNotNull(activity); menuReady(mode);
        }
        Instrumentation.ActivityMonitor game = getInstrumentation().addMonitor(PuzzleActivity.class.getName(), null, false);
        try {
            tap(require(tagged("new-setting-" + setting)));
            if (windowText("Keep")) tapWindow(confirm ? "Replace" : "Keep");
            else assertTrue("Active progress requires confirmation", confirm);
            if (confirm) { activity = game.waitForActivityWithTimeout(5000); assertNotNull(activity); ready(mode); }
        } finally { getInstrumentation().removeMonitor(game); }
    }
    private File save(Mode mode) { return new File(getInstrumentation().getTargetContext().getFilesDir(), "puzzles/" + mode.name().toLowerCase(java.util.Locale.ROOT) + "-v1.bin"); }
    private File fixtureFile(String name) { return new File(getInstrumentation().getTargetContext().getExternalFilesDir(null), "six-fixtures-20261117/" + name); }
    private byte[] fixture(Mode mode, String state) throws Exception { return Files.readAllBytes(fixtureFile(mode + "-" + state + ".bin").toPath()); }
    private Puzzle decodeFixture(Mode mode, String state) throws Exception { return PuzzleStore.decode(mode, fixture(mode, state)); }
    private byte[] bytes(Mode mode) throws Exception {
        return PuzzleStore.encode(progress(mode).withElapsed(0));
    }
    private Puzzle read(Mode mode) throws Exception { return PuzzleStore.decode(mode, bytes(mode)); }
    private PuzzleProgress progress(Mode mode) throws Exception {
        byte[][] data = new byte[1][];
        java.io.IOException[] failure = new java.io.IOException[1];
        getInstrumentation().runOnMainSync(() -> {
            try { data[0] = Files.readAllBytes(save(mode).toPath()); }
            catch (java.io.IOException ex) { failure[0] = ex; }
        });
        if (failure[0] != null) throw failure[0];
        return PuzzleStore.decodeProgress(mode, data[0]);
    }
    private View binary(int value) { return require(activity.findViewById(android.R.id.content).findViewWithTag("binary-" + value)); }
    private void write(Mode mode, byte[] bytes) throws Exception {
        File parent = save(mode).getParentFile(); assertTrue(parent.isDirectory() || parent.mkdirs());
        for (String suffix : new String[]{".bak", ".new"}) { File extra = new File(save(mode).toString() + suffix); if (extra.exists()) assertTrue(extra.delete()); }
        Files.write(save(mode).toPath(), bytes);
    }
    private void launch(Mode mode) {
        activity = getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(), PuzzleActivity.class)
                .putExtra(PuzzleActivity.MODE, mode.name()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        ready(mode);
    }
    private void ready(Mode mode) {
        for (int i = 0; i < 250; i++) {
            getInstrumentation().waitForIdleSync();
            View next = findText("New puzzle");
            if (next != null && next.isEnabled() && activity.hasWindowFocus()) { SystemClock.sleep(650); return; }
            SystemClock.sleep(50);
        }
        fail("Puzzle did not open within its bounded generation interval: " + mode);
    }
    private void settle() {
        getInstrumentation().waitForIdleSync();
        for (int i = 0; i < 100 && !activity.hasWindowFocus(); i++) SystemClock.sleep(50);
        SystemClock.sleep(650);
    }
    private void close() {
        if (activity != null && !activity.isDestroyed()) { getInstrumentation().runOnMainSync(() -> activity.finish()); getInstrumentation().waitForIdleSync(); }
        activity = null;
    }
    private void rotate(int orientation) {
        Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(PuzzleActivity.class.getName(), null, false);
        getInstrumentation().runOnMainSync(() -> activity.setRequestedOrientation(orientation));
        Activity recreated = getInstrumentation().waitForMonitorWithTimeout(monitor, 5000);
        getInstrumentation().removeMonitor(monitor);
        if (recreated != null) activity = recreated;
        settle();
        assertEquals(orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                ? Configuration.ORIENTATION_LANDSCAPE : Configuration.ORIENTATION_PORTRAIT,
                activity.getResources().getConfiguration().orientation);
    }
    private View require(View view) { assertNotNull(view); return view; }
    private View findText(String text) { return find(activity.getWindow().getDecorView(), v ->
            v instanceof TextView && text.contentEquals(((TextView) v).getText())
                    || v.isClickable() && text.contentEquals(v.getContentDescription() == null ? "" : v.getContentDescription())); }
    private View findDescription(String text) { return find(activity.getWindow().getDecorView(), v -> text.contentEquals(v.getContentDescription() == null ? "" : v.getContentDescription())); }
    private View square(int cell) { return require(activity.findViewById(android.R.id.content).findViewWithTag("cell-" + cell)); }
    private View pathSquare(int cell) { return ((ViewGroup) require(find(activity.getWindow().getDecorView(), v -> v instanceof NumberlinkBoard))).getChildAt(cell); }
    private View find(View view, java.util.function.Predicate<View> match) {
        if (match.test(view)) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View found = find(((ViewGroup) view).getChildAt(i), match); if (found != null) return found;
        }
        return null;
    }
    private void tap(View view) {
        getInstrumentation().runOnMainSync(() -> view.requestRectangleOnScreen(new Rect(0, 0, view.getWidth(), view.getHeight()), true));
        getInstrumentation().waitForIdleSync(); SystemClock.sleep(100);
        android.graphics.RectF bounds = ViewportChecks.bounds(view);
        assertTrue("Fitted control has positive geometry: " + view.getTag(), bounds.width() > 0 && bounds.height() > 0);
        touch(bounds.centerX(), bounds.centerY());
    }
    private void touch(float x, float y) {
        long time = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(time, time + 70, MotionEvent.ACTION_UP, x, y, 0);
        getInstrumentation().sendPointerSync(down); getInstrumentation().sendPointerSync(up);
        down.recycle(); up.recycle(); getInstrumentation().waitForIdleSync(); SystemClock.sleep(100);
    }
    private void tapSlitherEdge(int edge) throws Exception {
        View board = require(find(activity.getWindow().getDecorView(), v -> v instanceof SlitherlinkBoard));
        getInstrumentation().runOnMainSync(() -> board.requestRectangleOnScreen(new Rect(0, 0, board.getWidth(), board.getHeight()), true));
        getInstrumentation().waitForIdleSync();
        android.graphics.RectF p = ViewportChecks.bounds(board);
        Slitherlink game = (Slitherlink) read(Mode.SLITHERLINK); int width = game.size() + 1;
        int a = game.grid.a[edge], b = game.grid.b[edge]; float cell = p.width() / width;
        touch(p.left + ((a % width + b % width) / 2f + .5f) * cell,
                p.top + ((a / width + b / width) / 2f + .5f) * cell);
    }
    private boolean windowText(String text) {
        for (int attempt = 0; attempt < 20; attempt++) {
            AccessibilityNodeInfo root = getInstrumentation().getUiAutomation().getRootInActiveWindow();
            if (root != null) for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text))
                if (node.getText() != null && text.equalsIgnoreCase(node.getText().toString())) return true;
            SystemClock.sleep(100);
        }
        return false;
    }
    private void tapWindow(String text) {
        for (int attempt = 0; attempt < 20; attempt++) {
            AccessibilityNodeInfo root = getInstrumentation().getUiAutomation().getRootInActiveWindow();
            if (root != null) for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text))
                if (node.getText() != null && text.equalsIgnoreCase(node.getText().toString())
                        || node.getContentDescription() != null && text.equalsIgnoreCase(node.getContentDescription().toString())) {
                    Rect bounds = new Rect(); node.getBoundsInScreen(bounds); touch(bounds.centerX(), bounds.centerY()); return;
                }
            SystemClock.sleep(100);
        }
        fail("Dialog action not found: " + text);
    }
    private void assertBoardControlAdjacency() {
        View root = activity.findViewById(android.R.id.content);
        View grid = root.findViewWithTag("puzzle-board"), input = root.findViewWithTag("puzzle-input");
        assertNotNull(grid); assertNotNull(input);
        android.graphics.RectF a = ViewportChecks.bounds(grid), b = ViewportChecks.bounds(input);
        float x = Math.max(0, Math.max(a.left - b.right, b.left - a.right));
        float y = Math.max(0, Math.max(a.top - b.bottom, b.top - a.bottom));
        double gap = Math.hypot(x, y) / grid.getResources().getDisplayMetrics().density;
        assertTrue("Board and first controls form one fitted group: " + gap + "dp", gap <= 24);
    }
    private void capture(String name) throws Exception {
        getInstrumentation().waitForIdleSync(); SystemClock.sleep(250);
        Bitmap screenshot = getInstrumentation().getUiAutomation().takeScreenshot(); assertNotNull(screenshot);
        try (FileOutputStream stream = new FileOutputStream(new File(getInstrumentation().getTargetContext().getExternalFilesDir(null), name))) {
            assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream));
        }
        screenshot.recycle();
    }
}
