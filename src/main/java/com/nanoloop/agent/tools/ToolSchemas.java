package com.nanoloop.agent.tools;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolUnion;
import java.util.List;
import java.util.Map;

/**
 * Builder helpers to reduce boilerplate when constructing {@link ToolUnion} schemas
 * for the Anthropic API. Provides factory methods for creating tool definitions
 * and common JSON Schema property types (string, integer, boolean).
 */
public final class ToolSchemas {
    private ToolSchemas() {}

    public static ToolUnion tool(String name, String description, Map<String, JsonValue> properties, List<String> required) {
        Tool.InputSchema.Properties.Builder propsBuilder = Tool.InputSchema.Properties.builder();
        for (Map.Entry<String, JsonValue> e : properties.entrySet()) {
            propsBuilder.putAdditionalProperty(e.getKey(), e.getValue());
        }

        Tool.InputSchema.Builder schemaBuilder = Tool.InputSchema.builder()
                .type(JsonValue.from("object"))
                .properties(propsBuilder.build());

        if (required != null && !required.isEmpty()) {
            schemaBuilder.required(required);
        }

        return ToolUnion.ofTool(
                Tool.builder()
                        .name(name)
                        .description(description)
                        .inputSchema(schemaBuilder.build())
                        .build()
        );
    }

    public static JsonValue stringProp(String description) {
        return JsonValue.from(Map.of(
                "type", "string",
                "description", description
        ));
    }

    public static JsonValue intProp(String description) {
        return JsonValue.from(Map.of(
                "type", "integer",
                "description", description
        ));
    }

    public static JsonValue boolProp(String description) {
        return JsonValue.from(Map.of(
                "type", "boolean",
                "description", description
        ));
    }
}
