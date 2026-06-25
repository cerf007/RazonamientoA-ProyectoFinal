package org.example.Razonamiento.api;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Minimal JSON construction utility — no external library required.
 * All methods are static; the class is package-private (not public) by design.
 */
class JsonBuilder {

    private JsonBuilder() {
        // utility class — no instances
    }

    // -------------------------------------------------------------------------
    // Primitive emitters
    // -------------------------------------------------------------------------

    /** Emits {@code "key":"escaped_value"} */
    static String str(String key, String value) {
        return "\"" + escape(key) + "\":\"" + escape(value) + "\"";
    }

    /** Emits {@code "key":number} (integer or floating-point, no quotes) */
    static String num(String key, Number value) {
        return "\"" + escape(key) + "\":" + value;
    }

    // -------------------------------------------------------------------------
    // Structural builders
    // -------------------------------------------------------------------------

    /** Wraps members in a JSON object: {@code {member1,member2,...}} */
    static String obj(String... members) {
        return "{" + String.join(",", members) + "}";
    }

    /**
     * Emits a JSON array field: {@code "key":[item1,item2,...]}.
     * Each {@code item} is expected to be a pre-built JSON fragment (e.g. an object string).
     */
    static String array(String key, String... items) {
        return "\"" + escape(key) + "\":[" + String.join(",", items) + "]";
    }

    // -------------------------------------------------------------------------
    // Type serializers
    // -------------------------------------------------------------------------

    /**
     * Returns the UUID as its standard lower-case string representation,
     * e.g. {@code xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx}.
     */
    static String uuid(UUID value) {
        return value.toString();
    }

    /**
     * Returns the {@link LocalDateTime} truncated to whole seconds and formatted
     * using {@link DateTimeFormatter#ISO_LOCAL_DATE_TIME}, e.g. {@code 2024-06-15T10:45:00}.
     */
    static String iso(LocalDateTime value) {
        return value.truncatedTo(ChronoUnit.SECONDS)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    // -------------------------------------------------------------------------
    // Escaping
    // -------------------------------------------------------------------------

    /**
     * Escapes a string for safe embedding as a JSON string value.
     * Handles: {@code "}, {@code \}, {@code \n}, {@code \r}, {@code \t},
     * and all other control characters (code points below U+0020).
     *
     * @param value the raw string; {@code null} is treated as empty string
     * @return the escaped string (without surrounding quotes)
     */
    static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) {
                        // Encode remaining control characters as unicode escape (backslash + uXXXX)
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
