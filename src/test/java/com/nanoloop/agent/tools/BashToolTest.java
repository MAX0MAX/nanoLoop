package com.nanoloop.agent.tools;

import com.anthropic.core.JsonValue;
import com.nanoloop.agent.AgentLoop;
import com.nanoloop.agent.TestStubs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BashToolTest {

    @TempDir Path tempDir;
    BashTool tool;

    @BeforeEach
    void setUp() {
        tool = new BashTool(new PathSandbox(tempDir));
    }

    @Test
    void allowedCommandEcho() {
        String result = tool.execute(JsonValue.from(Map.of("command", "echo hello")));
        assertTrue(result.contains("\"ok\":true"));
        assertTrue(result.contains("hello"));
    }

    @Test
    void deniedCommandRm() {
        String result = tool.execute(JsonValue.from(Map.of("command", "rm -rf /")));
        assertTrue(result.contains("\"ok\":false"));
        assertTrue(result.contains("permission"));
    }

    @Test
    void deniedTokenPipe() {
        String result = tool.execute(JsonValue.from(Map.of("command", "echo foo | cat")));
        assertTrue(result.contains("\"ok\":false"));
        assertTrue(result.contains("permission"));
    }

    @Test
    void deniedTokenSemicolon() {
        String result = tool.execute(JsonValue.from(Map.of("command", "echo a ; echo b")));
        assertTrue(result.contains("\"ok\":false"));
        assertTrue(result.contains("permission"));
    }

    @Test
    void unknownCommandPrefix() {
        String result = tool.execute(JsonValue.from(Map.of("command", "python3 -c 'print(1)'")));
        assertTrue(result.contains("\"ok\":false"));
        assertTrue(result.contains("permission"));
    }

    @Test
    void agentInvokesBash() {
        List<String> events = new ArrayList<>();
        AgentLoop agent = new AgentLoop(TestStubs.SYSTEM_PROMPT,
                e -> { events.add(e); System.out.println("[EVENT] " + e); }, tempDir);
        agent.prompt("Run 'echo hello world' to test the shell");

        assertTrue(events.contains("tool_execution_start: bash"),
                "agent should have invoked the bash tool");
        assertTrue(events.stream()
                        .filter(e -> e.startsWith("tool_execution_end:"))
                        .anyMatch(e -> e.contains("hello world")),
                "bash tool result should contain 'hello world'");
    }
}
