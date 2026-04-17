package com.nanoloop.agent.util;

import java.util.Map;

/**
 * Standardized JSON response builder for all tool results.
 *
 * <p>All tools return one of two shapes:
 * <ul>
 *   <li>Success: {@code {"ok":true, "result":..., "truncated":false}}</li>
 *   <li>Error: {@code {"ok":false, "kind":"validation|not_found|permission|runtime", "error":"..."}}</li>
 * </ul>
 *
 * <p>Uses a minimal hand-rolled JSON encoder to avoid adding external dependencies.
 */
public final class JsonResult {
    private JsonResult() {}

    public static String ok(Object result) {
        return toJson(Map.of(
                "ok", true,
                "result", result,
                "truncated", false
        ));
    }

    public static String ok(Object result, boolean truncated) {
        return toJson(Map.of(
                "ok", true,
                "result", result,
                "truncated", truncated
        ));
    }

    public static String error(String kind, String message) {
        return toJson(Map.of(
                "ok", false,
                "kind", kind,
                "error", message
        ));
    }

    private static String toJson(Object o) {
        // Minimal JSON encoder for primitives/maps/lists used by our tools.
        // Avoids adding new dependencies.
        if (o == null) return "null";
        if (o instanceof Boolean b) return b ? "true" : "false";
        if (o instanceof Number n) return n.toString();
        if (o instanceof String s) return quote(s);
        if (o instanceof Map<?, ?> m) {
            StringBuilder sb = new StringBuilder();
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append(quote(String.valueOf(e.getKey())));
                sb.append(':');
                sb.append(toJson(e.getValue()));
            }
            sb.append('}');
            return sb.toString();
        }
        if (o instanceof Iterable<?> it) {
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            boolean first = true;
            for (Object v : it) {
                if (!first) sb.append(',');
                first = false;
                sb.append(toJson(v));
            }
            sb.append(']');
            return sb.toString();
        }
        return quote(String.valueOf(o));
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
