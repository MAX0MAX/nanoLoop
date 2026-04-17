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

class FindToolTest {

    @TempDir Path tempDir;
    FindTool tool;

    @BeforeEach
    void setUp() throws Exception {
        tool = new FindTool(new PathSandbox(tempDir));
        Files.writeString(tempDir.resolve("App.java"), "class App {}");
        Files.createDirectory(tempDir.resolve("sub"));
        Files.writeString(tempDir.resolve("sub/Util.java"), "class Util {}");
        Files.writeString(tempDir.resolve("readme.md"), "# hi");
    }

    @Test
    void findJavaFiles() {
        String result = tool.execute(JsonValue.from(Map.of("pattern", "*.java")));
        assertTrue(result.contains("\"ok\":true"));
        assertTrue(result.contains("App.java"));
        assertTrue(result.contains("Util.java"));
    }

    @Test
    void findNoMatch() {
        String result = tool.execute(JsonValue.from(Map.of("pattern", "*.xyz")));
        assertTrue(result.contains("\"ok\":true"));
        assertTrue(result.contains("\"matches\":[]"));
    }

    @Test
    void findOutsideSandbox() {
        String result = tool.execute(JsonValue.from(Map.of("pattern", "*.java", "path", "../../..")));
        assertTrue(result.contains("\"ok\":false"));
        assertTrue(result.contains("permission"));
    }

    @Test
    void agentInvokesFind() throws Exception {
        List<String> events = new ArrayList<>();
        StubClient client = new StubClient(List.of(
                TestStubs.toolUseMsg("toolu_1", "find", """
                    {"pattern":"*.java"}
                    """),
                TestStubs.textMsg("Found App.java and Util.java.")
        ));

        AgentLoop agent = TestStubs.agentWith(client, events, tempDir);
        agent.prompt("Find all Java files in the project");

        assertTrue(events.contains("tool_execution_start: find"));
        assertTrue(events.stream()
                        .filter(e -> e.startsWith("tool_execution_end:"))
                        .anyMatch(e -> e.contains("App.java") && e.contains("Util.java")),
                "find tool result should contain .java filenames");
    }
}
