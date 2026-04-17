package com.nanoloop.agent.tools;

import com.anthropic.core.JsonValue;
import com.nanoloop.agent.util.JsonResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Lists directory contents, appending "/" to directory names.
 *
 * <p>Entries are sorted case-insensitively. Caps at 5000 entries.
 *
 * <p>Input: {@code {"path"?: string}} (defaults to project root)
 * <br>Output: {@code {"path", "entries": [string]}}
 */
public final class LsTool {
    private static final int MAX_ENTRIES = 5000;

    private final PathSandbox sandbox;

    public LsTool(PathSandbox sandbox) {
        this.sandbox = sandbox;
    }

    public String execute(JsonValue input) {
        try {
            Map<?, ?> m = input.convert(Map.class);
            String pathStr = m.get("path") != null ? (String) m.get("path") : "";
            Path dir = pathStr.isBlank() ? sandbox.projectRoot() : sandbox.resolveInProject(pathStr);
            if (!Files.exists(dir)) return JsonResult.error("not_found", "path not found: " + pathStr);
            if (!Files.isDirectory(dir)) return JsonResult.error("validation", "not a directory: " + pathStr);

            List<String> entries;
            try (var stream = Files.list(dir)) {
            entries = stream
                    .limit(MAX_ENTRIES + 1L)
                    .map(p -> {
                        String name = p.getFileName().toString();
                        if (Files.isDirectory(p)) return name + "/";
                        return name;
                    })
                    .sorted(Comparator.comparing(String::toLowerCase))
                    .collect(Collectors.toList());
            }

            boolean truncated = entries.size() > MAX_ENTRIES;
            if (truncated) entries = entries.subList(0, MAX_ENTRIES);

            return JsonResult.ok(Map.of(
                    "path", pathStr.isBlank() ? "." : pathStr,
                    "entries", entries
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
