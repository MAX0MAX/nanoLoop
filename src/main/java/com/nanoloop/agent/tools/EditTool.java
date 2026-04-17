package com.nanoloop.agent.tools;

import com.anthropic.core.JsonValue;
import com.nanoloop.agent.util.JsonResult;
import com.nanoloop.agent.util.InputParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Search-and-replace within a file using exact string matching.
 *
 * <p>By default requires exactly one occurrence of {@code old_string}; set
 * {@code replace_all=true} to replace all occurrences. The model should call
 * {@code read} first to see the file content before editing.
 *
 * <p>Input: {@code {"path": string, "old_string": string, "new_string": string, "replace_all"?: boolean}}
 * <br>Output: {@code {"path", "replacements"}}
 */
public final class EditTool {

    private final PathSandbox sandbox;

    public EditTool(PathSandbox sandbox) {
        this.sandbox = sandbox;
    }

    public String execute(JsonValue input) {
        try {
            Map<?, ?> m = input.convert(Map.class);
            String pathStr = (String) m.get("path");
            if (pathStr == null || pathStr.isBlank()) return JsonResult.error("validation", "missing path");
            String oldString = InputParser.asString(m.get("old_string"));
            String newString = InputParser.asString(m.get("new_string"));
            boolean replaceAll = InputParser.boolOrDefault(m.get("replace_all"), false);
            if (oldString == null) return JsonResult.error("validation", "missing old_string");
            if (newString == null) newString = "";

            Path path = sandbox.resolveInProject(pathStr);
            if (!Files.exists(path)) return JsonResult.error("not_found", "file not found: " + pathStr);

            String content = Files.readString(path, StandardCharsets.UTF_8);

            int count = countOccurrences(content, oldString);
            if (!replaceAll && count != 1) {
                return JsonResult.error("validation", "expected exactly 1 occurrence, found " + count);
            }

            String updated = replaceAll ? content.replace(oldString, newString) : content.replaceFirst(java.util.regex.Pattern.quote(oldString), java.util.regex.Matcher.quoteReplacement(newString));
            Files.writeString(path, updated, StandardCharsets.UTF_8);

            int replacements = replaceAll ? count : (count == 1 ? 1 : 0);
            return JsonResult.ok(Map.of(
                    "path", pathStr,
                    "replacements", replacements
            ));
        } catch (PathSandbox.SandboxViolationException e) {
            return e.jsonError();
        } catch (IOException e) {
            return JsonResult.error("runtime", e.getMessage());
        } catch (Exception e) {
            return JsonResult.error("runtime", String.valueOf(e));
        }
    }

    private static int countOccurrences(String s, String sub) {
        if (sub.isEmpty()) return 0;
        int count = 0;
        int idx = 0;
        while ((idx = s.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
