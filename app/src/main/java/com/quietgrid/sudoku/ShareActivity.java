package com.quietgrid.sudoku;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import com.quietgrid.sudoku.puzzles.PuzzleShare;

/** Routes links for this build; puzzle validation belongs to each background loader. */
public final class ShareActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (!PuzzleSharing.shared(getIntent())) { finish(); return; }
        try {
            String link = PuzzleSharing.incoming(getIntent());
            String kind = LocalShareLink.kind(link);
            Intent open = new Intent(Intent.ACTION_VIEW, getIntent().getData());
            if ("sudoku".equals(kind)) open.setClass(this, MainActivity.class);
            else if (WordShare.KIND.equals(kind)) open.setClass(this, DailyWordActivity.class);
            else open.setClass(this, PuzzleActivity.class).putExtra(PuzzleActivity.MODE, PuzzleShare.mode(link).name());
            startActivity(open);
            finish();
        } catch (IllegalArgumentException ex) {
            android.util.Log.e("PuzzleSharing", "Unsupported shared link", ex);
            new AlertDialog.Builder(this).setTitle(R.string.shared_puzzle_error)
                    .setMessage(ex.getMessage()).setPositiveButton("Back", (dialog, which) -> finish())
                    .setOnCancelListener(dialog -> finish()).show();
        }
    }
}
