package com.nanoloop.agent.tools;

import com.anthropic.core.JsonValue;
import com.nanoloop.agent.AgentLoop;
import com.nanoloop.agent.TestStubs;
import com.nanoloop.agent.TestStubs.StubClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ReadToolTest {

    @TempDir Path tempDir;
    ReadTool tool;

    @BeforeEach
    void setUp() {
        tool = new ReadTool(new PathSandbox(tempDir));
    }

    @Test
    void readExistingFile() throws Exception {
        Files.writeString(tempDir.resolve("hello.txt"), "line1\nline2\nline3\n");
        String result = tool.execute(JsonValue.from(Map.of("path", "hello.txt")));
        assertTrue(result.contains("\"ok\":true"));
        assertTrue(result.contains("line1"));
        assertTrue(result.contains("line2"));
    }

    @Test
    void readWithOffsetAndLimit() throws Exception {
        Files.writeString(tempDir.resolve("nums.txt"), "a\nb\nc\nd\ne\n");
        String result = tool.execute(JsonValue.from(Map.of("path", "nums.txt", "offset", 2, "limit", 2)));
        assertTrue(result.contains("\"ok\":true"));
        // Lines are "2\tb" and "3\tc" but \t is escaped to \\t in JSON
        assertTrue(result.contains("2\\tb"));
        assertTrue(result.contains("3\\tc"));
        assertFalse(result.contains("4\\td"));
    }

    @Test
    void readNonexistentFile() {
        String result = tool.execute(JsonValue.from(Map.of("path", "nope.txt")));
        assertTrue(result.contains("\"ok\":false"));
        assertTrue(result.contains("not_found"));
    }

    @Test
    void readOutsideSandbox() {
        String result = tool.execute(JsonValue.from(Map.of("path", "../../etc/passwd")));
        assertTrue(result.contains("\"ok\":false"));
        assertTrue(result.contains("permission"));
    }

    @Test
    void agentInvokesRead() throws Exception {
        Files.writeString(tempDir.resolve("hello.txt"), "line1\nline2\nline3\n");

        List<String> events = new ArrayList<>();
        StubClient client = new StubClient(List.of(
                TestStubs.toolUseMsg("toolu_1", "read", """
                    {"path":"hello.txt"}
                    """),
                TestStubs.textMsg("The file contains 3 lines.")
        ));

        AgentLoop agent = TestStubs.agentWith(client, events, tempDir);
        agent.prompt("Show me the contents of hello.txt");

        assertTrue(events.contains("tool_execution_start: read"));
        assertTrue(events.stream()
                        .filter(e -> e.startsWith("tool_execution_end:"))
                        .anyMatch(e -> e.contains("line1") && e.contains("line2")),
                "read tool result should contain file content");
    }
}
