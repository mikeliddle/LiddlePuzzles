package com.quietgrid.sudoku;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;

final class PuzzleSharing {
    private PuzzleSharing() {}

    static boolean shared(Intent intent) {
        return BuildConfig.LOCAL_PUZZLE_SHARING && Intent.ACTION_VIEW.equals(intent.getAction());
    }

    static void send(Activity activity, String title, String link) {
        if (!BuildConfig.LOCAL_PUZZLE_SHARING) return;
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, activity.getString(R.string.share_puzzle_message, title, link));
        try { activity.startActivity(Intent.createChooser(send, activity.getString(R.string.share_puzzle_local))); }
        catch (ActivityNotFoundException ex) {
            android.util.Log.e("PuzzleSharing", "No text sharing activity", ex);
            new AlertDialog.Builder(activity).setTitle(R.string.share_sudoku_error)
                    .setMessage(R.string.share_sudoku_no_app).setPositiveButton("OK", null).show();
        }
    }

    static void warm(Activity activity, Intent intent) {
        if (!shared(intent)) return;
        activity.startActivity(new Intent(intent).setClass(activity, activity.getClass()).setFlags(0));
        activity.finish();
    }
}
