# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What Is This

nanoLoop is a personal toy project — a minimal Java agent loop inspired by the architecture of production agent systems like Claude Code. It's actively evolving with new features being added incrementally.

## Build & Test

```bash
mvn test                                    # full JUnit 5 suite (33 tests)
mvn -DskipTests compile                     # compile only
mvn -Dtest=ReadToolTest test                # single test class
mvn -Dtest=ReadToolTest#readExistingFile test  # single test method
```

## Architecture

**nanoLoop** calls an Anthropic-compatible `/v1/messages` endpoint, detects `tool_use` blocks, executes tools locally, and feeds `tool_result` back until the model stops requesting tools. Design patterns are ported from pi-mono's TypeScript implementation into Java 17.

**Core flow** (`AgentLoop.java`):
- `prompt(userInput)` → append user message → inner loop: `callLLM()` → if `tool_use` → `executeToolCalls()` → repeat until no tools requested
- Assistant responses are stored as **full content blocks** (including `tool_use`), not just extracted text. This is critical — `tool_result` blocks reference `toolUseId` from the preceding assistant message.

**Tool system** (`tools/` package):
- `ToolRegistry` maps tool names to `(schema, executor)` pairs. `AgentLoop.buildToolRegistry()` is the single wiring point.
- `PathSandbox` enforces project-root boundary on all file tools. Paths are resolved relative to projectRoot; traversal or external absolute paths are rejected.
- `ToolSchemas` provides builder helpers to reduce `Tool.InputSchema` boilerplate.
- All tool executors return JSON via `JsonResult`: `{"ok":true,"result":...,"truncated":...}` or `{"ok":false,"kind":"validation|not_found|permission|runtime","error":"..."}`.
- `InputParser` (`util/`) has shared type-coercion helpers (`intOrDefault`, `boolOrDefault`, etc.) used across tools.

**Available tools**: `read`, `ls`, `find`, `grep`, `write`, `edit`, `bash`

**Config** (`src/main/resources/application.properties`):
- `anthropic.base-url` — API endpoint (default: `https://api.anthropic.com`)
- `anthropic.model` — model ID (default: `claude-haiku-4-5`)
- `anthropic.max-tokens` — max output tokens (default: `1024`)
- API key comes from `ANTHROPIC_API_KEY` env var (via SDK's `fromEnv()`)

## Key Conventions

- **Tool registration is centralized** in `AgentLoop.buildToolRegistry()`. Adding a tool = add schema + executor there.
- **Edit workflow**: model must `read` first, then `edit` with exact `old_string`. EditTool rejects ambiguous matches unless `replace_all=true`.
- **BashTool is intentionally narrow**: allowlisted prefixes only (`mvn`, `git`, `ls`, `pwd`, `java`, `javac`, `echo`, `cat`), shell operators (`|`, `;`, `&&`, `>`) rejected.
- **Tests use `@TempDir`** for sandboxed filesystem and assert on JSON substrings from tool results.
- **Multi-turn**: repeated `prompt()` calls on same `AgentLoop` instance reuse the transcript. New instance = fresh session.
## Roadmap

Completed: tool registry + 7 coding tools (read/ls/find/grep/write/edit/bash)
Next: multi-turn conversation improvements, then memory (JSONL persistence + compaction)
