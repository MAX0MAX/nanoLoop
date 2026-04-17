package com.nanoloop.agent.tools;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ToolUnion;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps tool names to their (schema, executor) pairs.
 * Provides a single point for registering tools and dispatching execution.
 *
 * <p>Usage: register tools via {@link #register}, export schemas to the LLM via
 * {@link #schemas()}, and dispatch incoming tool calls via {@link #execute}.
 */
public final class ToolRegistry {

    /**
     * Functional interface for tool execution. Accepts raw JSON input from the LLM
     * and returns a JSON string following the {@link JsonResult} contract.
     */
    @FunctionalInterface
    public interface ToolExecutor {
        String execute(JsonValue input);
    }

    public record ToolSpec(ToolUnion schema, ToolExecutor executor) {}

    private final Map<String, ToolSpec> byName = new HashMap<>();

    public void register(String name, ToolUnion schema, ToolExecutor executor) {
        byName.put(name, new ToolSpec(schema, executor));
    }

    public List<ToolUnion> schemas() {
        return new ArrayList<>(byName.values().stream().map(ToolSpec::schema).toList());
    }

    public String execute(String name, JsonValue input) {
        ToolSpec spec = byName.get(name);
        if (spec == null) {
            return "{\"ok\":false,\"kind\":\"validation\",\"error\":\"unknown tool: " + name + "\"}";
        }
        return spec.executor().execute(input);
    }
}
