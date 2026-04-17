package com.nanoloop.agent.tools;

import com.anthropic.core.JsonValue;
import com.nanoloop.agent.util.JsonResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Creates or overwrites a file with the given content.
 *
 * <p>Automatically creates parent directories if they don't exist.
 *
 * <p>Input: {@code {"path": string, "content": string}}
 * <br>Output: {@code {"path", "bytes"}}
 */
public final class WriteTool {

    private final PathSandbox sandbox;

    public WriteTool(PathSandbox sandbox) {
        this.sandbox = sandbox;
    }

    public String execute(JsonValue input) {
        try {
            Map<?, ?> m = input.convert(Map.class);
            String pathStr = (String) m.get("path");
            if (pathStr == null || pathStr.isBlank()) return JsonResult.error("validation", "missing path");
            Object contentObj = m.get("content");
            String content = contentObj == null ? "" : contentObj.toString();

            Path path = sandbox.resolveInProject(pathStr);
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);

            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            Files.write(path, bytes);

            return JsonResult.ok(Map.of(
                    "path", pathStr,
                    "bytes", bytes.length
            ));
        } catch (PathSandbox.SandboxViolationException e) {
            return e.jsonError();
        } catch (IOException e) {
            return JsonResult.error("runtime", e.getMessage());
        } catch (Exception e) {
            return JsonResult.error("runtime", String.valueOf(e));
        }
    }
}
