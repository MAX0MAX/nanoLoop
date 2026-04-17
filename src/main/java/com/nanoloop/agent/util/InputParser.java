package com.nanoloop.agent.util;

/**
 * Shared type-coercion helpers for extracting typed values from the raw
 * {@code Map<?,?>} produced by {@code JsonValue.convert(Map.class)}.
 *
 * <p>Each method handles null, Number, Boolean, and String inputs gracefully,
 * returning a sensible default when the value is absent or unparseable.
 */
public final class InputParser {
    private InputParser() {}

    public static int intOrDefault(Object v, int def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(v.toString());
        } catch (Exception ignored) {
            return def;
        }
    }

    public static long longOrDefault(Object v, long def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(v.toString());
        } catch (Exception ignored) {
            return def;
        }
    }

    public static boolean boolOrDefault(Object v, boolean def) {
        if (v == null) return def;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(v.toString());
    }

    public static String asString(Object o) {
        return o == null ? null : o.toString();
    }
}
