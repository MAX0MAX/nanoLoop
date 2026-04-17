package com.nanoloop.agent.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JsonResultTest {

    @Test
    void okReturnsValidJson() {
        String json = JsonResult.ok("hello");
        assertTrue(json.contains("\"ok\":true"));
        assertTrue(json.contains("\"result\":\"hello\""));
        assertTrue(json.contains("\"truncated\":false"));
    }

    @Test
    void okTruncatedReturnsFlag() {
        String json = JsonResult.ok("data", true);
        assertTrue(json.contains("\"truncated\":true"));
    }

    @Test
    void errorReturnsValidJson() {
        String json = JsonResult.error("not_found", "file missing");
        assertTrue(json.contains("\"ok\":false"));
        assertTrue(json.contains("\"kind\":\"not_found\""));
        assertTrue(json.contains("\"error\":\"file missing\""));
    }

    @Test
    void specialCharsEscaped() {
        String json = JsonResult.ok("line1\nline2\t\"quoted\"");
        assertTrue(json.contains("\\n"));
        assertTrue(json.contains("\\t"));
        assertTrue(json.contains("\\\"quoted\\\""));
    }
}
