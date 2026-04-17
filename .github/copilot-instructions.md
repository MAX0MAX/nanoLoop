# nanoLoop Copilot Instructions

## Build and test commands

From the repository root:

```bash
mvn test
mvn -DskipTests package
mvn -Dtest=ReadToolTest test
mvn -Dtest=ReadToolTest#readExistingFile test
```

- `mvn test` runs the full JUnit 5 suite.
- `mvn -DskipTests package` builds `target/nanoloop-1.0-SNAPSHOT.jar`.
- Use `-Dtest=ClassName` for a single test class and `-Dtest=ClassName#methodName` for a single test method.

## High-level architecture

- The meaningful runtime entrypoint is `src/main/java/com/nanoloop/agent/AgentLoop.java`. `src/main/java/com/nanoloop/agent/Main.java` is still the default IntelliJ starter and is not the real application flow.
- `AgentLoop` owns the Anthropic client, the conversation transcript, and the control flow. Each `prompt()` call appends a user message, calls the model, executes any returned `tool_use` blocks, appends matching `tool_result` blocks as a synthetic user message, and loops until the assistant stops requesting tools.
- Anthropic settings come from `src/main/resources/application.properties` (`anthropic.base-url`, `anthropic.model`, `anthropic.max-tokens`) and credentials/environment settings come from `AnthropicOkHttpClient.builder().fromEnv()`.
- The tool layer is centered on `ToolRegistry` and `ToolSchemas`. `AgentLoop.buildToolRegistry()` is the single place where tool schemas and executors are wired together for `read`, `ls`, `find`, `grep`, `write`, `edit`, and `bash`.
- `PathSandbox` is the hard project-root boundary for all file-aware tools. Tools resolve user paths relative to the project root and reject traversal or absolute paths outside that root.
- `JsonResult` defines the tool result contract shared by every tool: executors return JSON strings with either `{"ok":true,"result":...,"truncated":...}` or `{"ok":false,"kind":...,"error":...}`.

## Key conventions

- Preserve assistant content blocks exactly in `messages`; the loop relies on raw `tool_use` blocks so later `tool_result` blocks can reference the correct `toolUseId`.
- Keep tool registration centralized in `AgentLoop.buildToolRegistry()`. Adding a tool here means adding both the Anthropic schema and the executor wiring.
- Match existing tool behavior instead of inventing new result shapes. Tool executors return JSON strings, not typed objects, and tests usually assert on JSON substrings rather than deserializing responses.
- Follow the existing file-edit workflow: read first, then edit with an exact `old_string`. `EditTool` rejects ambiguous single replacements unless `replace_all` is explicitly true.
- Keep new filesystem features sandbox-aware and limit-aware. Existing tools enforce size and count caps, skip binary files with a simple null-byte check, and surface sandbox violations as `permission` errors.
- `BashTool` is intentionally narrow: only allowlisted command prefixes (`mvn`, `git`, `ls`, `pwd`, `java`, `javac`, `echo`, `cat`) are permitted, and shell chaining/redirection tokens such as `|`, `;`, `&&`, and `>` are rejected.
- Multi-turn state is instance-local. Repeated `prompt()` calls on the same `AgentLoop` reuse the existing transcript; constructing a new `AgentLoop` starts a fresh session.
- Tests follow a consistent style: JUnit 5, `@TempDir` for sandboxed filesystem behavior, and focused assertions around the JSON contract and permission boundaries.
