package com.nanoloop.agent;

import com.nanoloop.agent.TestStubs.StubClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.nanoloop.agent.TestStubs.*;
import static org.junit.jupiter.api.Assertions.*;

class AgentLoopTest {

    @TempDir Path tempDir;
    List<String> events;

    @BeforeEach
    void setUp() {
        events = new ArrayList<>();
    }

    // ── Test cases ───────────────────────────────────────────────────────────

    @Test
    void agentCallsLsTool() throws Exception {
        Files.writeString(tempDir.resolve("hello.txt"), "hi");
        Files.createDirectory(tempDir.resolve("subdir"));

        StubClient client = new StubClient(List.of(
                toolUseMsg("toolu_1", "ls", """
                    {"path":""}
                    """),
                textMsg("I see hello.txt and subdir.")
        ));

        AgentLoop agent = agentWith(client, events, tempDir);
        agent.prompt("List files");

        assertTrue(events.contains("tool_execution_start: ls"), "ls tool should have been called");
        assertHasEvent("tool_execution_end:", "\"ok\":true");
        assertEquals(2, client.requestCount(), "Should make 2 LLM calls: tool_use + final text");
    }

    @Test
    void agentCallsReadTool() throws Exception {
        Files.writeString(tempDir.resolve("test.txt"), "line1\nline2\nline3\n");

        StubClient client = new StubClient(List.of(
                toolUseMsg("toolu_1", "read", """
                    {"path":"test.txt"}
                    """),
                textMsg("The file has 3 lines.")
        ));

        AgentLoop agent = agentWith(client, events, tempDir);
        agent.prompt("Read test.txt");

        assertTrue(events.contains("tool_execution_start: read"));
        assertHasEvent("tool_execution_end:", "line1");
        assertHasEvent("tool_execution_end:", "line2");
    }

    @Test
    void agentCallsWriteThenReadTool() throws Exception {
        StubClient client = new StubClient(List.of(
                toolUseMsg("toolu_1", "write", """
                    {"path":"out.txt","content":"hello world"}
                    """),
                toolUseMsg("toolu_2", "read", """
                    {"path":"out.txt"}
                    """),
                textMsg("File written and verified.")
        ));

        AgentLoop agent = agentWith(client, events, tempDir);
        agent.prompt("Write and then read a file");

        assertTrue(Files.exists(tempDir.resolve("out.txt")), "write tool should have created the file");
        assertEquals("hello world", Files.readString(tempDir.resolve("out.txt")));
        assertHasEvent("tool_execution_end:", "hello world");
    }

    @Test
    void agentHandlesNoToolUse() {
        StubClient client = new StubClient(List.of(
                textMsg("I have nothing to do with tools.")
        ));

        AgentLoop agent = agentWith(client, events, tempDir);
        agent.prompt("Just say hello");

        assertTrue(events.stream().noneMatch(e -> e.startsWith("tool_execution_start")),
                "No tool execution should happen for text-only response");
        assertEquals(1, client.requestCount());
    }

    @Test
    void agentMultiToolSingleResponse() throws Exception {
        Files.writeString(tempDir.resolve("a.txt"), "alpha");

        StubClient client = new StubClient(List.of(
                multiToolMsg(
                        toolUseContent("toolu_1", "ls", """
                            {"path":""}
                            """),
                        toolUseContent("toolu_2", "read", """
                            {"path":"a.txt"}
                            """)
                ),
                textMsg("Both tools called.")
        ));

        AgentLoop agent = agentWith(client, events, tempDir);
        agent.prompt("List and read");

        assertTrue(events.contains("tool_execution_start: ls"));
        assertTrue(events.contains("tool_execution_start: read"));
        assertHasEvent("tool_execution_end:", "alpha");
        assertEquals(2, client.requestCount());
    }

    // ── Assertion helpers ────────────────────────────────────────────────────

    private void assertHasEvent(String prefix, String substring) {
        boolean found = events.stream()
                .filter(e -> e.startsWith(prefix))
                .anyMatch(e -> e.contains(substring));
        assertTrue(found, "Expected event starting with '" + prefix + "' containing '" + substring
                + "' but events were: " + events);
    }
}
