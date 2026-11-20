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
        tap(awaitTag(game, "cell-0"));
        PuzzleProgress edited = read();
        assertEquals("Native tap fills the square", 1, ((Nonogram) edited.game).values[0]);
        assertTrue("Input records Undo", edited.canUndo());

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
        assertTrue("Undo restores the generated board", Arrays.equals(generated.values, ((Nonogram) read().game).values));
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
