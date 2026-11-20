package com.quietgrid.sudoku;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.SystemClock;
import java.lang.reflect.Field;

final class NativeSaveAwaiter {
    private NativeSaveAwaiter() {}
    static void await(Instrumentation instrumentation, Activity activity) {
        if (activity == null) return;
        long deadline = SystemClock.uptimeMillis() + 15000;
        boolean[] idle = {false};
        do {
            instrumentation.runOnMainSync(() -> {
                try {
                    Field field = activity.getClass().getDeclaredField("saveSession"); field.setAccessible(true);
                    Object value = field.get(activity);
                    idle[0] = value instanceof SaveSession<?> session && session.idle();
                } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
            });
            if (idle[0]) {
                java.io.File preferences = new java.io.File(instrumentation.getTargetContext().getFilesDir().getParentFile(),
                        "shared_prefs/settings.xml");
                try { SaveQueue.forPath(preferences.getAbsolutePath()).flushBlocking(); }
                catch (java.io.IOException ex) { throw new AssertionError(ex); }
                instrumentation.waitForIdleSync(); return;
            }
            SystemClock.sleep(10);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Native saves did not settle");
    }
}
