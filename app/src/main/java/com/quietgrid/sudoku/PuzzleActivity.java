package com.quietgrid.sudoku;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.quietgrid.sudoku.puzzles.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

/** Native presentation and atomic per-mode progress; rules and generation live in separate engines. */
public final class PuzzleActivity extends Activity {
    public static final String MODE = "puzzle-mode";
    public static final String FROM_MENU = "from-menu", NEW_SETTING = "new-setting";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Button> cells = new ArrayList<>();
    private final List<TextView> loopClues = new ArrayList<>();
    private final List<View> futoKeys = new ArrayList<>();
    private final SparseIntArray pendingPictureCells = new SparseIntArray();
    private final List<View> sideControls = new ArrayList<>();
    private final MinesFeedback minesFeedback = new MinesFeedback();
    private TextView[] pictureClues = new TextView[0];
    private Future<?> pending;
    private Mode mode;
    private boolean shared;
    private String sharedLink, sharedKey, sharedError;
    private ImageButton share;
    private Puzzle game;
    private PuzzleProgress progress;
    private MainActivity.Palette palette;
    private LinearLayout content, boardArea;
    private TextView status;
    private Button newPuzzle, clearPuzzle, retry, mineTool;
    private ImageButton undo, redo;
    private Button edgeUndo, edgeRedo, edgeClear;
    private boolean canceledMultiTouch;
    private ImageButton gameSettingsButton;
    private final View[] binaryKeys = new View[3];
    private LinearLayout binaryBank;
    private View binaryGrid;
    private View futoGrid, futoBank;
    private int binaryArmed = -1;
    private NumberlinkBoard paths;
    private SlitherlinkBoard loop;
    private Spinner edgeChoice;
    private ArrayAdapter<String> edgeAdapter;
    private Button loopTool, loopLine, loopCross;
    private boolean busy, loadFailed, generationFailed, flag;
    private int selected = -1;
    private int futoArmed;
    final ActivePlayTimer playTime = new ActivePlayTimer(android.os.SystemClock::elapsedRealtime);
    private TextView clock;
    private CompletionFeedback completion;
    private PuzzleFeedback feedback;
    private boolean resumed, focused;
    private PlayViewport playViewport;
    private LinearLayout sideActions;
    private View mineGrid;
    private TextView puzzleTitle;
    private boolean newRequested;
    private int newSetting;
    private SaveQueue saves;
    private SaveSession<PuzzleActivity> saveSession;
    private PuzzleSave puzzleSave;
    private boolean showingLimit;
    private byte[] replacementSave;
    private static final class Retained {
        final SaveSession<PuzzleActivity> session;
        Retained(SaveSession<PuzzleActivity> session) { this.session = session; }
    }
    private volatile long durableElapsed;
    private volatile long savedRevision;
    java.util.function.Function<File, DurableFile> saveFileFactory = AndroidSaveFile::at;

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(MainActivity.appearanceContext(base));
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String id = getIntent().getStringExtra(MODE);
        try { mode = Mode.valueOf(id == null ? "" : id); }
        catch (IllegalArgumentException ex) { Log.e("Puzzle", "Unknown puzzle mode", ex); finish(); return; }
        shared = PuzzleSharing.shared(getIntent());
        if (shared) {
            try {
                sharedLink = PuzzleSharing.incoming(getIntent());
                if (PuzzleShare.mode(sharedLink) != mode) throw new IllegalArgumentException("The link belongs to another puzzle mode.");
                sharedKey = LocalShareLink.saveKey(sharedLink, PuzzleShare.kind(mode));
            } catch (IllegalArgumentException ex) { Log.e("Puzzle", "Invalid shared link", ex); sharedError = ex.getMessage(); }
        }
        if (state != null) {
            flag = state.getBoolean("flag"); selected = state.getInt("selected", -1);
            binaryArmed = state.getInt("binaryArmed", -1);
            futoArmed = state.getInt("futoArmed");
        }
        if (mode == Mode.TAKUZU) selected = -1;
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        if (android.os.Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int accent = getSharedPreferences("settings", 0).getInt("accentColor", 3);
        palette = new MainActivity.Palette(dark, accent >= 0 && accent < MainActivity.COLOR_NAMES.length ? accent : 3);
        newRequested = state == null ? getIntent().hasExtra(NEW_SETTING) : state.getBoolean("newRequested");
        newSetting = state == null ? getIntent().getIntExtra(NEW_SETTING, mode.defaultSetting())
                : state.getInt(NEW_SETTING, mode.defaultSetting());
        puzzleSave = quota(this, mode);
        if (shared) { newRequested = false; newSetting = mode.defaultSetting(); }
        saves = puzzleSave.queue();
        saveSession = getLastNonConfigurationInstance() instanceof Retained retained ? retained.session
                : new SaveSession<>(saves, new Handler(Looper.getMainLooper())::post, ex -> Log.e("Puzzle", "Game commit failed", ex));
        saveSession.attach(this);
        if (state != null) replacementSave = state.getByteArray("replacementSave");
        if (!(state != null ? mode.canGenerate(newSetting) : mode.supports(newSetting))) {
            Log.e("Puzzle", "Unsupported new puzzle setting"); finish(); return;
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::showMenu);
        build();
        open();
    }

    private static File saved(Context context, Mode mode) {
        return new File(context.getFilesDir(), "puzzles/" + mode.name().toLowerCase(Locale.ROOT) + "-v1.bin");
    }

    static PuzzleSave quota(Context context, Mode mode) { return PuzzleSave.forFile(saved(context, mode)); }
    private boolean limited() { return ResponsibleMode.limited(this, puzzleSave); }
    private File saved() {
        return shared ? new File(getFilesDir(), sharedPrefix(mode) + sharedKey + ".bin") : saved(this, mode);
    }

    private static String sharedPrefix(Mode mode) { return "shared-" + PuzzleShare.kind(mode) + "-"; }

    static PuzzleProgress readProgress(Context context, Mode mode) throws IOException {
        File file = saved(context, mode);
        byte[] bytes = BuildConfig.LOCAL_PUZZLE_SHARING && mode != Mode.MINESWEEPER
                ? quota(context, mode).read(AndroidSaveFile.at(file), PuzzleStore.MAX_SAVE_BYTES,
                        context.getFilesDir(), sharedPrefix(mode), null)
                : AndroidSaveFile.read(file, PuzzleStore.MAX_SAVE_BYTES);
        return bytes == null ? null : PuzzleStore.decodeProgress(mode, bytes);
    }

    private void open() {
        busy = true; refresh("Opening saved puzzle...");
        pending = worker.submit(() -> {
            try {
                PuzzleProgress result;
                boolean freshShared = false;
                if (shared) {
                    if (sharedError != null) throw new IllegalArgumentException(sharedError);
                    Puzzle imported = PuzzleShare.open(sharedLink);
                    File file = saved();
                    byte[] bytes = puzzleSave.read(AndroidSaveFile.at(file), PuzzleStore.MAX_SAVE_BYTES,
                            getFilesDir(), sharedPrefix(mode), file);
                    result = bytes == null ? new PuzzleProgress(imported) : PuzzleStore.decodeProgress(mode, bytes);
                    if (!PuzzleShare.link(result.game).equals(sharedLink)) throw new IOException("Shared progress does not match this puzzle.");
                    freshShared = bytes == null;
                } else result = readProgress(this, mode);
                boolean saveFreshShared = freshShared;
                boolean replacementCommitted = result != null && replacementSave != null
                        && java.util.Arrays.equals(replacementSave, PuzzleStore.encode(result));
                handler.post(() -> {
                    if (isDestroyed()) return;
                    busy = false; loadFailed = false; progress = result; game = result == null ? null : result.game;
                    playTime.restore(result == null ? 0 : result.elapsedMillis);
                    durableElapsed = result == null ? 0 : result.elapsedMillis;
                    if (game != null && selected >= game.size() * game.size()) selected = -1;
                    if (replacementCommitted) newRequested = false;
                    saveSession.ready();
                    if (saveFreshShared && !limited()) {
                        saveSession.dispatch(owner -> owner.persist(owner.progress, PuzzleSave.Edit.REPLACE,
                                value -> { value.showBoard(); value.refresh(null); }));
                    } else if (newRequested) generate();
                    else if (game == null) generate(); else { showBoard(); refresh(null); }
                });
            } catch (IOException | IllegalArgumentException | IllegalStateException ex) {
                Log.e("Puzzle", "Could not read " + mode, ex);
                handler.post(() -> {
                    if (isDestroyed()) return;
                    busy = false; loadFailed = true;
                    saveSession.ready();
                    if (newRequested) generate();
                    else refresh(shared ? "Shared puzzle could not be opened. " + ex.getMessage()
                            : "Saved progress could not be read. It has not been replaced. Retry, or choose New puzzle to replace it.");
                });
            }
        });
    }

    private void requestNew() {
        if (shared) { showMenu(); return; }
        if (busy || ResponsibleMode.notifyIfLimited(this, puzzleSave)) return;
        int setting = game == null ? newSetting : mode.settingOf(game);
        confirmNew(setting);
    }

    private void confirmNew(int setting) {
        Runnable start = () -> saveSession.dispatch(owner -> {
            if (owner.busy) return;
            owner.newSetting = setting;
            owner.generate();
        });
        if (loadFailed || game != null && !game.solved() && !game.lost()) {
            new AlertDialog.Builder(this).setTitle("Replace this puzzle?")
                    .setMessage(loadFailed ? "The unreadable save will be replaced only after a new puzzle is generated and saved."
                            : "Your current progress and history will be replaced by a new " + mode.title
                                    + " puzzle (" + mode.settingLabel(setting) + "). Other puzzles are not affected.")
                    .setNegativeButton("Keep", null).setPositiveButton("Replace", (d, w) -> start.run()).show();
        } else start.run();
    }

    private boolean canClearPuzzle() {
        return !busy && !limited() && progress != null && progress.canClear();
    }

    private void requestClear() {
        if (!canClearPuzzle()) return;
        new AlertDialog.Builder(this).setTitle(R.string.clear_puzzle)
                .setMessage(mode == Mode.MINESWEEPER ? R.string.clear_mines_confirmation : R.string.clear_puzzle_confirmation)
                .setNegativeButton("Keep", null)
                .setPositiveButton(R.string.clear_action, (d, w) -> saveSession.dispatch(PuzzleActivity::clearPuzzle))
                .show();
    }

    private void clearPuzzle() {
        if (!canClearPuzzle()) return;
        feedback.stop(); minesFeedback.stop();
        playTime.setRunning(false);
        PuzzleProgress cleared = progress.withElapsed(playTime.elapsedMillis()).cleared();
        persist(cleared, PuzzleSave.Edit.PLAY, owner -> {
            owner.progress = cleared; owner.game = cleared.game;
            owner.selected = -1; owner.binaryArmed = -1; owner.futoArmed = 0; owner.flag = false;
            owner.refresh(null);
        });
    }

    private void generate() {
        if (shared) throw new IllegalStateException("A shared puzzle cannot be replaced by generation.");
        if (saveSession.defer(PuzzleActivity::generate)) return;
        if (limited()) { showLimit(); return; }
        newRequested = true;
        playTime.setRunning(false); checkpointTime();
        if (feedback != null) feedback.stop();
        if (completion != null) completion.stop();
        busy = true; generationFailed = false; refresh("Creating a fresh puzzle...");
        pending = worker.submit(() -> {
            try {
                Puzzle created = mode.generate(newSetting, new Random(), new Generation());
                byte[] createdSave = PuzzleStore.encode(created);
                handler.post(() -> {
                    if (isDestroyed()) return;
                    busy = false;
                    PuzzleProgress fresh = new PuzzleProgress(created);
                    replacementSave = createdSave;
                    persist(fresh, PuzzleSave.Edit.REPLACE, owner -> {
                        owner.newRequested = false; owner.replacementSave = null;
                        owner.progress = fresh; owner.game = created; owner.loadFailed = false;
                        owner.playTime.restore(0);
                        owner.selected = -1; owner.binaryArmed = -1; owner.futoArmed = 0;
                        owner.showBoard(); owner.refresh(null);
                    });
                });
            } catch (CancellationException ex) {
                // Lifecycle destruction cancels only this generation, leaving its previous save intact.
            } catch (IllegalStateException | IOException ex) {
                Log.e("Puzzle", "Could not generate " + mode, ex);
                handler.post(() -> {
                    if (isDestroyed()) return;
                    busy = false; generationFailed = true; refresh(ex.getMessage() + " Previous progress is unchanged.");
                });
            }
        });
    }

    private void showLimit() {
        newRequested = false; replacementSave = null;
        if (game != null) showBoard();
        refresh(null);
    }

    private void persist(PuzzleProgress next, PuzzleSave.Edit edit, java.util.function.Consumer<PuzzleActivity> success) {
        java.time.LocalDate today = ResponsibleMode.today();
        saveSession.commit(() -> write(next, edit, today), success, (owner, ex) -> {
            owner.clearPicturePending();
            if (ex instanceof PuzzleSave.LimitReached) { owner.showLimit(); return; }
            if (owner.newRequested) owner.generationFailed = true;
            owner.saveError(ex);
        });
    }
    private void write(PuzzleProgress next, PuzzleSave.Edit edit, java.time.LocalDate today) throws IOException {
        File file = saved();
        byte[] payload = PuzzleStore.encode(next);
        boolean enabled = ResponsibleMode.enabled(this);
        if (shared) puzzleSave.commitShared(file, saveFileFactory.apply(file), payload, edit, enabled, today);
        else puzzleSave.commit(saveFileFactory.apply(file), payload, edit, enabled, today);
        durableElapsed = next.elapsedMillis;
        savedRevision++;
    }
    private void saveError(IOException ex) {
        Log.e("Puzzle", "Could not save " + mode, ex);
        refresh("Could not save. Pending changes were not applied. Check device storage and try again. Your previous progress is unchanged.");
    }
    private void change(Function<Puzzle, String> action) {
        if (saveSession.defer(owner -> owner.change(action))) return;
        if (busy || game == null || game.solved() || game.lost() || limited()) return;
        minesFeedback.stop();
        feedback.stop();
        playTime.setRunning(false);
        Puzzle next = game.copy();
        String error = action.apply(next);
        if (error != null) { refresh(error); return; }
        PuzzleProgress current = progress.withElapsed(playTime.elapsedMillis());
        PuzzleProgress edited = current.edited(next);
        if (edited == current) { refresh(null); return; }
        Puzzle before = game;
        PuzzleActivity origin = this;
        persist(edited, next.solved() || next.lost() ? PuzzleSave.Edit.FINISH : PuzzleSave.Edit.PLAY, owner -> {
            if (next.solved() || next.lost()) owner.playTime.restore(edited.elapsedMillis);
            owner.progress = edited; owner.game = next; owner.refresh(null);
            if (owner == origin && owner.resumed && owner.focused && next instanceof Minesweeper)
                owner.minesFeedback.changed((Minesweeper) before, (Minesweeper) next, owner.cells, owner.palette.tint);
            else if (owner == origin && owner.resumed && owner.focused && !next.solved() && before instanceof UndoablePuzzle && next instanceof UndoablePuzzle)
                owner.feedback.changed((UndoablePuzzle) before, (UndoablePuzzle) next, owner.cells, owner.paths, owner.loop);
            if (owner == origin && next.solved() && owner.resumed && owner.focused) owner.completion.celebrate();
        });
    }

    private void restoreMove(boolean forward) {
        if (saveSession.defer(owner -> owner.restoreMove(forward))) return;
        if (busy || progress == null || limited() || !(forward ? progress.canRedo() : progress.canUndo())) return;
        minesFeedback.stop();
        feedback.stop();
        playTime.setRunning(false); completion.stop();
        PuzzleProgress current = progress.withElapsed(playTime.elapsedMillis());
        PuzzleProgress restored = forward ? current.redo() : current.undo();
        PuzzleSave.Edit edit = forward && (restored.game.solved() || restored.game.lost()) ? PuzzleSave.Edit.FINISH
                : !forward && (game.solved() || game.lost()) ? PuzzleSave.Edit.UNDO_FINISH : PuzzleSave.Edit.PLAY;
        persist(restored, edit, owner -> {
            if (restored.game.solved() || restored.game.lost()) owner.playTime.restore(restored.elapsedMillis);
            owner.progress = restored; owner.game = restored.game;
            owner.selected = -1; owner.binaryArmed = -1; owner.futoArmed = 0;
            owner.refresh(null); if (owner.resumed) owner.undo.announceForAccessibility(forward ? "Move redone." : "Move undone.");
        });
    }

    private void chooseBinary(int value) {
        if (saveSession.defer(owner -> owner.chooseBinary(value))) return;
        if (busy || !(game instanceof Takuzu) || game.solved() || limited()) return;
        selected = -1; binaryArmed = binaryArmed == value ? -1 : value; refresh(null);
    }

    private void cell(int cell) {
        if (game instanceof Nonogram) { pictureCell(cell, false); return; }
        if (saveSession.defer(owner -> owner.cell(cell))) return;
        if (busy || game == null || game.solved() || game.lost() || limited()) return;
        if (game instanceof Takuzu) {
            if (((Takuzu) game).clues[cell] >= 0) return;
            selected = -1;
            if (binaryArmed >= 0) {
                int value = binaryArmed == 2 ? -1 : binaryArmed;
                change(next -> { ((Takuzu) next).enter(cell, value); return null; });
            } else binaryCell(cell, 0);
            return;
        }

        if (game instanceof Futoshiki futo) {
            if (futo.clues[cell] != 0) return;
            if (futoArmed == 0) { selected = cell; refresh(null); }
            else change(next -> { ((Futoshiki) next).enter(cell, futoArmed); return null; });
        }
        else if (game instanceof Numberlink) {
            if (selected < 0 || selected == cell) { selected = selected == cell ? -1 : cell; refresh(null); }
            else {
                int previous = selected; selected = -1;
                change(next -> ((Numberlink) next).toggle(previous, cell));
            }
        } else change(next -> {
            if (next instanceof Minesweeper) {
                Minesweeper mine = (Minesweeper) next;
                return mine.tap(cell, flag && mine.cells[cell] != 1);
            }
            return null;
        });
    }

    private void binaryCell(int cell, int digit) {
        if (saveSession.defer(owner -> owner.binaryCell(cell, digit))) return;
        if (busy || !(game instanceof Takuzu) || game.solved() || ((Takuzu) game).clues[cell] >= 0) return;
        selected = -1;
        change(next -> {
            Takuzu binary = (Takuzu) next;
            binary.enter(cell, binary.values[cell] == digit ? -1 : digit);
            return null;
        });
    }

    private void secondaryCell(int cell) {
        if (!resumed || !focused || busy || game == null || game.solved() || game.lost()) return;
        if (game instanceof Takuzu) binaryCell(cell, 1);
        else if (game instanceof Nonogram) pictureCell(cell, true);
        else if (game instanceof Minesweeper) change(next -> ((Minesweeper) next).tap(cell, true));
    }

    private void pictureCell(int cell, boolean cross) {
        if (busy || game.solved() || limited()) return;
        pendingPictureCells.put(cell, pendingPictureCells.get(cell) + 1);
        showPicturePending(cell);
        saveSession.dispatch(owner -> owner.change(next -> {
            Nonogram picture = (Nonogram) next;
            if (cross) picture.mark(cell, 2); else picture.cycle(cell);
            return null;
        }));
        // The next queued action runs after this edit is durable (or determined to be a no-op).
        saveSession.dispatch(owner -> {
            if (owner != this) return;
            int remaining = pendingPictureCells.get(cell) - 1;
            if (remaining > 0) pendingPictureCells.put(cell, remaining); else pendingPictureCells.delete(cell);
            showPicturePending(cell);
        });
    }

    private void showPicturePending(int cell) {
        Button square = cells.get(cell);
        if (pendingPictureCells.get(cell) == 0) { square.setForeground(null); return; }
        GradientDrawable outline;
        if (square.getForeground() instanceof GradientDrawable existing) outline = existing;
        else {
            outline = new GradientDrawable();
            outline.setColor(android.graphics.Color.TRANSPARENT);
            outline.setCornerRadius(dp(5));
            square.setForeground(outline);
        }
        outline.setStroke(dp(2), square.getCurrentTextColor(), dp(3), dp(2));
    }

    private void clearPicturePending() {
        for (int i = 0; i < pendingPictureCells.size(); i++) cells.get(pendingPictureCells.keyAt(i)).setForeground(null);
        pendingPictureCells.clear();
    }

    private void markEdge(int edge, int mark) {
        if (!resumed) return;
        change(next -> { ((Slitherlink) next).mark(edge, mark); return null; });
    }
    private void enterFutoshiki(int digit) {
        if (busy || game == null || game.solved() || limited()) return;
        if (selected < 0) {
            if (digit != 0) { futoArmed = futoArmed == digit ? 0 : digit; refresh(null); }
            return;
        }
        int target = selected;
        selected = -1;
        change(next -> { ((Futoshiki) next).enter(target, digit); return null; });
    }

    private void build() {
        minesFeedback.stop();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(palette.background);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int left, top, right, bottom;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout());
                left = bars.left; top = bars.top; right = bars.right; bottom = bars.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft(); top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight(); bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(left, top, right, bottom); return insets;
        });
        content = column(); content.setPadding(dp(8), dp(4), dp(8), dp(4));
        root.addView(content, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout heading = row();
        heading.setGravity(Gravity.TOP);
        ImageButton back = iconButton(getString(R.string.back_to_puzzles), R.drawable.ic_back);
        back.setContentDescription(shared ? getString(R.string.close_shared_sudoku) : "Back to " + mode.title + " menu");
        back.setOnClickListener(v -> showMenu()); heading.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = text(shared ? getString(R.string.shared_puzzle_title, mode.title) : mode.title, 26);
        puzzleTitle = title; title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setSingleLine(true); title.setHorizontallyScrolling(false); title.setTag("puzzle-title");
        title.setAutoSizeTextTypeUniformWithConfiguration(12, 26, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        FrameLayout titleSlot = new FrameLayout(this);
        title.setGravity(Gravity.CENTER);
        titleSlot.addView(title, new FrameLayout.LayoutParams(-1, -1));
        heading.addView(titleSlot, new LinearLayout.LayoutParams(0, ScreenChrome.titleHeight(title), 1));
        ImageButton help = iconButton("How to play " + mode.title, R.drawable.ic_help);
        help.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("How to play " + mode.title)
                .setMessage((shared ? getString(R.string.shared_puzzle_help) + "\n\n" : "") + mode.help)
                .setPositiveButton("Close", null).show());
        heading.addView(help, new LinearLayout.LayoutParams(dp(48), dp(48)));
        ImageButton settings = iconButton("Settings", R.drawable.ic_settings);
        gameSettingsButton = settings;
        settings.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)
                .putExtra(MainActivity.OPEN_SETTINGS, true).putExtra(MainActivity.SETTINGS_GAME, mode.name())));
        heading.addView(settings, new LinearLayout.LayoutParams(dp(48), dp(48)));
        ScreenChrome.centerTitle(heading, titleSlot);
        clock = text("0:00", 18); clock.setTag("elapsed-time"); clock.setGravity(Gravity.CENTER);
        clock.setSingleLine(true);
        clock.setAutoSizeTextTypeUniformWithConfiguration(10, 18, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        clock.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_NONE);
        LinearLayout clockRow = row();
        clockRow.addView(clock, new LinearLayout.LayoutParams(0, dp(30 * Math.max(1, getResources().getConfiguration().fontScale)), 1));
        clearPuzzle = button(getString(R.string.clear_action));
        clearPuzzle.setMinimumWidth(dp(64));
        clearPuzzle.setContentDescription(getString(R.string.clear_puzzle));
        clearPuzzle.setOnClickListener(v -> requestClear());
        clockRow.addView(clearPuzzle, new LinearLayout.LayoutParams(-2, dp(48)));
        if (BuildConfig.LOCAL_PUZZLE_SHARING && mode != Mode.MINESWEEPER) {
            share = iconButton(getString(R.string.share_puzzle_local), R.drawable.ic_share);
            share.setOnClickListener(v -> saveSession.dispatch(owner -> {
                PuzzleProgress saved = owner.progress;
                if (saved != null) PuzzleSharing.send(owner, owner.mode.title, PuzzleShare.link(saved.game),
                        saved.game.solved() ? saved.elapsedMillis : 0);
            }));
            clockRow.addView(share, new LinearLayout.LayoutParams(dp(48), dp(48)));
        }
        content.addView(clockRow);
        status = text("", 16); status.setTag("puzzle-feedback"); status.setPadding(dp(4), 0, dp(4), 0);
        status.setGravity(Gravity.CENTER); status.setMaxLines(2);
        status.setAutoSizeTextTypeUniformWithConfiguration(10, 24, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        titleSlot.addView(status, new FrameLayout.LayoutParams(-1, -1));
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        boardArea = column(); boardArea.setGravity(Gravity.CENTER_HORIZONTAL); content.addView(boardArea);
        LinearLayout controls = row(); newPuzzle = button("");
        GradientDrawable gutter = new GradientDrawable(); gutter.setColor(android.graphics.Color.TRANSPARENT);
        gutter.setSize(dp(8), 1); controls.setDividerDrawable(gutter);
        controls.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        ControlStyle.icon(newPuzzle, shared ? getString(R.string.close_shared_sudoku) : "New puzzle",
                shared ? R.drawable.ic_close : R.drawable.ic_add, palette.accent);
        newPuzzle.setOnClickListener(v -> requestNew());
        newPuzzle.setTag("puzzle-input");
        LinearLayout.LayoutParams newLayout = new LinearLayout.LayoutParams(0, dp(56), 1);
        controls.addView(newPuzzle, newLayout);
        retry = button("Retry"); retry.setOnClickListener(v -> {
            if (!busy) {
                if (loadFailed && !generationFailed) open();
                else if (newRequested) {
                    if (!ResponsibleMode.notifyIfLimited(this, puzzleSave)) confirmNew(newSetting);
                }
                else requestNew();
            }
        });
        LinearLayout.LayoutParams retryLayout = new LinearLayout.LayoutParams(0, dp(56), 1);
        controls.addView(retry, retryLayout);
        LinearLayout.LayoutParams actionsLayout = new LinearLayout.LayoutParams(-1, -2); actionsLayout.topMargin = dp(12);
        content.addView(controls, actionsLayout);
        controls.setTag("puzzle-actions");
        undo = iconButton("Undo last move", R.drawable.ic_undo);
        redo = iconButton("Redo last move", R.drawable.ic_redo);
        for (ImageButton action : new ImageButton[]{undo, redo}) {
            action.setPadding(dp(16), dp(16), dp(16), dp(16));
            action.setMinimumHeight(dp(56)); action.setMinimumWidth(dp(56));
            action.setBackground(new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf((palette.ink & 0xFFFFFF) | 0x30000000),
                    surface(palette.surface, palette.line), null));
        }
        undo.setOnClickListener(v -> restoreMove(false));
        redo.setOnClickListener(v -> restoreMove(true));
        if (mode == Mode.TAKUZU) {
            binaryBank = row(); binaryBank.setMinimumHeight(dp(56));
            binaryBank.setTag("puzzle-input");
            for (int value = 0; value < 3; value++) {
                int digit = value;
                View key = value == 2 ? iconButton("Erase", R.drawable.ic_erase) : button("");
                key.setTag("binary-" + value);
                if (key instanceof Button) ((Button) key).setTextSize(22);
                key.setOnClickListener(v -> chooseBinary(digit)); binaryKeys[value] = key;
                LinearLayout.LayoutParams keyLayout = new LinearLayout.LayoutParams(0, dp(56), 1); keyLayout.setMarginEnd(dp(8));
                binaryBank.addView(key, keyLayout);
            }
            LinearLayout.LayoutParams bankLayout = new LinearLayout.LayoutParams(-1, -2); bankLayout.topMargin = dp(12);
            content.addView(binaryBank, content.indexOfChild(controls), bankLayout);
        }
        if (mode != Mode.MINESWEEPER) {
            controls.addView(undo, new LinearLayout.LayoutParams(dp(56), dp(56)));
            controls.addView(redo, new LinearLayout.LayoutParams(dp(56), dp(56)));
        }
        boolean wide = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        sideActions = null;
        if (wide) {
            LinearLayout body = row(), actions = column();
            body.setGravity(Gravity.TOP);
            int boardIndex = content.indexOfChild(boardArea);
            content.removeView(boardArea);
            while (content.getChildCount() > boardIndex) {
                View child = content.getChildAt(boardIndex); content.removeViewAt(boardIndex); actions.addView(child);
            }
            content.removeView(clockRow);
            actions.addView(clockRow, 0);
            LinearLayout.LayoutParams boardLayout = new LinearLayout.LayoutParams(-2, -2);
            boardLayout.setMarginEnd(dp(16));
            body.addView(boardArea, boardLayout);
            body.addView(actions, new LinearLayout.LayoutParams(0, -2, 1));
            content.addView(body);
            sideActions = actions;
        }
        FrameLayout stage = new FrameLayout(this);
        playViewport = new PlayViewport(this, root, dp(wide ? 720 : 360), palette.background);
        stage.addView(ScreenChrome.frame(this, heading, playViewport, palette.background), new FrameLayout.LayoutParams(-1, -1));
        feedback = new PuzzleFeedback(stage, palette);
        completion = new CompletionFeedback(stage, palette);
        setContentView(stage); stage.requestApplyInsets();
    }

    private void showBoard() {
        for (View control : sideControls) if (control.getParent() instanceof android.view.ViewGroup)
            ((android.view.ViewGroup) control.getParent()).removeView(control);
        sideControls.clear();
        cells.clear(); loopClues.clear(); futoKeys.clear();
        pictureClues = new TextView[0];
        binaryGrid = null; mineGrid = null; futoGrid = null; futoBank = null;
        paths = null; loop = null; mineTool = null; edgeChoice = null; loopTool = null; boardArea.removeAllViews();
        if (game instanceof Numberlink) {
            paths = new NumberlinkBoard(this, palette, this::button, this::cell, drawn -> {
                Numberlink before = ((Numberlink) game).copy();
                selected = -1;
                change(next -> ((Numberlink) next).applyChanges(before, drawn));
            }, cellSize(game.size()), game.size());
            horizontal(paths); return;
        }
        if (game instanceof Slitherlink) {
            loop = new SlitherlinkBoard(this, palette, edge -> markEdge(edge, 1), edge -> markEdge(edge, 2),
                    cellSize(game.size() + 1), game.size());
            horizontal(loop);
            Button clues = button("Clues");
            clues.setTag("puzzle-input");
            LinearLayout clueList = column();
            clues.setOnClickListener(v -> {
                LargeControlsDialog dialog = new LargeControlsDialog(this, palette, "Cell clues");
                if (clueList.getParent() != null) ((android.view.ViewGroup) clueList.getParent()).removeView(clueList);
                dialog.content.addView(clueList); dialog.window.show();
            });
            clues.setContentDescription("Cell clues");
            addBoardControls(clues);
            for (int cell = 0; cell < game.size() * game.size(); cell++) {
                TextView clue = text("", 20); clue.setFocusable(true); clue.setMinHeight(dp(48));
                clue.setPadding(dp(4), dp(8), dp(4), dp(8));
                clue.setTag("loop-clue-" + cell); loopClues.add(clue);
                clueList.addView(clue, new LinearLayout.LayoutParams(-1, -2));
            }
            Button accessible = button("");
            accessible.setText("Edge controls");
            accessible.setTag("edge-controls");
            accessible.setContentDescription("Edge controls");
            accessible.setSingleLine(true);
            accessible.setAutoSizeTextTypeUniformWithConfiguration(10, 16, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
            accessible.setTooltipText("Edge controls: choose an edge and mark it");
            LinearLayout alternatives = column();
            accessible.setOnClickListener(v -> {
                LargeControlsDialog dialog = new LargeControlsDialog(this, palette, "Edge controls");
                if (alternatives.getParent() != null) ((android.view.ViewGroup) alternatives.getParent()).removeView(alternatives);
                dialog.content.addView(alternatives); dialog.window.show();
            });
            addBoardControls(accessible);
            edgeChoice = new Spinner(this);
            edgeAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, new ArrayList<>()) {
                @Override public View getView(int position, View convert, android.view.ViewGroup parent) {
                    View view = super.getView(position, convert, parent);
                    view.setContentDescription(edgeDescription(position)); return view;
                }
                @Override public View getDropDownView(int position, View convert, android.view.ViewGroup parent) {
                    View view = super.getDropDownView(position, convert, parent);
                    view.setContentDescription(edgeDescription(position)); return view;
                }
            };
            edgeChoice.setAdapter(edgeAdapter);
            edgeChoice.setContentDescription("Choose a loop edge");
            alternatives.addView(edgeChoice, new LinearLayout.LayoutParams(-1, dp(56)));
            loopTool = button("Cycle"); loopTool.setContentDescription("Cycle selected edge");
            loopTool.setOnClickListener(v -> {
                int edge = edgeChoice.getSelectedItemPosition();
                change(next -> { ((Slitherlink) next).cycle(edge); return null; });
            });
            alternatives.addView(loopTool);
            loopLine = button("Line"); loopCross = button("Cross");
            loopLine.setContentDescription("Enter or clear line on selected edge");
            loopCross.setContentDescription("Enter or clear cross on selected edge");
            loopLine.setOnClickListener(v -> markEdge(edgeChoice.getSelectedItemPosition(), 1));
            loopCross.setOnClickListener(v -> markEdge(edgeChoice.getSelectedItemPosition(), 2));
            alternatives.addView(loopLine); alternatives.addView(loopCross);
            edgeUndo = button("Undo"); edgeRedo = button("Redo"); edgeClear = button(getString(R.string.clear_action));
            edgeUndo.setOnClickListener(v -> restoreMove(false));
            edgeRedo.setOnClickListener(v -> restoreMove(true));
            edgeClear.setContentDescription(getString(R.string.clear_puzzle));
            edgeClear.setOnClickListener(v -> requestClear());
            alternatives.addView(edgeUndo); alternatives.addView(edgeRedo); alternatives.addView(edgeClear);
            return;
        }
        LinearLayout grid = column();
        int n = game.size(), cellSize = cellSize(game instanceof Nonogram ? n + 2 : n);
        TextView measure = inequality(-1, true, "");
        measure.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int inequalitySpace = Math.max(dp(24), Math.max(measure.getMeasuredWidth(), measure.getMeasuredHeight()) + dp(4));
        int clueWidth = 0, clueHeight = 0;
        if (game instanceof Nonogram) {
            pictureClues = new TextView[n * 2];
            for (int index = 0; index < n * 2; index++) {
                TextView clue = pictureClue(index);
                clue.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                if (index < n) clueWidth = Math.max(clueWidth, clue.getMeasuredWidth());
                else clueHeight = Math.max(clueHeight, clue.getMeasuredHeight());
            }
            LinearLayout clues = row(); clues.addView(text("", 16), new LinearLayout.LayoutParams(clueWidth, clueHeight));
            for (int col = 0; col < n; col++) {
                TextView clue = pictureClues[n + col];
                clue.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
                clues.addView(clue, new LinearLayout.LayoutParams(cellSize, clueHeight));
            }
            grid.addView(clues);
        }
        for (int r = 0; r < n; r++) {
            LinearLayout line = row();
            if (game instanceof Nonogram) {
                TextView clue = pictureClues[r]; clue.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                clue.setPaddingRelative(dp(3), dp(3), dp(6), dp(3));
                line.addView(clue, new LinearLayout.LayoutParams(clueWidth, cellSize));
            }
            for (int c = 0; c < n; c++) {
                int index = r * n + c; Button square = button("");
                square.setTextSize(20); square.setTag("cell-" + index); square.setOnClickListener(v -> cell(index));
                if (game instanceof Minesweeper || game instanceof Takuzu || game instanceof Nonogram)
                    BinaryPress.attach(square, game instanceof Minesweeper ? "Toggle flag"
                                    : game instanceof Nonogram ? "Enter or clear cross" : "Enter or clear 1",
                            () -> secondaryCell(index));
                cells.add(square); line.addView(square, new LinearLayout.LayoutParams(cellSize, cellSize));
                if (game instanceof Futoshiki && c < n - 1) {
                    int sign = ((Futoshiki) game).horizontal[r * (n - 1) + c];
                    line.addView(inequality(sign, false, "Row " + (r + 1) + " columns " + (c + 1) + " and " + (c + 2)),
                            new LinearLayout.LayoutParams(inequalitySpace, cellSize));
                }
            }
            grid.addView(line);
            if (game instanceof Futoshiki && r < n - 1) {
                LinearLayout between = row();
                for (int c = 0; c < n; c++) {
                    int sign = ((Futoshiki) game).vertical[r * n + c];
                    between.addView(inequality(sign, true, "Column " + (c + 1) + " rows " + (r + 1) + " and " + (r + 2)),
                            new LinearLayout.LayoutParams(cellSize, inequalitySpace));
                    if (c < n - 1) between.addView(text("", 16), new LinearLayout.LayoutParams(inequalitySpace, inequalitySpace));
                }
                grid.addView(between);
            }
        }
        horizontal(grid);
        if (game instanceof Takuzu) binaryGrid = grid;
        if (game instanceof Minesweeper) mineGrid = grid;
        if (game instanceof Futoshiki) {
            futoGrid = grid;
            LinearLayout bank = row(); bank.setTag("puzzle-input");
            futoBank = bank;
            for (int value = 0; value <= 4; value++) {
                int digit = value;
                View key = value == 0 ? iconButton("Erase", R.drawable.ic_erase) : button("" + value);
                key.setTag("digit-" + value);
                futoKeys.add(key);
                key.setOnClickListener(v -> {
                    saveSession.dispatch(owner -> owner.enterFutoshiki(digit));
                });
                LinearLayout.LayoutParams keyLayout = new LinearLayout.LayoutParams(0, dp(56), 1);
                if (value < 4) keyLayout.setMarginEnd(dp(8));
                bank.addView(key, keyLayout);
            }
            addBoardControls(bank);
            if (sideActions == null) {
                LinearLayout.LayoutParams bankLayout = (LinearLayout.LayoutParams) bank.getLayoutParams();
                bankLayout.topMargin = dp(16);
                bank.setLayoutParams(bankLayout);
            }
        }
        if (game instanceof Minesweeper) {
            mineTool = button(""); mineTool.setSingleLine(true); mineTool.setHorizontallyScrolling(false);
            mineTool.setAutoSizeTextTypeUniformWithConfiguration(9, 16, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
            mineTool.setOnClickListener(v -> saveSession.dispatch(owner -> { owner.flag = !owner.flag; owner.refresh(null); }));
            mineTool.setTag("puzzle-input");
            addBoardControls(mineTool);
        }
    }

    private void refresh(String message) {
        if (status == null) return;
        showingLimit = limited();
        syncTime();
        newPuzzle.setEnabled(!busy); retry.setEnabled(!busy);
        clearPuzzle.setEnabled(!showingLimit && !busy && progress != null && progress.canClear());
        clearPuzzle.setTextColor(clearPuzzle.isEnabled() ? palette.accent : palette.muted);
        if (share != null) share.setEnabled(!busy && game != null && !loadFailed);
        undo.setEnabled(!showingLimit && !busy && progress != null && progress.canUndo());
        undo.setAlpha(undo.isEnabled() ? 1f : .38f);
        redo.setEnabled(!showingLimit && !busy && progress != null && progress.canRedo());
        redo.setAlpha(redo.isEnabled() ? 1f : .38f);
        if (edgeUndo != null) edgeUndo.setEnabled(undo.isEnabled());
        if (edgeRedo != null) edgeRedo.setEnabled(redo.isEnabled());
        if (edgeClear != null) {
            edgeClear.setEnabled(clearPuzzle.isEnabled());
            edgeClear.setTextColor(clearPuzzle.getCurrentTextColor());
        }
        retry.setVisibility((loadFailed || generationFailed) && !busy ? View.VISIBLE : View.GONE);
        boolean terminal = game != null && (game.solved() || game.lost());
        if (terminal) { binaryArmed = -1; selected = -1; futoArmed = 0; }
        String[] binaryErrors = game instanceof Takuzu ? ((Takuzu) game).errors() : null;
        String[] futoErrors = game instanceof Futoshiki ? ((Futoshiki) game).errors() : null;
        Slitherlink.Errors loopErrors = game instanceof Slitherlink ? ((Slitherlink) game).errors() : null;
        boolean[] pictureErrors = game instanceof Nonogram ? ((Nonogram) game).contradictions() : null;
        int errorCount = 0;
        if (binaryErrors != null) for (String error : binaryErrors) if (error != null) errorCount++;
        if (futoErrors != null) for (String error : futoErrors) if (error != null) errorCount++;
        String normal = game == null ? showingLimit ? ResponsibleMode.LIMIT_MESSAGE : "" : game.solved() ? "Puzzle complete!" : game.lost() ? "Mine hit"
                : "";
        String feedback = message != null ? message : normal;
        if (!android.text.TextUtils.equals(status.getText(), feedback)) status.setText(feedback);
        status.setVisibility(feedback.isEmpty() ? View.GONE : View.VISIBLE);
        puzzleTitle.setVisibility(feedback.isEmpty() ? View.VISIBLE : View.INVISIBLE);
        boolean contradiction = errorCount > 0 || loopErrors != null && loopErrors.any();
        boolean lost = game != null && game.lost();
        status.setTextColor(lost || (message != null && !busy || contradiction) && !terminal ? palette.error : palette.ink);
        status.setTextSize(terminal ? 24 : 16);
        status.setTypeface(Typeface.DEFAULT, terminal ? Typeface.BOLD : Typeface.NORMAL);
        status.setBackgroundColor(lost ? palette.errorFill : terminal ? palette.tint : android.graphics.Color.TRANSPARENT);
        if (!feedback.isEmpty() && !terminal) status.setBackgroundColor(palette.background);
        if (pictureErrors != null) for (int index = 0; index < pictureClues.length; index++) {
            TextView clue = pictureClues[index];
            if (clue == null) continue;
            boolean column = index >= game.size(), error = pictureErrors[index];
            int line = index % game.size();
            boolean satisfied = !error && ((Nonogram) game).lineSatisfied(line, column);
            int[] clues = column ? ((Nonogram) game).columns[line] : ((Nonogram) game).rows[line];
            String label = runs(clues, column ? "\n" : " ");
            if (!android.text.TextUtils.equals(clue.getText(), label)) clue.setText(label);
            clue.setContentDescription((column ? "Column " : "Row ") + (line + 1) + " runs " + runs(clues, ", ")
                    + (error ? ", contradiction: no placement of these runs fits the current filled and crossed-empty squares. Blank squares are undecided."
                            : satisfied ? ", satisfied: filled runs match this clue."
                            : ", can still fit the current marks."));
            clue.setTextColor(error ? palette.error : palette.ink);
            GradientDrawable fill = surface(satisfied ? palette.tint : palette.surface,
                    error ? palette.error : satisfied ? palette.accent : palette.line);
            if (error) fill.setStroke(dp(2), palette.error, dp(4), dp(2));
            else if (satisfied) fill.setStroke(dp(2), palette.accent);
            clue.setBackground(fill);
        }
        if (binaryBank != null) for (int value = 0; value < 3; value++) {
            View key = binaryKeys[value]; boolean armed = binaryArmed == value;
            String label = value == 2 ? "Erase" : "" + value;
            if (key instanceof Button) ((Button) key).setText(label);
            key.setSelected(armed);
            key.setContentDescription(label + (armed ? ", selected. Tap again to return to default tap 0, hold 1."
                    : ", select then tap squares repeatedly. With no tool selected, tap 0 or hold 1; repeat the same value to clear."));
            key.setTooltipText(label);
            key.setEnabled(!showingLimit && !busy && game != null && !terminal);
            key.setAlpha(key.isEnabled() ? 1f : .38f);
            int ink = value == 2 ? palette.accent : binaryColor(value, false);
            if (key instanceof Button) ((Button) key).setTextColor(ink);
            GradientDrawable fill = surface(value == 2 ? palette.surface : binaryColor(value, true), armed ? ink : palette.line);
            if (armed) fill.setStroke(dp(3), ink);
            actionSurface(key, fill);
        }
        for (int i = 0; i < cells.size(); i++) {
            Button cell = cells.get(i); String label = "", meaning = ""; boolean fixed = false;
            if (game instanceof Takuzu) {
                Takuzu p = (Takuzu) game; label = p.values[i] < 0 ? "" : "" + p.values[i]; fixed = p.clues[i] >= 0;
                meaning = label.isEmpty() ? "empty" : label;
                meaning += p.clues[i] >= 0 ? "" : binaryArmed < 0 ? ", tap to enter or clear 0, hold to enter or clear 1"
                        : binaryArmed == 2 ? ", tap to erase" : ", tap to enter " + binaryArmed;
            } else if (game instanceof Nonogram) {
                int value = ((Nonogram) game).values[i];
                label = value == 1 ? "\u2713" : value == 2 ? "\u00d7" : ""; meaning = value == 1 ? "filled" : value == 2 ? "crossed empty" : "undecided";
                meaning += value == 0 ? ", tap to fill, hold to cross"
                        : value == 1 ? ", tap to cross, hold to cross"
                        : ", tap or hold to clear";
            } else if (game instanceof Futoshiki) {
                Futoshiki p = (Futoshiki) game; label = p.values[i] == 0 ? "" : "" + p.values[i]; fixed = p.clues[i] != 0;
                meaning = label.isEmpty() ? "empty" : label;
                if (!fixed && !terminal && futoArmed != 0) meaning += ", tap to enter " + futoArmed;
            } else if (game instanceof Minesweeper) {
                Minesweeper p = (Minesweeper) game;
                if (p.cells[i] == 1 || p.lost() && p.mines[i] == 1) {
                    label = p.mines[i] == 1 ? "*" : p.adjacent(i) == 0 ? "\u00b7" : "" + p.adjacent(i);
                    meaning = p.mines[i] == 1 ? "mine" : p.adjacent(i) + " neighboring mines";
                    if (p.mines[i] == 1 && p.cells[i] == 1) { label = "*!"; meaning = "detonated mine, game lost"; }
                } else { label = p.cells[i] == 2 ? "F" : ""; meaning = p.cells[i] == 2 ? "flagged" : "covered"; }
                fixed = p.cells[i] == 1;
            }
            String error = binaryErrors != null ? binaryErrors[i] : futoErrors == null ? null : futoErrors[i];
            if (error != null) { label += "!"; meaning += ", error: " + error; }
            cell.setText(label);
            cell.setSelected(selected == i);
            cell.setContentDescription("Row " + (i / game.size() + 1) + " column " + (i % game.size() + 1) + ", " + meaning
                    + (fixed ? game instanceof Minesweeper ? ", revealed" : ", fixed" : "")
                    + (selected == i ? ", selected entry target" : ""));
            cell.setEnabled(!showingLimit && !busy && !terminal && (!fixed || game instanceof Takuzu || game instanceof Minesweeper));
            cell.setTypeface(Typeface.DEFAULT, fixed ? Typeface.BOLD : Typeface.NORMAL);
            cell.setPaintFlags(game instanceof Takuzu && fixed || game instanceof Futoshiki && selected == i
                    ? cell.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG
                    : cell.getPaintFlags() & ~android.graphics.Paint.UNDERLINE_TEXT_FLAG);
            if (game instanceof Takuzu) {
                int value = ((Takuzu) game).values[i];
                cell.setTextColor(value < 0 ? palette.accent : binaryColor(value, false));
                int stroke = selected == i ? binaryColor(0, false) : error != null ? palette.error : palette.line;
                GradientDrawable fill = surface(value < 0 ? palette.surface : binaryColor(value, true), stroke);
                if (selected == i || error != null) fill.setStroke(dp(2), stroke);
                actionSurface(cell, fill);
                continue;
            }
            if (game instanceof Minesweeper) {
                Minesweeper mine = (Minesweeper) game;
                boolean redMine = mine.lost() && mine.mines[i] == 1;
                cell.setTextColor(redMine ? palette.error : palette.accent);
                GradientDrawable fill = surface(redMine ? palette.errorFill : mine.cells[i] == 1 ? palette.background : palette.tint,
                        redMine ? palette.error : palette.line);
                if (redMine) fill.setStroke(dp(mine.cells[i] == 1 ? 3 : 1), palette.error);
                actionSurface(cell, fill);
                continue;
            }
            boolean filled = game instanceof Nonogram && ((Nonogram) game).values[i] == 1;
            cell.setTextColor(error != null ? palette.error : filled ? palette.surface : palette.accent);
            GradientDrawable fill = surface(error != null ? palette.errorFill : filled ? palette.ink : selected == i ? palette.tint : palette.surface,
                    error != null ? palette.error : selected == i ? palette.accent : palette.line);
            if (error != null) fill.setStroke(dp(3), palette.error);
            actionSurface(cell, fill);
            if (pendingPictureCells.get(i) > 0) showPicturePending(i);
        }
        for (int value = 0; value < futoKeys.size(); value++) {
            View key = futoKeys.get(value);
            boolean armed = value != 0 && value == futoArmed;
            String action = value == 0 ? "Erase" : "Enter " + value;
            key.setContentDescription(selected >= 0 ? action + ", row " + (selected / 4 + 1) + " column " + (selected % 4 + 1)
                    : value == 0 ? "Erase, choose a square first" : armed ? value + " armed, tap to disarm" : "Arm " + value);
            key.setSelected(armed);
            key.setEnabled(!showingLimit && !busy && !terminal && (value != 0 || selected >= 0));
            key.setAlpha(key.isEnabled() ? 1f : .38f);
            GradientDrawable fill = surface(armed ? palette.tint : palette.surface, armed ? palette.accent : palette.line);
            if (armed) fill.setStroke(dp(2), palette.accent);
            actionSurface(key, fill);
        }
        if (paths != null) paths.show((Numberlink) game, selected, !busy && !terminal);
        if (loop != null) loop.show((Slitherlink) game, loopErrors, !busy && !terminal);
        for (int cell = 0; cell < loopClues.size(); cell++) {
            Slitherlink p = (Slitherlink) game; int count = 0;
            for (int edge : p.sides[cell]) if (p.edges[edge] == 1) count++;
            loopClues.get(cell).setText("Row " + (cell / p.size() + 1) + " column " + (cell % p.size() + 1) + ", "
                    + (p.clues[cell] < 0 ? "blank, no count constraint" : "clue " + p.clues[cell])
                    + ", " + count + " sides drawn.");
            loopClues.get(cell).setContentDescription(loopClues.get(cell).getText()
                    + (loopErrors.cells[cell] == null ? "" : " Error: " + loopErrors.cells[cell]));
            loopClues.get(cell).setTextColor(loopErrors.cells[cell] == null ? palette.ink : palette.error);
        }
        if (edgeChoice != null) {
            int choice = Math.max(0, edgeChoice.getSelectedItemPosition());
            edgeAdapter.setNotifyOnChange(false); edgeAdapter.clear();
            int[] edges = ((Slitherlink) game).edges;
            for (int edge = 0; edge < edges.length; edge++) edgeAdapter.add(edgeName(edge)
                    + (edges[edge] == 1 ? ", line" : edges[edge] == 2 ? ", crossed out" : ", undecided"));
            edgeAdapter.notifyDataSetChanged(); edgeChoice.setSelection(choice);
            edgeChoice.setEnabled(!showingLimit && !busy && !terminal); loopTool.setEnabled(!showingLimit && !busy && !terminal);
            loopLine.setEnabled(!showingLimit && !busy && !terminal); loopCross.setEnabled(!showingLimit && !busy && !terminal);
        }
        if (mineTool != null) {
            String mineAction = (flag ? "Flag mode, switch to reveal" : "Reveal mode, switch to flag") + ". "
                    + ((Minesweeper) game).flags() + " of " + ((Minesweeper) game).mineCount + " flags placed.";
            mineTool.setContentDescription(mineAction); mineTool.setTooltipText(mineAction);
            String maximum = ((Minesweeper) game).mineCount < 10 ? "8" : "88";
            ControlStyle.countedIcon(mineTool, R.drawable.ic_flag,
                    ((Minesweeper) game).flags() + "/" + ((Minesweeper) game).mineCount,
                    "888/" + maximum, flag ? palette.ink : palette.accent);
            ControlStyle.state(mineTool, flag);
            GradientDrawable toolFill = surface(flag ? palette.tint : palette.surface, flag ? palette.accent : palette.line);
            if (flag) toolFill.setStroke(dp(3), palette.accent);
            actionSurface(mineTool, toolFill);
            mineTool.setEnabled(!showingLimit && !busy && !terminal);
            mineTool.setAlpha(mineTool.isEnabled() ? 1f : .38f);
        }
        if (mineGrid != null) {
            GradientDrawable outline = new GradientDrawable(); outline.setColor(android.graphics.Color.TRANSPARENT);
            if (lost) outline.setStroke(dp(3), palette.error);
            mineGrid.setForeground(outline);
        }
    }

    private void addBoardControls(View controls) {
        LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(-1, -2); slot.topMargin = dp(12);
        if (sideActions == null) boardArea.addView(controls, slot);
        else { sideActions.addView(controls, sideActions.getChildCount() - 1, slot); sideControls.add(controls); }
    }

    private String edgeDescription(int edge) {
        Slitherlink loop = (Slitherlink) game;
        String reason = loop.errors().edges[edge];
        return edgeName(edge) + (loop.edges[edge] == 1 ? ", line" : loop.edges[edge] == 2 ? ", crossed out" : ", undecided")
                + (reason == null ? "" : ". Error: " + reason);
    }

    private int binaryColor(int value, boolean fill) {
        boolean dark = android.graphics.Color.luminance(palette.background) < .5;
        return android.graphics.Color.parseColor(fill ? dark ? value == 0 ? "#153D3D" : "#48331C"
                : value == 0 ? "#D6F2F1" : "#FBE5BE"
                : dark ? value == 0 ? "#72E2DC" : "#FFD080" : value == 0 ? "#005A56" : "#744600");
    }

    private TextView pictureClue(int index) {
        Nonogram picture = (Nonogram) game; int n = picture.size();
        TextView clue = text(runs(index < n ? picture.rows[index] : picture.columns[index - n], index < n ? " " : "\n"), 16);
        clue.setFocusable(true); clue.setPaddingRelative(dp(3), dp(3), dp(index < n ? 6 : 3), dp(3));
        clue.setTag("nonogram-" + (index < n ? "row-" : "column-") + index % n);
        pictureClues[index] = clue;
        return clue;
    }

    private void horizontal(View board) {
        board.setTag("puzzle-board");
        board.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        boolean wide = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        playViewport.setMinimumContentWidth(Math.max(dp(wide ? 720 : 360), board.getMeasuredWidth()
                + dp(16 + (wide ? 220 * Math.max(1, getResources().getConfiguration().fontScale) : 0))));
        boardArea.addView(board, new LinearLayout.LayoutParams(-2, -2));
        if (!wide) {
            int width = board.getMeasuredWidth();
            for (int i = 0; i < content.getChildCount(); i++) {
                View child = content.getChildAt(i);
                if (child == boardArea || child == binaryBank || "puzzle-actions".equals(child.getTag())) {
                    LinearLayout.LayoutParams slot = (LinearLayout.LayoutParams) child.getLayoutParams();
                    slot.width = width; slot.gravity = Gravity.CENTER_HORIZONTAL; child.setLayoutParams(slot);
                }
            }
        }
    }
    private TextView inequality(int sign, boolean vertical, String name) {
        TextView view = text(sign == 0 ? "" : vertical ? sign < 0 ? "^" : "v" : sign < 0 ? "<" : ">", 19);
        view.setGravity(Gravity.CENTER);
        if (sign != 0) view.setContentDescription(name + (sign < 0 ? ", first is smaller" : ", first is larger"));
        return view;
    }
    private static String runs(int[] clues, String separator) {
        if (clues.length == 0) return "0";
        StringBuilder text = new StringBuilder();
        for (int clue : clues) { if (text.length() > 0) text.append(separator); text.append(clue); }
        return text.toString();
    }
    private String edgeName(int edge) {
        Slitherlink loop = (Slitherlink) game;
        int a = loop.grid.a[edge], b = loop.grid.b[edge], width = loop.size() + 1;
        return (b == a + 1 ? "Horizontal" : "Vertical") + " edge at dot row " + (a / width + 1) + " column " + (a % width + 1);
    }
    private int cellSize(int columns) {
        float scale = getResources().getConfiguration().fontScale;
        return Math.max(dp(48 * Math.max(1, scale)), Math.min(dp(64), (getResources().getDisplayMetrics().widthPixels - dp(28)) / columns));
    }
    private LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout row() { LinearLayout view = new LinearLayout(this); view.setGravity(Gravity.CENTER_VERTICAL); return view; }
    private ImageButton iconButton(String name, int resource) {
        ImageButton button = new ImageButton(this); button.setImageResource(resource);
        button.setImageTintList(android.content.res.ColorStateList.valueOf(palette.accent));
        button.setContentDescription(name); button.setTooltipText(name);
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setMinimumWidth(dp(48)); button.setMinimumHeight(dp(48));
        button.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        button.setStateListAnimator(null); button.setElevation(0);
        ControlStyle.navigation(button, android.content.res.ColorStateList.valueOf((palette.ink & 0xFFFFFF) | 0x30000000));
        return button;
    }
    private TextView text(String text, int size) { TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(palette.ink); view.setGravity(Gravity.CENTER); return view; }
    Button button(String text) {
        Button view = new Button(this); view.setText(text); view.setAllCaps(false); view.setTextSize(16);
        view.setMinWidth(0); view.setMinimumWidth(0); view.setMinHeight(dp(48)); view.setMinimumHeight(dp(48));
        view.setPadding(dp(4), dp(4), dp(4), dp(4)); view.setStateListAnimator(null);
        view.setTextColor(palette.accent);
        view.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf((palette.ink & 0xFFFFFF) | 0x30000000),
                surface(palette.surface, palette.line), null));
        return view;
    }
    private GradientDrawable surface(int fill, int stroke) {
        GradientDrawable bg = new GradientDrawable(); bg.setColor(fill); bg.setCornerRadius(dp(5)); bg.setStroke(dp(1), stroke); return bg;
    }
    private void actionSurface(View view, GradientDrawable fill) {
        view.setBackground(ControlStyle.ripple(
                android.content.res.ColorStateList.valueOf((palette.ink & 0xFFFFFF) | 0x30000000), fill));
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (mode == Mode.NONOGRAM || mode == Mode.TAKUZU || mode == Mode.MINESWEEPER) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) canceledMultiTouch = false;
            if (canceledMultiTouch) return true;
            if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
                // Intercept before ViewGroup can split the pointers across different buttons.
                canceledMultiTouch = true;
                MotionEvent cancel = MotionEvent.obtain(event);
                try {
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(cancel);
                } finally { cancel.recycle(); }
                return true;
            }
        }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && feedback != null) feedback.stop();
        if (mode == Mode.TAKUZU && event.getActionMasked() == MotionEvent.ACTION_DOWN
                && !inside(binaryGrid, event) && !inside(binaryBank, event)
                && !inside(gameSettingsButton, event) && (selected >= 0 || binaryArmed >= 0)) {
            selected = -1; binaryArmed = -1; refresh(null);
        }
        boolean outsideFuto = futoGrid != null && !inside(futoGrid, event)
                && !inside(futoBank, event) && !inside(gameSettingsButton, event);
        if (outsideFuto && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            saveSession.dispatch(owner -> {
                if (owner.futoArmed != 0) { owner.futoArmed = 0; owner.refresh(null); }
            });
        }
        boolean handled = super.dispatchTouchEvent(event);
        if (outsideFuto && event.getActionMasked() == MotionEvent.ACTION_UP) {
            handler.post(() -> saveSession.dispatch(owner -> {
                if (owner.selected >= 0) { owner.selected = -1; owner.refresh(null); }
            }));
        }
        return handled;
    }
    private boolean inside(View view, MotionEvent event) {
        android.graphics.Rect bounds = new android.graphics.Rect();
        return view != null && view.getGlobalVisibleRect(bounds) && bounds.contains((int) event.getRawX(), (int) event.getRawY());
    }
    private void showMenu() {
        newRequested = false;
        if (!shared && !getIntent().getBooleanExtra(FROM_MENU, false)) startActivity(new Intent(this, PuzzleMenuActivity.class).putExtra(MODE, mode.name()));
        finish();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        PuzzleSharing.warm(this, intent);
    }

    @SuppressWarnings("deprecation")
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { showMenu(); }

    @Override protected void onSaveInstanceState(Bundle out) {
        saveSession.dispatch(PuzzleActivity::checkpointTime);
        super.onSaveInstanceState(out); out.putInt("selected", selected); out.putBoolean("flag", flag); out.putInt("binaryArmed", binaryArmed);
        out.putInt("futoArmed", futoArmed);
        out.putBoolean("newRequested", newRequested); out.putInt(NEW_SETTING, newSetting);
        out.putByteArray("replacementSave", replacementSave);
    }

    private void syncTime() {
        playTime.setRunning(!showingLimit && resumed && focused && !busy && game != null && !game.solved() && !game.lost());
        if (clock == null) return;
        long elapsed = playTime.elapsedMillis();
        String label = showingLimit ? ResponsibleMode.LIMIT_MESSAGE : ActivePlayTimer.label(elapsed);
        if (!android.text.TextUtils.equals(clock.getText(), label)) clock.setText(label);
        clock.setContentDescription(showingLimit ? label : ActivePlayTimer.description(elapsed));
        clock.setVisibility(game == null ? View.GONE : View.VISIBLE);
    }

    private void checkpointTime() {
        if (saveSession != null && !saveSession.idle()) return;
        if (progress == null) return;
        PuzzleProgress timed = progress.withElapsed(playTime.elapsedMillis());
        if (timed.elapsedMillis != durableElapsed) {
            progress = timed;
            saves.checkpoint(() -> write(timed, PuzzleSave.Edit.CHECKPOINT, ResponsibleMode.today()), ex -> {
                Log.e("Puzzle", "Timer checkpoint failed for " + mode, ex);
                long failedRevision = savedRevision;
                handler.post(() -> { if (!isDestroyed() && savedRevision == failedRevision) saveError(ex); });
            });
        }
    }

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (showingLimit != limited()) refresh(null); else syncTime();
            if (playTime.elapsedMillis() - durableElapsed >= 10000) checkpointTime();
            if (resumed) handler.postDelayed(this, 1000);
        }
    };

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        focused = hasFocus; syncTime();
        if (!hasFocus) {
            minesFeedback.stop();
            if (feedback != null) feedback.stop();
            if (loop != null) loop.cancelPress();
            if (paths != null) paths.cancelDrag();
            checkpointTime();
            if (completion != null) completion.stop();
        }
    }

    @Override protected void onResume() {
        super.onResume(); resumed = true; syncTime();
        handler.removeCallbacks(ticker); handler.post(ticker);
    }

    @Override protected void onPause() {
        minesFeedback.stop();
        if (feedback != null) feedback.stop();
        if (loop != null) loop.cancelPress();
        if (paths != null) paths.cancelDrag();
        resumed = false; syncTime(); saveSession.dispatch(PuzzleActivity::checkpointTime);
        handler.removeCallbacks(ticker);
        if (completion != null) completion.stop();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (saveSession != null) saveSession.detach(this, !isChangingConfigurations());
        minesFeedback.stop();
        if (feedback != null) feedback.stop();
        if (completion != null) completion.stop();
        if (pending != null) pending.cancel(true);
        worker.shutdownNow(); handler.removeCallbacksAndMessages(null); super.onDestroy();
    }
    @Override public Object onRetainNonConfigurationInstance() { return new Retained(saveSession); }
}
