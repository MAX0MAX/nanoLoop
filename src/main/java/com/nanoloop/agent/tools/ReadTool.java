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
 * Reads a file and returns numbered lines.
 *
 * <p>Supports optional line offset and limit. Rejects binary files (detected via
 * null-byte sniffing) and files larger than 256 KB.
 *
 * <p>Input: {@code {"path": string, "offset"?: int, "limit"?: int}}
 * <br>Output: {@code {"path", "offset", "limit", "content"}}
 */
public final class ReadTool {

    private static final int DEFAULT_LIMIT_LINES = 200;
    private static final int MAX_LIMIT_LINES = 2000;
    private static final long MAX_BYTES = 256 * 1024;

    private final PathSandbox sandbox;

    public ReadTool(PathSandbox sandbox) {
        this.sandbox = sandbox;
    }

    public String execute(JsonValue input) {
        try {
            Map<?, ?> m = input.convert(Map.class);
            String pathStr = (String) m.get("path");
            if (pathStr == null || pathStr.isBlank()) return JsonResult.error("validation", "missing path");

            int offset = InputParser.intOrDefault(m.get("offset"), 1);
            int limit = InputParser.intOrDefault(m.get("limit"), DEFAULT_LIMIT_LINES);
            if (offset < 1) offset = 1;
            if (limit < 1) limit = 1;
            if (limit > MAX_LIMIT_LINES) limit = MAX_LIMIT_LINES;

            Path path = sandbox.resolveInProject(pathStr);
            if (!Files.exists(path)) return JsonResult.error("not_found", "file not found: " + pathStr);
            if (Files.size(path) > MAX_BYTES) return JsonResult.error("runtime", "file too large (>256KB): " + pathStr);

            byte[] bytes = Files.readAllBytes(path);
            int sniff = Math.min(bytes.length, 4096);
            for (int i = 0; i < sniff; i++) {
                if (bytes[i] == 0) return JsonResult.error("runtime", "binary file not supported: " + pathStr);
            }

            String content = new String(bytes, StandardCharsets.UTF_8);
            String[] lines = content.split("\\R", -1);

            int startIdx = offset - 1;
            if (startIdx > lines.length) startIdx = lines.length;
            int endIdx = Math.min(lines.length, startIdx + limit);

            List<String> outLines = new ArrayList<>(endIdx - startIdx);
            for (int i = startIdx; i < endIdx; i++) {
                int lineNo = i + 1;
                outLines.add(lineNo + "\t" + lines[i]);
            }

            boolean truncated = endIdx < lines.length;
            Map<String, Object> resultMap = Map.of(
                    "path", pathStr,
                    "offset", offset,
                    "limit", limit,
                    "content", String.join("\n", outLines)
            );
            return JsonResult.ok(resultMap, truncated);
        } catch (PathSandbox.SandboxViolationException e) {
            return e.jsonError();
        } catch (IOException e) {
            return JsonResult.error("runtime", e.getMessage());
        } catch (Exception e) {
            return JsonResult.error("runtime", String.valueOf(e));
        }
    }
}
