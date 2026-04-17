package com.nanoloop.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.*;
import com.nanoloop.agent.tools.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * Minimal agent loop demo — mirrors pi-agent-core architecture.
 * Tool use and memory are stubbed; focus is on the dual-loop structure.
 *
 * Architecture key points:
 *   - Serial guard: synchronized prompt() blocks concurrent runs
 *   - Dual loop: outer = follow-up messages, inner = tool calls + steering
 *   - Event sequence: agent_start → turn_start → message_* → tool_* → turn_end → agent_end
 */
public class AgentLoop {

    private static final Properties CONFIG = loadConfig();

    private final AnthropicClient client;
    private final String systemPrompt;
    private final List<MessageParam> messages = new ArrayList<>();
    private volatile boolean isRunning = false;
    private final Consumer<String> onEvent;
    private final ToolRegistry toolRegistry;

    public AgentLoop(String systemPrompt, Consumer<String> onEvent) {
        this(systemPrompt, onEvent, Paths.get("").toAbsolutePath().normalize());
    }

    public AgentLoop(String systemPrompt, Consumer<String> onEvent, Path projectRoot) {
        this(AnthropicOkHttpClient.builder()
                .baseUrl(CONFIG.getProperty("anthropic.base-url", "https://api.anthropic.com"))
                .fromEnv()
                .build(), systemPrompt, onEvent, projectRoot);
    }

    // Package-private: used by tests to inject a stub client
    AgentLoop(AnthropicClient client, String systemPrompt, Consumer<String> onEvent, Path projectRoot) {
        this.client = client;
        this.systemPrompt = systemPrompt;
        this.onEvent = onEvent;
        this.toolRegistry = buildToolRegistry(projectRoot);
    }

    private static Properties loadConfig() {
        Properties props = new Properties();
        try (InputStream in = AgentLoop.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in != null) props.load(in);
        } catch (IOException ignored) {
        }
        return props;
    }

    /**
     * Serial guard: only one active run at a time.
     * To inject mid-run, extend steer() / followUp() methods.
     */
    public synchronized void prompt(String userInput) {
        if (isRunning) throw new IllegalStateException("Agent already running");
        isRunning = true;
        emit("agent_start");
        messages.add(userMessage(userInput));
        try {
            runLoop();
        } finally {
            isRunning = false;
            emit("agent_end");
        }
    }

    // ── Dual loop ─────────────────────────────────────────────────────────────

    private void runLoop() {
        while (true) {                          // outer: for follow-up messages
            boolean hasPendingTools;
            do {                                // inner: for tool calls + steering
                emit("turn_start");
                hasPendingTools = false;

                Message response = callLLM();

                // Tool calls present? Execute and continue inner loop
                boolean wantsTools = response.content().stream()
                        .anyMatch(b -> b.toolUse().isPresent());
                if (wantsTools) {
                    executeToolCalls(response);
                    hasPendingTools = true;
                }

                emit("turn_end");
            } while (hasPendingTools);

            break; // No follow-up messages → exit outer loop
        }
    }

    // ── LLM call ──────────────────────────────────────────────────────────────

    private Message callLLM() {
        emit("message_start");
        MessageCreateParams params = MessageCreateParams.builder()
                .model(CONFIG.getProperty("anthropic.model", "claude-haiku-4-5"))
                .maxTokens(Long.parseLong(CONFIG.getProperty("anthropic.max-tokens", "1024")))
                .system(systemPrompt)
                .tools(toolRegistry.schemas())
                .messages(messages)
                .build();

        Message response = client.messages().create(params);

        // Message boundary: store assistant's raw content blocks (including tool_use)
        List<ContentBlockParam> assistantBlocks = response.content().stream()
                .map(ContentBlock::toParam)
                .toList();

        messages.add(MessageParam.builder()
                .role(MessageParam.Role.ASSISTANT)
                .contentOfBlockParams(assistantBlocks)
                .build());

        String text = response.content().stream()
                .flatMap(b -> b.text().stream())
                .map(TextBlock::text)
                .findFirst().orElse("");

        emit("message_end: " + preview(text));
        return response;
    }

    // ── Tool execution — prepare → execute → finalize ─────────────────────────

    private void executeToolCalls(Message response) {
        List<ContentBlockParam> toolResults = new ArrayList<>();

        for (ContentBlock block : response.content()) {
            block.toolUse().ifPresent(tool -> {
                emit("tool_execution_start: " + tool.name());

                String result = toolRegistry.execute(tool.name(), tool._input());

                emit("tool_execution_end: " + result);
                toolResults.add(ContentBlockParam.ofToolResult(
                        ToolResultBlockParam.builder()
                                .toolUseId(tool.id())
                                .content(result)
                                .build()));
            });
        }

        if (!toolResults.isEmpty()) {
            messages.add(MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .contentOfBlockParams(toolResults)
                    .build());
        }
    }

    // ── Tool Registry ────────────────────────────────────────────────────────

    private static ToolRegistry buildToolRegistry(Path projectRoot) {
        PathSandbox sandbox = new PathSandbox(projectRoot);
        ToolRegistry reg = new ToolRegistry();

        ReadTool readTool = new ReadTool(sandbox);
        reg.register("read", ToolSchemas.tool("read",
                "Read a file. Returns numbered lines.",
                Map.of(
                        "path", ToolSchemas.stringProp("File path (relative to project root)"),
                        "offset", ToolSchemas.intProp("Start line (1-indexed, default 1)"),
                        "limit", ToolSchemas.intProp("Max lines to return (default 200, max 2000)")
                ), List.of("path")), readTool::execute);

        LsTool lsTool = new LsTool(sandbox);
        reg.register("ls", ToolSchemas.tool("ls",
                "List directory contents.",
                Map.of(
                        "path", ToolSchemas.stringProp("Directory path (default: project root)")
                ), null), lsTool::execute);

        FindTool findTool = new FindTool(sandbox);
        reg.register("find", ToolSchemas.tool("find",
                "Find files by glob pattern.",
                Map.of(
                        "pattern", ToolSchemas.stringProp("Glob pattern (e.g. *.java, **/*.ts)"),
                        "path", ToolSchemas.stringProp("Search root (default: project root)")
                ), List.of("pattern")), findTool::execute);

        GrepTool grepTool = new GrepTool(sandbox);
        reg.register("grep", ToolSchemas.tool("grep",
                "Search file contents for a text pattern.",
                Map.of(
                        "pattern", ToolSchemas.stringProp("Text to search for"),
                        "path", ToolSchemas.stringProp("Search root (default: project root)"),
                        "ignoreCase", ToolSchemas.boolProp("Case-insensitive search (default false)")
                ), List.of("pattern")), grepTool::execute);

        WriteTool writeTool = new WriteTool(sandbox);
        reg.register("write", ToolSchemas.tool("write",
                "Write content to a file (creates parent dirs).",
                Map.of(
                        "path", ToolSchemas.stringProp("File path"),
                        "content", ToolSchemas.stringProp("File content")
                ), List.of("path", "content")), writeTool::execute);

        EditTool editTool = new EditTool(sandbox);
        reg.register("edit", ToolSchemas.tool("edit",
                "Replace text in a file. Use read first to see the file.",
                Map.of(
                        "path", ToolSchemas.stringProp("File path"),
                        "old_string", ToolSchemas.stringProp("Exact text to find"),
                        "new_string", ToolSchemas.stringProp("Replacement text"),
                        "replace_all", ToolSchemas.boolProp("Replace all occurrences (default false, requires exactly 1 match)")
                ), List.of("path", "old_string", "new_string")), editTool::execute);

        BashTool bashTool = new BashTool(sandbox);
        reg.register("bash", ToolSchemas.tool("bash",
                "Execute a shell command (sandboxed to project dir). Allowed: mvn, git, ls, pwd, java, javac, echo, cat.",
                Map.of(
                        "command", ToolSchemas.stringProp("Shell command to execute"),
                        "timeout_ms", ToolSchemas.intProp("Timeout in ms (default 120000)")
                ), List.of("command")), bashTool::execute);

        return reg;
    }

    // ── Utility methods ───────────────────────────────────────────────────────

    private MessageParam userMessage(String text) {
        return MessageParam.builder().role(MessageParam.Role.USER).content(text).build();
    }

    private String preview(String s) {
        return s.length() <= 80 ? s : s.substring(0, 80) + "…";
    }

    private void emit(String event) {
        onEvent.accept(event);
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    public static void main(String[] args) {
        Path projectRoot = Paths.get("").toAbsolutePath().normalize();
        AgentLoop agent = new AgentLoop(
                """
                You are a coding assistant with access to these tools: read, ls, find, grep, write, edit, bash.

                Tool use policy (MUST follow):
                - To see file contents, MUST call read (never guess).
                - To list directories, MUST call ls.
                - To find files by pattern, MUST call find.
                - To search text in files, MUST call grep.
                - To create/overwrite a file, MUST call write.
                - To modify part of a file, MUST call read first, then call edit with exact old_string.
                - To run shell commands, MUST call bash (only allowed: mvn, git, ls, pwd, java, javac, echo, cat).
                - All paths are relative to the project root.
                - Never guess tool outputs. Always use the tool result.
                """.trim(),
                event -> System.out.println("[EVENT] " + event),
                projectRoot
        );

        agent.prompt("List all files in the src directory");
        agent.prompt("Read pom.xml and tell me what dependencies we have");
    }
}
