package com.quietgrid.sudoku;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Rect;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.MotionEvent;
import android.view.View;

import com.quietgrid.sudoku.puzzles.Generation;
import com.quietgrid.sudoku.puzzles.Mode;
import com.quietgrid.sudoku.puzzles.Nonogram;
import com.quietgrid.sudoku.puzzles.PuzzleProgress;
import com.quietgrid.sudoku.puzzles.PuzzleStore;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Random;

/** Small hosted-emulator gate; no external fixtures, screenshots, or player installation. */
@SuppressWarnings("deprecation")
public final class CiSmokeTests extends InstrumentationTestCase {
    private Activity menu, game;
    private File save;
    private final byte[][] originals = new byte[3][];
    private final String[] suffixes = {"", ".bak", ".new"};

    @Override protected void setUp() throws Exception {
        super.setUp();
        assertEquals("Smoke fixtures must never target the player installation",
                "fyi.mllab.puzzle.validation", getInstrumentation().getTargetContext().getPackageName());
        save = new File(getInstrumentation().getTargetContext().getFilesDir(), "puzzles/nonogram-v1.bin");
        for (int i = 0; i < suffixes.length; i++) {
            File file = new File(save + suffixes[i]);
            originals[i] = file.exists() ? Files.readAllBytes(file.toPath()) : null;
            if (file.exists()) assertTrue(file.delete());
        }
    }

    @Override protected void tearDown() throws Exception {
        try {
            finish(game);
            finish(menu);
            if (save != null) for (int i = 0; i < suffixes.length; i++) {
                File file = new File(save + suffixes[i]);
                if (originals[i] != null) Files.write(file.toPath(), originals[i]);
                else if (file.exists()) assertTrue(file.delete());
            }
        } finally {
            super.tearDown();
        }
    }

    public void testGeneratedGameInputMenuResumeAndUndo() throws Exception {
        Nonogram generated = Nonogram.generate(5, new Random(41000), new Generation());
        byte[] fixture = PuzzleStore.encode(generated);
        assertTrue("Seeded fixture is repeatable", Arrays.equals(fixture,
                PuzzleStore.encode(Nonogram.generate(5, new Random(41000), new Generation()))));
        assertFalse(generated.solved());
        assertTrue(save.getParentFile().isDirectory() || save.getParentFile().mkdirs());
        Files.write(save.toPath(), fixture);

        menu = getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),
                PuzzleMenuActivity.class).putExtra(PuzzleActivity.MODE, Mode.NONOGRAM.name())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        resume();
        View square = awaitTag(game, "cell-0");
        for (int action : new int[]{MotionEvent.ACTION_MOVE, MotionEvent.ACTION_CANCEL}) {
            squareGesture(square, action);
            assertFalse("Drag off and back or cancel must not edit", read().canUndo());
        }
        twoFingerGesture(square, awaitTag(game, "cell-1"));
        assertFalse("Two fingers on separate cells must not edit", read().canUndo());
        squareGesture(square, MotionEvent.ACTION_UP);
        PuzzleProgress edited = read();
        assertEquals("Same-square drift beyond slop cancels hold but preserves the tap", 1, ((Nonogram) edited.game).values[0]);
        assertEquals("Exactly one edit", 1, edited.undoCount());

        View back = findDescription(game.getWindow().getDecorView(), "Back to " + Mode.NONOGRAM.title + " menu");
        assertNotNull("Game offers menu navigation", back);
        click(back);
        awaitTag(menu, "puzzle-resume");
        game = null;
        resume();
        Nonogram reopened = (Nonogram) read().game;
        assertTrue("Clues survive reopening", Arrays.deepEquals(generated.rows, reopened.rows));
        assertTrue(Arrays.deepEquals(generated.columns, reopened.columns));
        assertTrue("Input survives menu and new activity", Arrays.equals(((Nonogram) edited.game).values, reopened.values));
        assertTrue("Undo survives reopening", read().canUndo());
        View undo = findDescription(game.getWindow().getDecorView(), "Undo last move");
        assertNotNull(undo);
        click(undo);
        PuzzleProgress undone = read();
        assertTrue("Undo restores the generated board", Arrays.equals(generated.values, ((Nonogram) undone.game).values));
        assertTrue(undone.canRedo());
        click(findDescription(game.getWindow().getDecorView(), "Back to " + Mode.NONOGRAM.title + " menu"));
        game = null;
        resume();
        View redo = findDescription(game.getWindow().getDecorView(), "Redo last move");
        assertNotNull(redo);
        getInstrumentation().runOnMainSync(() -> assertTrue("Redo is enabled after reopen", redo.isEnabled()));
        click(redo);
        assertEquals(1, ((Nonogram) read().game).values[0]);
        getInstrumentation().runOnMainSync(() -> assertFalse("No later move to restore", redo.isEnabled()));
        click(findDescription(game.getWindow().getDecorView(), "Undo last move"));
        assertTrue(read().canRedo());
        tap(awaitTag(game, "cell-1"));
        assertFalse("A fresh edit discards the old future", read().canRedo());
    }

    private void squareGesture(View square, int variation) {
        long start = SystemClock.uptimeMillis();
        getInstrumentation().runOnMainSync(() -> {
            float x = 1, y = square.getHeight() / 2f;
            float drift = x + android.view.ViewConfiguration.get(square.getContext()).getScaledTouchSlop() + 1;
            assertTrue("Both sampled points are inside the same square", drift < square.getWidth());
            sendLocal(square, start, MotionEvent.ACTION_DOWN, x, y);
            if (variation == MotionEvent.ACTION_UP) sendLocal(square, start, MotionEvent.ACTION_MOVE, drift, y);
            else if (variation == MotionEvent.ACTION_MOVE) {
                sendLocal(square, start, MotionEvent.ACTION_MOVE, -1, y);
                sendLocal(square, start, MotionEvent.ACTION_MOVE, x, y);
            } else sendLocal(square, start, MotionEvent.ACTION_CANCEL, x, y);
        });
        if (variation == MotionEvent.ACTION_UP) SystemClock.sleep(BinaryPress.timeout(square.getContext()) + 100);
        getInstrumentation().runOnMainSync(() -> sendLocal(square, start, MotionEvent.ACTION_UP, 1, square.getHeight() / 2f));
        getInstrumentation().waitForIdleSync();
    }

    private void twoFingerGesture(View first, View second) {
        getInstrumentation().runOnMainSync(() -> {
            long start = SystemClock.uptimeMillis();
            MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[2];
            MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[2];
            View[] cells = {first, second};
            for (int i = 0; i < cells.length; i++) {
                Rect bounds = new Rect();
                assertTrue(cells[i].getGlobalVisibleRect(bounds));
                properties[i] = new MotionEvent.PointerProperties(); properties[i].id = i;
                properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
                coords[i] = new MotionEvent.PointerCoords(); coords[i].x = bounds.centerX(); coords[i].y = bounds.centerY();
            }
            int index = 1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT;
            for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN | index,
                    MotionEvent.ACTION_POINTER_UP | index, MotionEvent.ACTION_UP}) {
                int count = action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP ? 1 : 2;
                MotionEvent event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, count,
                        properties, coords, 0, 0, 1, 1, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0);
                try { assertTrue(game.dispatchTouchEvent(event)); } finally { event.recycle(); }
            }
        });
        getInstrumentation().waitForIdleSync();
    }

    private static void sendLocal(View view, long start, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, x, y, 0);
        try { view.dispatchTouchEvent(event); } finally { event.recycle(); }
    }

    private void resume() {
        View resume = awaitTag(menu, "puzzle-resume");
        Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(PuzzleActivity.class.getName(), null, false);
        try {
            click(resume);
            game = monitor.waitForActivityWithTimeout(10000);
            assertNotNull("Resume opens the game", game);
            awaitTag(game, "cell-0");
        } finally {
            getInstrumentation().removeMonitor(monitor);
        }
    }

    private PuzzleProgress read() throws Exception {
        NativeSaveAwaiter.await(getInstrumentation(), game);
        getInstrumentation().waitForIdleSync();
        PuzzleProgress progress = PuzzleActivity.readProgress(getInstrumentation().getTargetContext(), Mode.NONOGRAM);
        assertNotNull("Progress is durable", progress);
        return progress;
    }

    private View awaitTag(Activity activity, String tag) {
        long deadline = SystemClock.uptimeMillis() + 10000;
        View[] found = new View[1];
        do {
            getInstrumentation().waitForIdleSync();
            getInstrumentation().runOnMainSync(() -> {
                View view = activity.getWindow().getDecorView().findViewWithTag(tag);
                found[0] = view != null && view.isShown() && view.isEnabled()
                        && view.getWidth() > 0 && view.getHeight() > 0 ? view : null;
            });
            if (found[0] != null) return found[0];
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Control did not become available: " + tag);
    }

    private void click(View view) {
        getInstrumentation().runOnMainSync(() -> assertTrue("Native action is handled", view.performClick()));
        getInstrumentation().waitForIdleSync();
    }

    private void tap(View view) {
        Rect bounds = new Rect();
        getInstrumentation().runOnMainSync(() -> assertTrue("Input square is visible", view.getGlobalVisibleRect(bounds)));
        long downTime = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, bounds.centerX(), bounds.centerY(), 0);
        MotionEvent up = MotionEvent.obtain(downTime, downTime + 60, MotionEvent.ACTION_UP, bounds.centerX(), bounds.centerY(), 0);
        try {
            getInstrumentation().sendPointerSync(down);
            getInstrumentation().sendPointerSync(up);
        } finally {
            down.recycle();
            up.recycle();
        }
        getInstrumentation().waitForIdleSync();
    }

    private View findDescription(View view, String description) {
        if (description.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findDescription(group.getChildAt(i), description);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void finish(Activity activity) {
        if (activity != null && !activity.isDestroyed()) {
            if (activity instanceof PuzzleActivity) NativeSaveAwaiter.await(getInstrumentation(), activity);
            getInstrumentation().runOnMainSync(activity::finish);
            getInstrumentation().waitForIdleSync();
            if (save != null) {
                try { SaveQueue.forPath(save.getAbsolutePath()).flushBlocking(); }
                catch (java.io.IOException ex) { throw new AssertionError(ex); }
            }
        }
    }
}
