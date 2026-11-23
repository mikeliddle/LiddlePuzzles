package com.quietgrid.sudoku;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

final class ResponsibleMode {
    static final String LIMIT_LABEL = "Daily limit reached";
    static final String LIMIT_MESSAGE = "Daily limit reached. Play again after local midnight.";
    static final long OFF = Long.MIN_VALUE;
    private static final String KEY = "responsibleModeSince";
    private static volatile Long savingFrom;

    private ResponsibleMode() {}

    static LocalDate today() { return DailyWord.today(Clock.systemUTC(), ZoneId.systemDefault()); }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    static long since(Context context) {
        Long pending = savingFrom;
        return pending != null ? pending : preferences(context).getLong(KEY, OFF);
    }

    static boolean enabled(Context context) { return since(context) != OFF; }
    static boolean limited(Context context, PuzzleSave puzzle) { return puzzle.limited(enabled(context), today()); }
    static boolean notifyIfLimited(android.app.Activity activity, PuzzleSave puzzle) {
        if (!limited(activity, puzzle)) return false;
        new android.app.AlertDialog.Builder(activity).setTitle(LIMIT_LABEL)
                .setMessage("Play again after local midnight. You can still view your saved puzzle.")
                .setPositiveButton("OK", null).show();
        return true;
    }
    static boolean locked(long since, LocalDate today) { return since != OFF && since >= today.toEpochDay(); }
    static boolean saving() { return savingFrom != null; }

    static void set(Context context, boolean enabled, LocalDate today) throws IOException {
        SharedPreferences prefs = preferences(context);
        long previous = prefs.getLong(KEY, OFF);
        if (enabled == (previous != OFF) || !enabled && locked(previous, today)) return;
        savingFrom = previous;
        try {
            SharedPreferences.Editor change = prefs.edit();
            if (!(enabled ? change.putLong(KEY, today.toEpochDay()) : change.remove(KEY)).commit()) {
                SharedPreferences.Editor rollback = prefs.edit();
                if (!(previous == OFF ? rollback.remove(KEY) : rollback.putLong(KEY, previous)).commit())
                    android.util.Log.e("ResponsibleMode", "Could not restore the previous setting on disk");
                throw new IOException("Could not save Responsible mode");
            }
        } finally {
            savingFrom = null;
        }
    }
}
