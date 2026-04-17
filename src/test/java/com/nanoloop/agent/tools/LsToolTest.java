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

class LsToolTest {

    @TempDir Path tempDir;
    LsTool tool;

    @BeforeEach
    void setUp() throws Exception {
        tool = new LsTool(new PathSandbox(tempDir));
        Files.writeString(tempDir.resolve("a.txt"), "hi");
        Files.createDirectory(tempDir.resolve("subdir"));
    }

    @Test
    void listRoot() {
        String result = tool.execute(JsonValue.from(Map.of()));
        assertTrue(result.contains("\"ok\":true"));
        assertTrue(result.contains("a.txt"));
        assertTrue(result.contains("subdir/"));
    }

    @Test
    void listNonexistent() {
        String result = tool.execute(JsonValue.from(Map.of("path", "nope")));
        assertTrue(result.contains("\"ok\":false"));
        assertTrue(result.contains("not_found"));
    }

    @Test
    void listOutsideSandbox() {
        String result = tool.execute(JsonValue.from(Map.of("path", "../../..")));
        assertTrue(result.contains("\"ok\":false"));
        assertTrue(result.contains("permission"));
    }

    @Test
    void agentInvokesLs() {
        List<String> events = new ArrayList<>();
        StubClient client = new StubClient(List.of(
                TestStubs.toolUseMsg("toolu_1", "ls", """
                    {"path":""}
                    """),
                TestStubs.textMsg("The directory contains a.txt and subdir/.")
        ));

        AgentLoop agent = TestStubs.agentWith(client, events, tempDir);
        agent.prompt("List the contents of the project directory");

        assertTrue(events.contains("tool_execution_start: ls"));
        assertTrue(events.stream()
                        .filter(e -> e.startsWith("tool_execution_end:"))
                        .anyMatch(e -> e.contains("a.txt") && e.contains("subdir/")),
                "ls tool result should contain directory entries");
    }
}
