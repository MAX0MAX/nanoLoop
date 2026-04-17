# nanoLoop

A minimal AI agent loop in Java 17 — call an LLM, execute tools locally, feed results back, repeat.

nanoLoop mirrors the architecture of production agent systems (inspired by [Claude Code](https://docs.anthropic.com/en/docs/claude-code)) but strips it down to the essentials: one loop, seven tools, zero frameworks.

## How It Works

```
User prompt
    │
    ▼
┌──────────────────────────┐
│       AgentLoop          │
│                          │
│  ┌────────────────────┐  │
│  │  callLLM()         │◄─┼─── Anthropic /v1/messages
│  └────────┬───────────┘  │
│           │              │
│     tool_use?            │
│      ╱       ╲           │
│    yes        no ──────► │ ── return response
│     │                    │
│  ┌──▼─────────────────┐ │
│  │ executeToolCalls()  │ │
│  │  read / write /     │ │
│  │  edit / grep / ...  │ │
│  └──────────┬──────────┘ │
│             │            │
│     tool_result ─────────┘ (loop back)
│                          │
└──────────────────────────┘
```

The agent sends a user message to the LLM. If the response contains `tool_use` blocks, it executes each tool locally and appends `tool_result` messages back to the conversation. This repeats until the model responds with plain text.

## Available Tools

| Tool | Description |
|------|-------------|
| `read` | Read file contents with optional line offset/limit |
| `write` | Create or overwrite a file |
| `edit` | Search-and-replace within a file (exact string match) |
| `ls` | List directory contents |
| `find` | Find files by glob pattern |
| `grep` | Search file contents with regex |
| `bash` | Execute allowlisted shell commands (`mvn`, `git`, `ls`, `pwd`, `java`, `javac`, `echo`, `cat`) |

All file tools are sandboxed to the project root via `PathSandbox` — no path traversal, no escaping.

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.8+
- An Anthropic API key (or a compatible local proxy)

### Run

```bash
export ANTHROPIC_API_KEY=sk-ant-...
mvn -DskipTests compile exec:java -Dexec.mainClass=com.nanoloop.agent.Main
```

### Test

```bash
mvn test                                        # full suite
mvn -Dtest=ReadToolTest test                    # single test class
mvn -Dtest=EditToolTest#editExactMatch test     # single test method
```

## Configuration

Edit `src/main/resources/application.properties`:

```properties
anthropic.base-url=https://api.anthropic.com   # or http://localhost:4141 for a local proxy
anthropic.model=claude-haiku-4-5
anthropic.max-tokens=1024
```

The API key is read from the `ANTHROPIC_API_KEY` environment variable.

## Project Structure

```
src/main/java/com/nanoloop/agent/
├── AgentLoop.java              # Core agent loop (prompt → LLM → tools → repeat)
├── Main.java                   # Entry point
├── tools/
│   ├── ToolRegistry.java       # Maps tool names to (schema, executor) pairs
│   ├── ToolSchemas.java        # Builder helpers for tool schemas
│   ├── PathSandbox.java        # Filesystem sandboxing
│   ├── ReadTool.java
│   ├── WriteTool.java
│   ├── EditTool.java
│   ├── LsTool.java
│   ├── FindTool.java
│   ├── GrepTool.java
│   └── BashTool.java
└── util/
    ├── JsonResult.java         # Standardized JSON responses (ok/error)
    └── InputParser.java        # Type coercion helpers
```

## Design Decisions

- **No frameworks** — pure Java 17 + the [Anthropic Java SDK](https://github.com/anthropics/anthropic-sdk-java). No Spring, no dependency injection.
- **Full content blocks** — assistant messages retain `tool_use` blocks intact so `tool_result` references stay valid across turns.
- **Centralized tool wiring** — `AgentLoop.buildToolRegistry()` is the single place to add tools.
- **Narrow bash** — `BashTool` only allows a short allowlist of commands and rejects shell operators (`|`, `;`, `&&`, `>`).
- **Sandboxed filesystem** — all file operations are confined to the project root.

## Tech Stack

- Java 17
- [Anthropic Java SDK](https://github.com/anthropics/anthropic-sdk-java) 2.24.0
- JUnit 5 for testing
- Maven for build

## Contributing

Contributions are welcome! Feel free to open an issue or submit a pull request.

## License

This project is licensed under the [MIT License](LICENSE).
