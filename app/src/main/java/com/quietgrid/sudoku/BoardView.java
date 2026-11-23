package com.quietgrid.sudoku;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.view.accessibility.AccessibilityManager;

public final class BoardView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final MainActivity.Palette palette;
    private Game game;
    private boolean highlightHouses;
    private final boolean[] armedHouseCells = new boolean[81];
    private float left, top, cellSize;
    private Runnable selectionChanged, entryChanged;
    private int accessibilityFocus = -1, hovered = -1;
    private int rejectedCell = -1;
    private long rejectionStarted;
    private android.animation.ValueAnimator entryAnimator;
    private int animatedCell = -1;
    private float entryProgress;
    private int completedHouses;
    private final boolean[] completedCells = new boolean[81];
    private final Runnable clearRejection = () -> { rejectedCell = -1; invalidate(); };
    private int pressedCell = -1, pressedPointer = -1;
    private float pressedX, pressedY;
    private boolean held, canceledPress;
    private boolean pendingArm;
    private int pendingDigit;
    private int effectiveArmedDigit() { return pendingArm ? pendingDigit : game.armedDigit; }
    void clearPendingInput() { pendingArm = false; }
    void acknowledgeInput() {
        if (pendingArm && game != null && (game.armedDigit == pendingDigit || game.won()
                || pendingDigit != 0 && game.remaining(pendingDigit) == 0)) pendingArm = false;
    }
    private final Runnable hold = () -> {
        if (!canceledPress && pressedCell >= 0 && game != null && effectiveArmedDigit() != 0 && hasWindowFocus()) {
            held = true;
            note(pressedCell);
        }
    };

    int rejectedCell() { return rejectedCell; }
    int animatedCell() { return animatedCell; }
    int animatedHouses() { return completedHouses; }
    boolean animatesHouseCell(int cell) { return completedCells[cell]; }

    public void clearEntryFeedback() {
        cancelPress();
        if (entryAnimator != null) {
            entryAnimator.removeAllListeners();
            entryAnimator.cancel();
            entryAnimator = null;
        }
        animatedCell = -1;
        completedHouses = 0;
        java.util.Arrays.fill(completedCells, false);
        removeCallbacks(clearRejection);
        rejectedCell = -1;
        invalidate();
    }

    private void acknowledgeEntry(int cell, int previousHouses) {
        clearEntryFeedback();
        if (game.noteMode || game.won() || !android.animation.ValueAnimator.areAnimatorsEnabled()) return;
        completedHouses = game.completedHouses(cell) & ~previousHouses;
        for (int h = 0; h < Sudoku.HOUSES.length; h++) if ((completedHouses & (1 << h)) != 0) {
            for (int member : Sudoku.HOUSES[h]) completedCells[member] = true;
        }
        animatedCell = cell;
        entryProgress = 0f;
        entryAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f);
        entryAnimator.setDuration(completedHouses == 0 ? 480 : 900);
        entryAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
        entryAnimator.addUpdateListener(animation -> {
            entryProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        entryAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                animatedCell = -1;
                completedHouses = 0;
                java.util.Arrays.fill(completedCells, false);
                entryAnimator = null;
                invalidate();
            }
        });
        entryAnimator.start();
    }

    private void reject(int cell) {
        clearEntryFeedback();
        rejectedCell = cell;
        rejectionStarted = android.os.SystemClock.uptimeMillis();
        removeCallbacks(clearRejection);
        postDelayed(clearRejection, 550);
        invalidate();
        announceForAccessibility("Cannot place that number. It is already in this row, column, or box.");
    }

    private boolean conflictingEntry(int cell, int digit) {
        return cell >= 0 && digit != 0 && !game.noteMode && game.clues[cell] == 0
                && game.values[cell] != digit && game.conflicts(cell, digit);
    }

    public void chooseDigit(int digit) {
        if (dispatcher != null) {
            if (game != null && game.selected < 0 && !game.won() && game.remaining(digit) > 0) {
                pendingDigit = effectiveArmedDigit() == digit ? 0 : digit; pendingArm = true;
            }
            dispatcher.accept(view -> view.chooseDigitNow(digit)); return;
        }
        chooseDigitNow(digit);
    }
    private void chooseDigitNow(int digit) {
        if (game == null) return;
        clearEntryFeedback();
        int target = game.selected;
        int previousHouses = game.completedHouses(target);
        boolean conflict = conflictingEntry(target, digit);
        boolean entered = game.chooseDigit(digit);
        if (conflict) reject(target);
        else if (entered) acknowledgeEntry(target, previousHouses);
        if (entered && entryChanged != null) entryChanged.run();
        else if (selectionChanged != null) selectionChanged.run();
    }

    @Override protected void onDetachedFromWindow() {
        cancelPress();
        removeCallbacks(clearRejection);
        clearEntryFeedback();
        rejectedCell = -1;
        super.onDetachedFromWindow();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != VISIBLE) clearEntryFeedback();
    }

    public BoardView(Context context, MainActivity.Palette palette) {
        super(context);
        this.palette = palette;
        setFocusable(true); setFocusableInTouchMode(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    public BoardView(Context context) {
        this(context, new MainActivity.Palette((context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES, 3));
    }

    public void setOnSelectionChanged(Runnable listener) { selectionChanged = listener; }
    public void setOnEntryChanged(Runnable listener) { entryChanged = listener; }
    private java.util.function.Consumer<java.util.function.Consumer<BoardView>> dispatcher;
    public void setDispatcher(java.util.function.Consumer<java.util.function.Consumer<BoardView>> dispatcher) { this.dispatcher = dispatcher; }
    void committed(Game previous) {
        for (int cell = 0; cell < 81; cell++) if (previous.values[cell] != game.values[cell] && game.values[cell] != 0) {
            acknowledgeEntry(cell, previous.completedHouses(cell)); break;
        }
    }
    void setHighlightHouses(boolean enabled) {
        if (highlightHouses == enabled) return;
        highlightHouses = enabled;
        invalidate();
    }

    public void setGame(Game game) {
        if (game != null && this.game != null && !java.util.Arrays.equals(game.solution, this.game.solution)) pendingArm = false;
        if (this.game != game) clearEntryFeedback();
        this.game = game;
        invalidate();
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (animatedCell >= 0 && !android.animation.ValueAnimator.areAnimatorsEnabled()) clearEntryFeedback();
        float side = Math.max(0, Math.min(getWidth() - dp(4), getHeight() - dp(12)));
        left = (getWidth() - side) / 2f; top = (getHeight() - side) / 2f;
        cellSize = side / 9;
        if (cellSize <= 0) return;
        paint.setStyle(Paint.Style.FILL); paint.setColor(palette.surface);
        canvas.drawRect(left, top, left + side, top + side, paint);
        int selected = game == null ? -1 : game.selected;
        int selectedValue = game == null ? 0 : game.armedDigit != 0
                ? game.armedDigit : selected < 0 ? 0 : game.values[selected];
        if (highlightHouses && game != null) game.markArmedDigitHouses(armedHouseCells);
        for (int i = 0; i < 81; i++) {
            float x = left + i % 9 * cellSize, y = top + i / 9 * cellSize;
            if (game == null) continue;
            int value = game.values[i];
            boolean wrong = game.showMistakes && value != 0 && value != game.solution[i];
            boolean armedHouse = highlightHouses && armedHouseCells[i] && !completedCells[i]
                    && i != animatedCell && i != rejectedCell;
            int fill = i == selected ? palette.tint : wrong ? palette.errorFill
                    : selectedValue != 0 && value == selectedValue ? palette.tint
                    : armedHouse || (selected >= 0 && Sudoku.PEERS[selected][i]) ? palette.peer : palette.surface;
            paint.setColor(fill);
            canvas.drawRect(x, y, x + cellSize, y + cellSize, paint);
            if (completedCells[i]) {
                int distance = Math.abs(i / 9 - animatedCell / 9) + Math.abs(i % 9 - animatedCell % 9);
                float phase = Math.max(0f, (entryProgress - distance * .025f) / .6f);
                float impulse = phase < .18f ? phase / .18f
                        : phase < .4f ? 1f : Math.max(0f, (1f - phase) / .6f);
                paint.setColor((palette.accent & 0x00FFFFFF) | Math.round(110 * impulse) << 24);
                canvas.drawRect(x, y, x + cellSize, y + cellSize, paint);
            } else if (i == animatedCell) {
                paint.setColor((palette.accent & 0x00FFFFFF) | Math.round(100 * (1f - entryProgress)) << 24);
                canvas.drawRect(x, y, x + cellSize, y + cellSize, paint);
            }
            if (i == animatedCell) {
                float progress = Math.min(1f, entryProgress * (completedHouses == 0 ? 1f : 900f / 480f));
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2.5f));
                paint.setColor((palette.accent & 0x00FFFFFF) | Math.round(210 * (1f - progress)) << 24);
                float inset = cellSize * (.2f - .15f * progress);
                canvas.drawRoundRect(x + inset, y + inset, x + cellSize - inset, y + cellSize - inset,
                        dp(6), dp(6), paint);
                paint.setStyle(Paint.Style.FILL);
            }
            if (i == rejectedCell) {
                float fraction = Math.min(1f, (android.os.SystemClock.uptimeMillis() - rejectionStarted) / 550f);
                int alpha = android.animation.ValueAnimator.areAnimatorsEnabled()
                        ? Math.round(220 * (1f - fraction)) : 180;
                paint.setColor((palette.error & 0x00FFFFFF) | alpha << 24);
                canvas.drawRect(x, y, x + cellSize, y + cellSize, paint);
                if (fraction < 1f && android.animation.ValueAnimator.areAnimatorsEnabled()) postInvalidateOnAnimation();
            }
            if (value != 0) {
                paint.setTypeface(Typeface.create("sans-serif", game.clues[i] != 0 ? Typeface.BOLD : Typeface.NORMAL));
                paint.setTextSize(Math.min(sp(28), cellSize * 0.64f));
                paint.setColor(wrong ? palette.error : game.clues[i] != 0 ? palette.ink : palette.accent);
                paint.setTextAlign(Paint.Align.CENTER);
                float baseline = y + cellSize / 2 - (paint.ascent() + paint.descent()) / 2;
                canvas.save();
                if (i == animatedCell) {
                    float progress = Math.min(1f, entryProgress * (completedHouses == 0 ? 1f : 900f / 480f));
                    float settle = Math.min(1f, progress / .24f);
                    float release = Math.max(0f, (progress - .24f) / .76f);
                    float scale = progress < .24f ? .65f + .7f * (1f - (1f - settle) * (1f - settle))
                            : 1f + .35f * (1f - release) * (1f - release);
                    canvas.clipRect(x + dp(2), y + dp(2), x + cellSize - dp(2), y + cellSize - dp(2));
                    canvas.translate(0f, cellSize * .09f * (1f - settle) * (1f - settle));
                    canvas.scale(scale, scale, x + cellSize / 2, y + cellSize / 2);
                }
                canvas.drawText(Integer.toString(value), x + cellSize / 2, baseline, paint);
                canvas.restore();
                if (wrong) {
                    paint.setStrokeWidth(dp(1.5f));
                    canvas.drawLine(x + cellSize * 0.33f, y + cellSize * 0.86f,
                            x + cellSize * 0.67f, y + cellSize * 0.86f, paint);
                }
            } else {
                int notes = game.visibleNotes(i);
                paint.setTextSize(Math.min(sp(11), cellSize * 0.25f));
                paint.setTextAlign(Paint.Align.CENTER);
                for (int d = 1; d <= 9; d++) if ((notes & Sudoku.bit(d)) != 0) {
                    boolean match = game.armedDigit == d;
                    paint.setTypeface(Typeface.create("sans-serif", match ? Typeface.BOLD : Typeface.NORMAL));
                    float nx = x + ((d - 1) % 3 + 0.5f) * cellSize / 3;
                    float ny = y + ((d - 1) / 3 + 0.5f) * cellSize / 3;
                    if (match) {
                        paint.setColor(palette.accent);
                        canvas.drawCircle(nx, ny, cellSize * 0.145f, paint);
                    }
                    paint.setColor(match ? palette.surface : palette.muted);
                    canvas.drawText(Integer.toString(d), nx, ny - (paint.ascent() + paint.descent()) / 2, paint);
                }
            }
        }
        for (int line = 0; line <= 9; line++) {
            paint.setColor(line % 3 == 0 ? palette.ink : palette.line);
            paint.setStrokeWidth(dp(line % 3 == 0 ? 1.7f : 0.5f));
            canvas.drawLine(left + line * cellSize, top, left + line * cellSize, top + side, paint);
            canvas.drawLine(left, top + line * cellSize, left + side, top + line * cellSize, paint);
        }
        if (completedHouses != 0) {
            float crest = Math.min(1f, entryProgress / .16f)
                    * Math.min(1f, (1f - entryProgress) / .45f);
            paint.setColor((palette.accent & 0x00FFFFFF) | Math.round(220 * crest) << 24);
            paint.setStrokeWidth(dp(3));
            float inset = dp(2);
            for (int i = 0; i < 81; i++) if (completedCells[i]) {
                float x = left + i % 9 * cellSize, y = top + i / 9 * cellSize;
                if (i < 9 || !completedCells[i - 9])
                    canvas.drawLine(x + inset, y + inset, x + cellSize - inset, y + inset, paint);
                if (i >= 72 || !completedCells[i + 9])
                    canvas.drawLine(x + inset, y + cellSize - inset, x + cellSize - inset, y + cellSize - inset, paint);
                if (i % 9 == 0 || !completedCells[i - 1])
                    canvas.drawLine(x + inset, y + inset, x + inset, y + cellSize - inset, paint);
                if (i % 9 == 8 || !completedCells[i + 1])
                    canvas.drawLine(x + cellSize - inset, y + inset, x + cellSize - inset, y + cellSize - inset, paint);
            }
        }
        if (selected >= 0) {
            paint.setStyle(Paint.Style.STROKE); paint.setColor(palette.accent); paint.setStrokeWidth(dp(2.5f));
            float x = left + selected % 9 * cellSize, y = top + selected / 9 * cellSize;
            canvas.drawRect(x + dp(1.5f), y + dp(1.5f), x + cellSize - dp(1.5f), y + cellSize - dp(1.5f), paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private int cellAt(float x, float y) {
        if (cellSize <= 0 || x < left || y < top || x >= left + 9 * cellSize || y >= top + 9 * cellSize) return -1;
        return (int) ((y - top) / cellSize) * 9 + (int) ((x - left) / cellSize);
    }

    public boolean containsBoardPoint(float screenX, float screenY) {
        android.graphics.Matrix transform = new android.graphics.Matrix();
        transformMatrixToGlobal(transform);
        android.graphics.Matrix inverse = new android.graphics.Matrix();
        if (!transform.invert(inverse)) return false;
        float[] point = {screenX, screenY}; inverse.mapPoints(point);
        return cellAt(point[0], point[1]) >= 0;
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = resolveSize(Math.round(dp(320)), widthSpec);
        setMeasuredDimension(width, resolveSize(width + Math.round(dp(8)), heightSpec));
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                cancelPress(); canceledPress = false; held = false;
                pressedCell = cellAt(event.getX(), event.getY()); pressedPointer = event.getPointerId(0);
                pressedX = event.getX(); pressedY = event.getY();
                if (pressedCell >= 0 && game != null && effectiveArmedDigit() != 0)
                    postDelayed(hold, BinaryPress.timeout(getContext()));
                return true;
            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() != 1 || event.getPointerId(0) != pressedPointer
                        || cellAt(event.getX(), event.getY()) != pressedCell
                        || Math.hypot(event.getX() - pressedX, event.getY() - pressedY)
                        > android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop()) cancelPress();
                return true;
            case MotionEvent.ACTION_UP:
                if (!canceledPress && !held && pressedCell >= 0 && pressedCell == cellAt(event.getX(), event.getY())) {
                    select(pressedCell); performClick();
                }
                cancelPress(); return true;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_CANCEL:
                cancelPress(); return true;
            default: return true;
        }
    }

    private void cancelPress() {
        removeCallbacks(hold); canceledPress = true; pressedCell = -1; pressedPointer = -1;
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (!focused) cancelPress();
    }

    private void note(int cell) {
        if (dispatcher != null) { dispatcher.accept(view -> view.noteNow(cell)); return; }
        noteNow(cell);
    }
    private void noteNow(int cell) {
        if (game == null) return;
        clearEntryFeedback();
        boolean entered = game.noteSelectedDigit(cell);
        requestFocus();
        if (entered && entryChanged != null) entryChanged.run();
        else if (selectionChanged != null) selectionChanged.run();
        sendCellEvent(cell, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }

    @Override public boolean performClick() { super.performClick(); return true; }

    private void select(int cell) {
        if (dispatcher != null) { dispatcher.accept(view -> view.selectNow(cell)); return; }
        selectNow(cell);
    }
    private void selectNow(int cell) {
        if (game == null) return;
        clearEntryFeedback();
        int previousHouses = game.completedHouses(cell);
        boolean conflict = conflictingEntry(cell, game.armedDigit);
        boolean entered = game.selectCell(cell);
        if (conflict) reject(cell);
        else if (entered) acknowledgeEntry(cell, previousHouses);
        requestFocus();
        if (entered && entryChanged != null) entryChanged.run();
        else if (selectionChanged != null) selectionChanged.run();
        sendCellEvent(cell, game.selected == cell ? AccessibilityEvent.TYPE_VIEW_SELECTED
                : AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }

    @Override public boolean onKeyDown(int code, KeyEvent event) {
        if (game == null) return super.onKeyDown(code, event);
        int cell = game.selected < 0 ? 0 : game.selected;
        if (code == KeyEvent.KEYCODE_DPAD_LEFT) select(Math.max(0, cell - 1));
        else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) select(Math.min(80, cell + 1));
        else if (code == KeyEvent.KEYCODE_DPAD_UP) select(Math.max(0, cell - 9));
        else if (code == KeyEvent.KEYCODE_DPAD_DOWN) select(Math.min(80, cell + 9));
        else if (code >= KeyEvent.KEYCODE_1 && code <= KeyEvent.KEYCODE_9) {
            int digit = code - KeyEvent.KEYCODE_0;
            if (dispatcher != null) { dispatcher.accept(view -> view.keyDigit(digit)); return true; }
            keyDigit(digit);
        } else if (code == KeyEvent.KEYCODE_DEL || code == KeyEvent.KEYCODE_FORWARD_DEL) {
            if (dispatcher != null) dispatcher.accept(BoardView::keyClear); else keyClear();
        } else return super.onKeyDown(code, event);
        return true;
    }
    private void keyDigit(int digit) {
            clearEntryFeedback();
            int target = game.selected;
            int previousHouses = game.completedHouses(target);
            if (conflictingEntry(game.selected, digit)) reject(game.selected);
            if (game.enter(digit)) {
                acknowledgeEntry(target, previousHouses);
                if (entryChanged != null) entryChanged.run();
            }
    }
    private void keyClear() {
            clearEntryFeedback();
            if (game.clear() && entryChanged != null) entryChanged.run();
    }

    @Override public boolean dispatchHoverEvent(MotionEvent event) {
        AccessibilityManager manager = getContext().getSystemService(AccessibilityManager.class);
        if (!manager.isTouchExplorationEnabled()) return super.dispatchHoverEvent(event);
        int cell = event.getAction() == MotionEvent.ACTION_HOVER_EXIT ? -1 : cellAt(event.getX(), event.getY());
        if (cell != hovered) {
            if (hovered >= 0) sendCellEvent(hovered, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);
            hovered = cell;
            if (cell >= 0) sendCellEvent(cell, AccessibilityEvent.TYPE_VIEW_HOVER_ENTER);
        }
        return cell >= 0;
    }

    CharSequence description(int cell) {
        if (game == null) return "Empty board";
        StringBuilder text = new StringBuilder("Row ").append(cell / 9 + 1)
                .append(", column ").append(cell % 9 + 1).append(", ");
        int value = game.values[cell];
        if (value == 0) {
            text.append("empty");
            int notes = game.visibleNotes(cell);
            if (notes != 0) {
                text.append(", notes");
                for (int d = 1; d <= 9; d++) if ((notes & Sudoku.bit(d)) != 0) text.append(" ").append(d);
            }
        } else {
            text.append(value);
            if (game.clues[cell] != 0) text.append(", fixed clue");
            if (game.showMistakes && value != game.solution[cell]) text.append(", mistake");
        }
        return text;
    }

    private void sendCellEvent(int cell, int type) {
        if (!getContext().getSystemService(AccessibilityManager.class).isEnabled()) return;
        AccessibilityEvent event = AccessibilityEvent.obtain(type);
        event.setSource(this, cell);
        event.setClassName("android.view.View"); event.setPackageName(getContext().getPackageName());
        event.setContentDescription(description(cell)); event.setEnabled(true);
        getParent().requestSendAccessibilityEvent(this, event);
    }

    @Override public AccessibilityNodeProvider getAccessibilityNodeProvider() {
        return new AccessibilityNodeProvider() {
            @Override public AccessibilityNodeInfo createAccessibilityNodeInfo(int id) {
                if (id == HOST_VIEW_ID) {
                    AccessibilityNodeInfo node = AccessibilityNodeInfo.obtain(BoardView.this);
                    onInitializeAccessibilityNodeInfo(node);
                    node.setClassName("android.widget.GridView");
                    node.setCollectionInfo(AccessibilityNodeInfo.CollectionInfo.obtain(9, 9, false));
                    for (int i = 0; i < 81; i++) node.addChild(BoardView.this, i);
                    return node;
                }
                if (id < 0 || id >= 81) return null;
                AccessibilityNodeInfo node = AccessibilityNodeInfo.obtain();
                node.setSource(BoardView.this, id); node.setParent(BoardView.this);
                node.setClassName("android.widget.Button"); node.setPackageName(getContext().getPackageName());
                node.setContentDescription(description(id));
                node.setEnabled(game != null); node.setVisibleToUser(isShown());
                node.setFocusable(true); node.setClickable(true);
                node.setSelected(game != null && game.selected == id);
                node.setCollectionItemInfo(AccessibilityNodeInfo.CollectionItemInfo.obtain(id / 9, 1, id % 9, 1, false));
                node.setAccessibilityFocused(accessibilityFocus == id);
                node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
                if (game != null && game.armedDigit != 0 && game.clues[id] == 0) {
                    node.setLongClickable(true);
                    node.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                            AccessibilityNodeInfo.ACTION_LONG_CLICK, "Toggle note " + game.armedDigit));
                }
                node.addAction(accessibilityFocus == id
                        ? AccessibilityNodeInfo.AccessibilityAction.ACTION_CLEAR_ACCESSIBILITY_FOCUS
                        : AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS);
                Rect bounds = new Rect(Math.round(left + id % 9 * cellSize), Math.round(top + id / 9 * cellSize),
                        Math.round(left + (id % 9 + 1) * cellSize), Math.round(top + (id / 9 + 1) * cellSize));
                node.setBoundsInParent(bounds);
                android.graphics.Matrix transform = new android.graphics.Matrix();
                transformMatrixToGlobal(transform);
                android.graphics.RectF screenBounds = new android.graphics.RectF(bounds);
                transform.mapRect(screenBounds); screenBounds.roundOut(bounds);
                node.setBoundsInScreen(bounds);
                return node;
            }

            @Override public boolean performAction(int id, int action, Bundle arguments) {
                if (id < 0 || id >= 81) return false;
                if (action == AccessibilityNodeInfo.ACTION_CLICK) { select(id); return true; }
                if (action == AccessibilityNodeInfo.ACTION_LONG_CLICK && game != null && game.armedDigit != 0) {
                    note(id); return true;
                }
                if (action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) {
                    if (accessibilityFocus >= 0) sendCellEvent(accessibilityFocus, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
                    accessibilityFocus = id;
                    sendCellEvent(id, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED); return true;
                }
                if (action == AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS) {
                    accessibilityFocus = -1;
                    sendCellEvent(id, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED); return true;
                }
                return false;
            }
        };
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    private float sp(float value) { return value * getResources().getDisplayMetrics().scaledDensity; }
}
