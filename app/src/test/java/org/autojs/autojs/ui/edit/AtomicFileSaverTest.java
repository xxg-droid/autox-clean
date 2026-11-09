package org.autojs.autojs.ui.edit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Unit tests for {@link AtomicFileSaver} (build27 item 2, editor save-loss fix).
 *
 * <p>Pure JVM, no Android dependencies. Covers: saving a new file, overwriting
 * an existing file (no .tmp / .b_a_k leftovers), and the failure path — a write
 * that cannot even start must leave the original file untouched.
 */
public class AtomicFileSaverTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static String read(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void saveCreatesNewFileWithExactContent() throws Exception {
        File target = new File(folder.getRoot(), "new-script.js");
        AtomicFileSaver.save(target, "console.log('hi');\n");
        assertTrue(target.isFile());
        assertEquals("console.log('hi');\n", read(target));
        assertFalse(new File(target.getPath() + ".tmp").exists());
        assertFalse(new File(target.getPath() + ".b_a_k").exists());
    }

    @Test
    public void saveOverwritesExistingFileAndLeavesNoLeftovers() throws Exception {
        File target = new File(folder.getRoot(), "script.js");
        Files.write(target.toPath(), "old content".getBytes(StandardCharsets.UTF_8));
        AtomicFileSaver.save(target, "new content — 新内容");
        assertEquals("new content — 新内容", read(target));
        assertFalse(new File(target.getPath() + ".tmp").exists());
        assertFalse(new File(target.getPath() + ".b_a_k").exists());
    }

    @Test
    public void saveEmptyTextProducesEmptyFile() throws Exception {
        File target = new File(folder.getRoot(), "empty.js");
        AtomicFileSaver.save(target, "");
        assertTrue(target.isFile());
        assertEquals(0, target.length());
    }

    @Test
    public void failedWriteLeavesOriginalUntouched() throws Exception {
        File target = new File(folder.getRoot(), "script.js");
        Files.write(target.toPath(), "original — must survive".getBytes(StandardCharsets.UTF_8));
        // Block the temp file: pre-create "<path>.tmp" as a directory, so the
        // temp write fails before anything is moved. The original must survive
        // byte-for-byte and no stray .b_a_k may remain.
        File tmpDir = new File(target.getPath() + ".tmp");
        assertTrue(tmpDir.mkdir());
        try {
            AtomicFileSaver.save(target, "new content");
            fail("expected IOException");
        } catch (IOException expected) {
            // expected: temp write cannot start
        }
        assertEquals("original — must survive", read(target));
        assertFalse(new File(target.getPath() + ".b_a_k").exists());
    }

    @Test
    public void failedWriteToMissingDirectoryThrowsAndCreatesNothing() throws Exception {
        File target = new File(new File(folder.getRoot(), "no-such-dir"), "script.js");
        try {
            AtomicFileSaver.save(target, "content");
            fail("expected IOException");
        } catch (IOException expected) {
            // expected: parent directory does not exist
        }
        assertFalse(target.exists());
        assertFalse(new File(target.getPath() + ".tmp").exists());
        assertFalse(new File(target.getPath() + ".b_a_k").exists());
    }
}
