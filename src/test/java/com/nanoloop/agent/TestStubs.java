package com.nanoloop.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.AnthropicClientAsync;
import com.anthropic.core.ClientOptions;
import com.anthropic.core.ObjectMappers;
import com.anthropic.core.RequestOptions;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.*;
import com.anthropic.services.blocking.*;
import com.anthropic.services.blocking.messages.BatchService;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

/**
 * Shared test infrastructure for agent integration tests.
 * Provides stub AnthropicClient, message factories, and a realistic system prompt.
 */
public final class TestStubs {
    private TestStubs() {}

    public static final String SYSTEM_PROMPT = """
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
            - Never guess tool outputs. Always use the tool result.""".trim();

    // ── Message factories ────────────────────────────────────────────────────

    public static Message parseMessage(String json) {
        try {
            return ObjectMappers.jsonMapper().readValue(json, Message.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Message JSON", e);
        }
    }

    public static Message textMsg(String text) {
        String escaped = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        return parseMessage("""
            {
                "id": "msg_%s",
                "type": "message",
                "role": "assistant",
                "model": "test-model",
                "stop_reason": "end_turn",
                "content": [{"type": "text", "text": "%s"}],
                "usage": {"input_tokens": 1, "output_tokens": 1}
            }
            """.formatted(UUID.randomUUID(), escaped));
    }

    public static Message toolUseMsg(String toolId, String toolName, String inputJson) {
        return parseMessage("""
            {
                "id": "msg_%s",
                "type": "message",
                "role": "assistant",
                "model": "test-model",
                "stop_reason": "tool_use",
                "content": [{"type": "tool_use", "id": "%s", "name": "%s", "input": %s}],
                "usage": {"input_tokens": 1, "output_tokens": 1}
            }
            """.formatted(UUID.randomUUID(), toolId, toolName, inputJson.trim()));
    }

    public static String toolUseContent(String toolId, String toolName, String inputJson) {
        return """
            {"type": "tool_use", "id": "%s", "name": "%s", "input": %s}
            """.formatted(toolId, toolName, inputJson.trim()).trim();
    }

    public static Message multiToolMsg(String... toolUseJsonBlocks) {
        String content = String.join(",", toolUseJsonBlocks);
        return parseMessage("""
            {
                "id": "msg_%s",
                "type": "message",
                "role": "assistant",
                "model": "test-model",
                "stop_reason": "tool_use",
                "content": [%s],
                "usage": {"input_tokens": 1, "output_tokens": 1}
            }
            """.formatted(UUID.randomUUID(), content));
    }

    // ── Agent helper ─────────────────────────────────────────────────────────

    public static AgentLoop agentWith(StubClient client, List<String> events, Path projectRoot) {
        return new AgentLoop(client, SYSTEM_PROMPT, events::add, projectRoot);
    }

    // ── Stub implementations ─────────────────────────────────────────────────

    public static class StubClient implements AnthropicClient {
        private final StubMessageService messageService;

        public StubClient(List<Message> responses) {
            this.messageService = new StubMessageService(new ArrayDeque<>(responses));
        }

        public int requestCount() {
            return messageService.requestCount;
        }

        @Override public MessageService messages() { return messageService; }
        @Override public AnthropicClientAsync async() { throw new UnsupportedOperationException(); }
        @Override public AnthropicClient.WithRawResponse withRawResponse() { throw new UnsupportedOperationException(); }
        @Override public AnthropicClient withOptions(Consumer<ClientOptions.Builder> c) { throw new UnsupportedOperationException(); }
        @Override public CompletionService completions() { throw new UnsupportedOperationException(); }
        @Override public ModelService models() { throw new UnsupportedOperationException(); }
        @Override public BetaService beta() { throw new UnsupportedOperationException(); }
        @Override public void close() {}
    }

    private static class StubMessageService implements MessageService {
        private final Queue<Message> responses;
        int requestCount = 0;

        StubMessageService(Queue<Message> responses) {
            this.responses = responses;
        }

        @Override
        public Message create(MessageCreateParams params, RequestOptions options) {
            requestCount++;
            Message next = responses.poll();
            if (next == null) {
                throw new IllegalStateException("StubMessageService: no more queued responses (call #" + requestCount + ")");
            }
            return next;
        }

        @Override
        public StreamResponse<RawMessageStreamEvent> createStreaming(MessageCreateParams params, RequestOptions options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MessageTokensCount countTokens(MessageCountTokensParams params, RequestOptions options) {
            throw new UnsupportedOperationException();
        }

        @Override public MessageService.WithRawResponse withRawResponse() { throw new UnsupportedOperationException(); }
        @Override public MessageService withOptions(Consumer<ClientOptions.Builder> c) { throw new UnsupportedOperationException(); }
        @Override public BatchService batches() { throw new UnsupportedOperationException(); }
    }
}
