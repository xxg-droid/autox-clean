package org.autojs.autojs.ui.edit;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Atomic-ish file save for the script editor (build27 item 2).
 *
 * <p>Old behaviour moved the original to {@code .b_a_k} FIRST and then wrote the
 * new content — a failed write lost the script (the .js was gone, the content
 * stranded in {@code .b_a_k}). New order:
 *
 * <ol>
 *   <li>write the new content to {@code path + ".tmp"} in the same directory;</li>
 *   <li>verify the temp file exists and its byte length matches the text;</li>
 *   <li>move the original to {@code .b_a_k};</li>
 *   <li>move the temp file to the original path;</li>
 *   <li>delete {@code .b_a_k}.</li>
 * </ol>
 *
 * <p>On ANY failure the backup is automatically restored to the original path
 * (if it was moved and the original is now missing) and the leftover temp file
 * is deleted, then the error is rethrown so the caller can surface it (the
 * editor toasts the message). The file on disk is never silently lost.
 *
 * <p>Pure JVM, no Android dependencies, so it is unit-testable
 * (see {@code AtomicFileSaverTest}).
 */
public final class AtomicFileSaver {

    private AtomicFileSaver() {
    }

    /**
     * Saves {@code text} to {@code original} using the temp-write/verify/swap
     * procedure above. Creates {@code original} if it does not exist yet
     * (new file being saved for the first time).
     *
     * @throws IOException if any step fails; in that case the original file
     *                     content is preserved (see class doc).
     */
    public static void save(File original, String text) throws IOException {
        String path = original.getPath();
        File tmp = new File(path + ".tmp");
        File backup = new File(path + ".b_a_k");
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        boolean movedToBackup = false;
        try {
            // 1. Write the new content to the temp file first.
            Files.write(tmp.toPath(), bytes);
            // 2. Verify: temp file exists and byte length matches the text.
            if (!tmp.isFile() || tmp.length() != bytes.length) {
                throw new IOException("Failed to verify temp file: " + tmp.getPath());
            }
            // 3. Move the original aside (only if it exists — new files have none).
            if (original.exists()) {
                if (!original.renameTo(backup)) {
                    throw new IOException("Failed to back up file: " + path);
                }
                movedToBackup = true;
            }
            // 4. Move the temp file into place.
            if (!tmp.renameTo(original)) {
                throw new IOException("Failed to replace file: " + path);
            }
            // 5. Drop the backup.
            backup.delete();
        } catch (IOException | RuntimeException e) {
            // Restore on ANY failure: only when we moved the original away
            // and it is now missing, and the backup is still there.
            if (movedToBackup && !original.exists() && backup.exists()) {
                backup.renameTo(original);
            }
            if (tmp.exists()) {
                tmp.delete();
            }
            throw e;
        }
    }
}
