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

class GrepToolTest {

    @TempDir Path tempDir;
    GrepTool tool;

    @BeforeEach
    void setUp() throws Exception {
        tool = new GrepTool(new PathSandbox(tempDir));
        Files.writeString(tempDir.resolve("code.java"), "public class Foo {\n    int bar = 42;\n}\n");
        Files.writeString(tempDir.resolve("notes.txt"), "Foo is great\nbar is not\n");
    }

    @Test
    void grepFindsMatches() {
        String result = tool.execute(JsonValue.from(Map.of("pattern", "bar")));
        assertTrue(result.contains("\"ok\":true"));
        assertTrue(result.contains("bar"));
    }

    @Test
    void grepNoMatch() {
        String result = tool.execute(JsonValue.from(Map.of("pattern", "zzzzz_no_match")));
        assertTrue(result.contains("\"ok\":true"));
        assertTrue(result.contains("\"matches\":[]"));
    }

    @Test
    void grepIgnoreCase() {
        String result = tool.execute(JsonValue.from(Map.of("pattern", "FOO", "ignoreCase", true)));
        assertTrue(result.contains("\"ok\":true"));
        assertTrue(result.contains("Foo"));
    }

    @Test
    void agentInvokesGrep() throws Exception {
        List<String> events = new ArrayList<>();
        StubClient client = new StubClient(List.of(
                TestStubs.toolUseMsg("toolu_1", "grep", """
                    {"pattern":"bar"}
                    """),
                TestStubs.textMsg("Found 'bar' in 2 files.")
        ));

        AgentLoop agent = TestStubs.agentWith(client, events, tempDir);
        agent.prompt("Search for 'bar' in the project");

        assertTrue(events.contains("tool_execution_start: grep"));
        assertTrue(events.stream()
                        .filter(e -> e.startsWith("tool_execution_end:"))
                        .anyMatch(e -> e.contains("bar")),
                "grep tool result should contain matches for 'bar'");
    }
}
