package com.quietgrid.sudoku;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;

final class AndroidSaveFile {
    private AndroidSaveFile() {}
    static DurableFile at(File file) {
        return new DurableFile(file, operations());
    }
    static DurableFile.Operations operations() {
        return new DurableFile.FileOperations() {
            @Override public void syncDirectory(File directory) throws IOException {
                try {
                    FileDescriptor descriptor = Os.open(directory.getPath(),
                            OsConstants.O_RDONLY, 0);
                    try { Os.fsync(descriptor); }
                    finally { Os.close(descriptor); }
                } catch (ErrnoException ex) {
                    throw new IOException("Could not commit save directory", ex);
                }
            }
        };
    }
    static byte[] read(File file) throws IOException {
        return read(file, Integer.MAX_VALUE);
    }
    static byte[] read(File file, int limit) throws IOException {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
            throw new IllegalStateException("Read saved progress on a worker, not the UI thread");
        return PuzzleSave.forFile(file).read(at(file), limit);
    }
}
