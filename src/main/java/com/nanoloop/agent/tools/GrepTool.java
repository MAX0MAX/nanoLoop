package com.nanoloop.agent.tools;

import com.anthropic.core.JsonValue;
import com.nanoloop.agent.util.JsonResult;
import com.nanoloop.agent.util.InputParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Searches file contents for a text pattern via line-by-line substring matching.
 *
 * <p>Walks the directory tree from the given root, skipping binary files and files
 * larger than 256 KB. Caps at 2000 files scanned and 2000 matches returned.
 *
 * <p>Input: {@code {"pattern": string, "path"?: string, "ignoreCase"?: boolean}}
 * <br>Output: {@code {"pattern", "root", "filesScanned", "matches": [{"path","line","text"}]}}
 */
public final class GrepTool {
    private static final int MAX_FILES = 2000;
    private static final int MAX_MATCHES = 2000;
    private static final long MAX_FILE_BYTES = 256 * 1024;

    private final PathSandbox sandbox;

    public GrepTool(PathSandbox sandbox) {
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

            boolean ignoreCase = InputParser.boolOrDefault(m.get("ignoreCase"), false);
            String needle = ignoreCase ? pattern.toLowerCase() : pattern;

            List<Map<String, Object>> matches = new ArrayList<>();
            int[] filesScanned = {0};

            try (var stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .forEach(p -> {
                        if (filesScanned[0] >= MAX_FILES) return;
                        if (matches.size() >= MAX_MATCHES) return;
                        filesScanned[0]++;

                        try {
                            if (Files.size(p) > MAX_FILE_BYTES) return;
                            byte[] bytes = Files.readAllBytes(p);
                            int sniff = Math.min(bytes.length, 4096);
                            for (int i = 0; i < sniff; i++) {
                                if (bytes[i] == 0) return;
                            }

                            String content = new String(bytes, StandardCharsets.UTF_8);
                            String[] lines = content.split("\\R", -1);
                            for (int i = 0; i < lines.length && matches.size() < MAX_MATCHES; i++) {
                                String hay = ignoreCase ? lines[i].toLowerCase() : lines[i];
                                if (hay.contains(needle)) {
                                    Path rel = sandbox.projectRoot().relativize(p.toAbsolutePath().normalize());
                                    matches.add(Map.of(
                                            "path", rel.toString(),
                                            "line", i + 1,
                                            "text", lines[i]
                                    ));
                                }
                            }
                        } catch (IOException ignored) {
                        }
                    });
            }

            boolean truncated = filesScanned[0] >= MAX_FILES || matches.size() >= MAX_MATCHES;
            return JsonResult.ok(Map.of(
                    "pattern", pattern,
                    "root", pathStr.isBlank() ? "." : pathStr,
                    "filesScanned", filesScanned[0],
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
