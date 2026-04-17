package com.nanoloop.agent.tools;

import com.anthropic.core.JsonValue;
import com.nanoloop.agent.util.JsonResult;
import com.nanoloop.agent.util.InputParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Executes shell commands in a restricted sandbox.
 *
 * <p>Only commands starting with an allowlisted prefix ({@code mvn, git, ls, pwd,
 * java, javac, echo, cat}) are permitted. Shell operators ({@code |, ;, &&, >, >>})
 * are rejected to prevent command chaining. Commands run with cwd set to the project root.
 *
 * <p>Input: {@code {"command": string, "timeout_ms"?: long}}
 * <br>Output: {@code {"command", "exitCode", "stdout", "stderr"}}
 */
public final class BashTool {

    private static final Set<String> ALLOWED_PREFIXES = Set.of(
            "mvn",
            "git",
            "ls",
            "pwd",
            "java",
            "javac",
            "echo",
            "cat"
    );

    private static final List<String> DENY_TOKENS = List.of(
            ">>",
            ">",
            "|",
            ";",
            "&&",
            "||"
    );

    private static final int MAX_OUTPUT_CHARS = 20_000;

    private final PathSandbox sandbox;

    public BashTool(PathSandbox sandbox) {
        this.sandbox = sandbox;
    }

    public String execute(JsonValue input) {
        try {
            Map<?, ?> m = input.convert(Map.class);
            String command = (String) m.get("command");
            if (command == null || command.isBlank()) return JsonResult.error("validation", "missing command");

            for (String tok : DENY_TOKENS) {
                if (command.contains(tok)) {
                    return JsonResult.error("permission", "denied token in command: " + tok);
                }
            }

            String trimmed = command.trim();
            String firstWord = trimmed.split("\\s+", 2)[0];
            if (!ALLOWED_PREFIXES.contains(firstWord)) {
                return JsonResult.error("permission", "command not allowed: " + firstWord);
            }

            long timeoutMs = InputParser.longOrDefault(m.get("timeout_ms"), 120_000L);
            if (timeoutMs < 1) timeoutMs = 1;

            Path cwd = sandbox.projectRoot();
            ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-lc", command);
            pb.directory(cwd.toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();

            String stdout = readAll(p.getInputStream());

            boolean finished;
            try {
                finished = p.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return JsonResult.error("runtime", "interrupted");
            }

            if (!finished) {
                p.destroyForcibly();
                return JsonResult.error("runtime", "command timed out after " + Duration.ofMillis(timeoutMs));
            }

            int exit = p.exitValue();

            boolean truncated = stdout.length() > MAX_OUTPUT_CHARS;
            if (stdout.length() > MAX_OUTPUT_CHARS) stdout = stdout.substring(0, MAX_OUTPUT_CHARS);

            return JsonResult.ok(Map.of(
                    "command", command,
                    "exitCode", exit,
                    "stdout", stdout,
                    "stderr", ""
            ), truncated);
        } catch (Exception e) {
            return JsonResult.error("runtime", String.valueOf(e));
        }
    }

    private static String readAll(java.io.InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            char[] buf = new char[4096];
            int n;
            while ((n = br.read(buf)) != -1) {
                sb.append(buf, 0, n);
                if (sb.length() > MAX_OUTPUT_CHARS + 4096) break;
            }
        }
        return sb.toString();
    }
}
