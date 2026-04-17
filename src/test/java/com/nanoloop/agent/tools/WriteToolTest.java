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

class WriteToolTest {

    @TempDir Path tempDir;
    WriteTool tool;

    @BeforeEach
    void setUp() {
        tool = new WriteTool(new PathSandbox(tempDir));
    }

    @Test
    void writeCreatesFile() throws Exception {
        String result = tool.execute(JsonValue.from(Map.of("path", "out/test.txt", "content", "hello")));
        assertTrue(result.contains("\"ok\":true"));
        assertEquals("hello", Files.readString(tempDir.resolve("out/test.txt")));
    }

    @Test
    void writeOutsideSandbox() {
        String result = tool.execute(JsonValue.from(Map.of("path", "../../evil.txt", "content", "x")));
        assertTrue(result.contains("\"ok\":false"));
        assertTrue(result.contains("permission"));
    }

    @Test
    void agentInvokesWrite() throws Exception {
        List<String> events = new ArrayList<>();
        StubClient client = new StubClient(List.of(
                TestStubs.toolUseMsg("toolu_1", "write", """
                    {"path":"greeting.txt","content":"hello world"}
                    """),
                TestStubs.textMsg("File greeting.txt has been created.")
        ));

        AgentLoop agent = TestStubs.agentWith(client, events, tempDir);
        agent.prompt("Create a file called greeting.txt with content 'hello world'");

        assertTrue(events.contains("tool_execution_start: write"));
        assertTrue(Files.exists(tempDir.resolve("greeting.txt")),
                "write tool should have created the file");
        assertEquals("hello world", Files.readString(tempDir.resolve("greeting.txt")));
    }
}
