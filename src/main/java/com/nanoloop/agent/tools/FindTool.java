package com.nanoloop.agent.tools;

import com.anthropic.core.JsonValue;
import com.nanoloop.agent.util.JsonResult;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Finds files by glob pattern under a given directory.
 *
 * <p>Uses Java's {@link PathMatcher} for glob matching. Returns paths relative
 * to the project root. Caps at 5000 matches.
 *
 * <p>Input: {@code {"pattern": string, "path"?: string}}
 * <br>Output: {@code {"root", "pattern", "matches": [string]}}
 */
public final class FindTool {
    private static final int MAX_MATCHES = 5000;

    private final PathSandbox sandbox;

    public FindTool(PathSandbox sandbox) {
        this.sandbox = sandbox;
    }

    public String execute(JsonValue input) {
        try {
            Map<?, ?> m = input.convert(Map.class);
            String pattern = (String) m.get("pattern");
            if (pattern == null || pattern.isBlank()) return JsonResult.error("validation", "missing pattern");
            String pathStr = m.get("path") != null ? (String) m.get("path") : "";

            Path root = pathStr.isBlank() ? sandbox.projectRoot() : sandbox.resolveInProject(pathStr);
            if (!Files.exists(root)) return JsonResult.error("not_found", "path not found: " + pathStr);
            if (!Files.isDirectory(root)) return JsonResult.error("validation", "not a directory: " + pathStr);

            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);

            List<String> allMatches;
            try (var stream = Files.walk(root)) {
            allMatches = stream
                    .filter(p -> matcher.matches(p.getFileName()))
                    .limit(MAX_MATCHES + 1L)
                    .map(p -> sandbox.projectRoot().relativize(p.toAbsolutePath().normalize()).toString())
                    .toList();
            }

            boolean truncated = allMatches.size() > MAX_MATCHES;
            List<String> matches = truncated ? allMatches.subList(0, MAX_MATCHES) : allMatches;

            return JsonResult.ok(Map.of(
                    "root", pathStr.isBlank() ? "." : pathStr,
                    "pattern", pattern,
                    "matches", matches
            ), truncated);
        } catch (PathSandbox.SandboxViolationException e) {
            return e.jsonError();
        } catch (IOException e) {
            return JsonResult.error("runtime", e.getMessage());
        } catch (Exception e) {
            return JsonResult.error("runtime", String.valueOf(e));
        }
    }
}
