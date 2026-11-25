package com.quietgrid.sudoku;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;

final class PuzzleSharing {
    private PuzzleSharing() {}

    static boolean shared(Intent intent) {
        return BuildConfig.LOCAL_PUZZLE_SHARING && Intent.ACTION_VIEW.equals(intent.getAction());
    }

    static String incoming(Intent intent) {
        return LocalShareLink.fromTransport(intent.getDataString(), BuildConfig.PUZZLE_LINK_SCHEME);
    }

    static void send(Activity activity, String title, String link, long solveMillis) {
        if (!BuildConfig.LOCAL_PUZZLE_SHARING) return;
        String transport = LocalShareLink.toTransport(link, BuildConfig.PUZZLE_LINK_SCHEME);
        String message = solveMillis > 0
                ? activity.getString(R.string.share_solved_puzzle_message, ActivePlayTimer.label(solveMillis), transport)
                : activity.getString(R.string.share_puzzle_message, title, transport);
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, message);
        try { activity.startActivity(Intent.createChooser(send, activity.getString(R.string.share_puzzle_local))); }
        catch (ActivityNotFoundException ex) {
            android.util.Log.e("PuzzleSharing", "No text sharing activity", ex);
            new AlertDialog.Builder(activity).setTitle(R.string.share_sudoku_error)
                    .setMessage(R.string.share_sudoku_no_app).setPositiveButton("OK", null).show();
        }
    }

    static void openFromText(Activity activity, String text) {
        String link = LocalShareLink.manualTransport(text, BuildConfig.PUZZLE_LINK_SCHEME);
        activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link), activity, ShareActivity.class));
    }

    static void warm(Activity activity, Intent intent) {
        if (!shared(intent)) return;
        activity.startActivity(new Intent(intent).setClass(activity, activity.getClass()).setFlags(0));
        activity.finish();
    }
}
