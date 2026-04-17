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

class EditToolTest {

    @TempDir Path tempDir;
    EditTool tool;

    @BeforeEach
    void setUp() throws Exception {
        tool = new EditTool(new PathSandbox(tempDir));
        Files.writeString(tempDir.resolve("file.txt"), "hello world\nhello again\n");
    }

    @Test
    void replaceUniqueString() throws Exception {
        String result = tool.execute(JsonValue.from(Map.of(
                "path", "file.txt", "old_string", "world", "new_string", "earth")));
        assertTrue(result.contains("\"ok\":true"));
        assertTrue(result.contains("\"replacements\":1"));
        String content = Files.readString(tempDir.resolve("file.txt"));
        assertTrue(content.contains("hello earth"));
    }

    @Test
    void replaceAllOccurrences() throws Exception {
        String result = tool.execute(JsonValue.from(Map.of(
                "path", "file.txt", "old_string", "hello", "new_string", "hi", "replace_all", true)));
        assertTrue(result.contains("\"ok\":true"));
        assertTrue(result.contains("\"replacements\":2"));
        String content = Files.readString(tempDir.resolve("file.txt"));
        assertEquals("hi world\nhi again\n", content);
    }

    @Test
    void rejectNonUniqueWithoutReplaceAll() {
        String result = tool.execute(JsonValue.from(Map.of(
                "path", "file.txt", "old_string", "hello", "new_string", "hi")));
        assertTrue(result.contains("\"ok\":false"));
        assertTrue(result.contains("expected exactly 1"));
    }

    @Test
    void editNonexistentFile() {
        String result = tool.execute(JsonValue.from(Map.of(
                "path", "nope.txt", "old_string", "a", "new_string", "b")));
        assertTrue(result.contains("\"ok\":false"));
        assertTrue(result.contains("not_found"));
    }

    @Test
    void agentInvokesEdit() throws Exception {
        Files.writeString(tempDir.resolve("hello.txt"), "hello world\n");

        List<String> events = new ArrayList<>();
        StubClient client = new StubClient(List.of(
                TestStubs.toolUseMsg("toolu_1", "edit", """
                    {"path":"hello.txt","old_string":"world","new_string":"Java"}
                    """),
                TestStubs.textMsg("I replaced 'world' with 'Java'.")
        ));

        AgentLoop agent = TestStubs.agentWith(client, events, tempDir);
        agent.prompt("In hello.txt, change 'world' to 'Java'");

        assertTrue(events.contains("tool_execution_start: edit"));
        String content = Files.readString(tempDir.resolve("hello.txt"));
        assertEquals("hello Java\n", content, "edit tool should have modified the file on disk");
    }
}
