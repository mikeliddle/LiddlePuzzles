package com.quietgrid.sudoku;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Observable commit and legacy .bak recovery; all access is serialized by the caller. */
final class DurableFile {
    interface Operations {
        void write(File file, byte[] bytes) throws IOException;
        void copy(File source, File target) throws IOException;
        void replace(File source, File target) throws IOException;
        void delete(File file) throws IOException;
        void syncDirectory(File directory) throws IOException;
    }

    abstract static class FileOperations implements Operations {
        @Override public void write(File file, byte[] bytes) throws IOException {
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(bytes); out.flush(); out.getFD().sync();
            }
        }
        @Override public void replace(File source, File target) throws IOException {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        }
        @Override public void copy(File source, File target) throws IOException {
            try (FileInputStream in = new FileInputStream(source); FileOutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                out.flush(); out.getFD().sync();
            }
        }
        @Override public void delete(File file) throws IOException { Files.deleteIfExists(file.toPath()); }
    }

    private final File base, backup, temporary;
    private final Operations operations;

    DurableFile(File base, Operations operations) {
        this.base = base; this.operations = operations;
        backup = new File(base + ".bak"); temporary = new File(base + ".new");
    }

    byte[] read() throws IOException {
        return read(Integer.MAX_VALUE);
    }
    byte[] read(int limit) throws IOException {
        recover();
        if (!base.exists()) return null;
        if (Files.size(base.toPath()) > limit) throw new IOException("Puzzle save is too large.");
        return Files.readAllBytes(base.toPath());
    }
    private void recover() throws IOException {
        if (backup.exists()) {
            operations.replace(backup, base);
            operations.syncDirectory(base.getParentFile());
        }
    }

    void commit(byte[] bytes) throws IOException {
        if (!base.getParentFile().exists()) {
            Files.createDirectories(base.getParentFile().toPath());
            operations.syncDirectory(base.getParentFile().getParentFile());
        }
        recover();
        boolean previous = base.exists();
        boolean replacing = false, retiringBackup = false;
        try {
            if (previous) {
                operations.copy(base, temporary);
                operations.replace(temporary, backup);
                operations.syncDirectory(base.getParentFile());
            }
            operations.write(temporary, bytes);
            replacing = true;
            operations.replace(temporary, base);
            operations.syncDirectory(base.getParentFile());
            if (previous) {
                // Keep synced old bytes until the final directory sync can be acknowledged.
                retiringBackup = true;
                operations.replace(backup, temporary);
            } else operations.delete(backup);
            operations.syncDirectory(base.getParentFile());
        } catch (IOException failure) {
            try {
                if (replacing) {
                    if (previous) {
                        if (!backup.exists()) {
                            if (!retiringBackup || !temporary.exists()) throw new IOException("Recovery backup is missing");
                            operations.replace(temporary, backup);
                            operations.syncDirectory(base.getParentFile());
                        }
                        operations.replace(backup, base);
                    } else operations.delete(base);
                    operations.syncDirectory(base.getParentFile());
                } else operations.delete(backup);
                operations.delete(temporary);
            } catch (IOException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
    }
}
