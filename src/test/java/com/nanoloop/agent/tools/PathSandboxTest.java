package com.nanoloop.agent.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PathSandboxTest {

    @TempDir
    Path tempDir;

    @Test
    void resolveValidRelativePath() {
        PathSandbox sandbox = new PathSandbox(tempDir);
        Path resolved = sandbox.resolveInProject("src/main");
        assertTrue(resolved.startsWith(tempDir));
    }

    @Test
    void rejectTraversalAboveRoot() {
        PathSandbox sandbox = new PathSandbox(tempDir);
        assertThrows(PathSandbox.SandboxViolationException.class,
                () -> sandbox.resolveInProject("../../etc/passwd"));
    }

    @Test
    void rejectAbsolutePathOutsideRoot() {
        PathSandbox sandbox = new PathSandbox(tempDir);
        assertThrows(PathSandbox.SandboxViolationException.class,
                () -> sandbox.resolveInProject("/etc/passwd"));
    }
}
