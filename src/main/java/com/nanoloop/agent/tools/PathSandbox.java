package com.nanoloop.agent.tools;

import com.nanoloop.agent.util.JsonResult;
import java.nio.file.Path;

/**
 * Enforces a project-root boundary on all file paths.
 * Resolves user-supplied paths relative to the project root and rejects any
 * path that escapes the boundary (e.g., via "../" traversal or absolute paths
 * outside the project).
 *
 * <p>Every file-based tool (read, write, edit, ls, find, grep) uses this
 * sandbox to confine its operations to the project directory.
 */
public final class PathSandbox {
    private final Path projectRoot;

    public PathSandbox(Path projectRoot) {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
    }

    public Path projectRoot() {
        return projectRoot;
    }

    public Path resolveInProject(String userPath) {
        Path resolved = projectRoot.resolve(userPath).normalize().toAbsolutePath();
        if (!resolved.startsWith(projectRoot)) {
            throw new SandboxViolationException(JsonResult.error("permission", "path outside project: " + userPath));
        }
        return resolved;
    }

    public static final class SandboxViolationException extends RuntimeException {
        private final String jsonError;

        public SandboxViolationException(String jsonError) {
            super(jsonError);
            this.jsonError = jsonError;
        }

        public String jsonError() {
            return jsonError;
        }
    }
}
