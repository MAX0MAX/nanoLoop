package com.nanoloop.agent.tools;

import com.anthropic.core.JsonValue;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryTest {

    @Test
    void registerAndExecute() {
        ToolRegistry reg = new ToolRegistry();
        reg.register("ping", null, input -> "{\"ok\":true,\"result\":\"pong\"}");

        String result = reg.execute("ping", JsonValue.from(Map.of()));
        assertTrue(result.contains("pong"));
    }

    @Test
    void executeUnknownTool() {
        ToolRegistry reg = new ToolRegistry();
        String result = reg.execute("nope", JsonValue.from(Map.of()));
        assertTrue(result.contains("\"ok\":false"));
        assertTrue(result.contains("unknown tool"));
    }
}
