package org.example.Razonamiento.api;

import net.jqwik.api.*;
import net.jqwik.api.Combinators;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based and unit tests for JsonBuilder.
 */
class JsonBuilderTest {

    // -----------------------------------------------------------------------
    // Property 9: UUID serialization always produces standard format
    // Feature: servlet-rest-api, Property 9: UUID serialization always produces standard format
    // -----------------------------------------------------------------------

    @Provide
    Arbitrary<UUID> uuids() {
        return Combinators.combine(
            Arbitraries.longs(),
            Arbitraries.longs()
        ).as((hi, lo) -> new UUID(hi, lo));
    }

    @Property(tries = 500)
    void uuidSerializationMatchesStandardFormat(@ForAll("uuids") UUID uuid) {
        // Validates: Requirements 6.1
        String result = JsonBuilder.uuid(uuid);
        assertTrue(
            result.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
            "UUID result '" + result + "' does not match standard format"
        );
    }

    // -----------------------------------------------------------------------
    // Property 10: ISO-8601 date serialization is correct for all dates
    // Feature: servlet-rest-api, Property 10: ISO-8601 date serialization is correct for all dates
    // -----------------------------------------------------------------------

    @Property(tries = 500)
    void dateSerializationRoundTrip(@ForAll("localDateTimes") LocalDateTime dt) {
        // Validates: Requirements 6.2
        String result = JsonBuilder.iso(dt);

        // Must match yyyy-MM-ddTHH:mm:ss
        assertTrue(
            result.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"),
            "ISO result '" + result + "' does not match expected pattern"
        );

        // Round-trip: parsing must yield the input truncated to seconds
        LocalDateTime parsed = LocalDateTime.parse(result);
        LocalDateTime expected = dt.truncatedTo(ChronoUnit.SECONDS);
        assertEquals(expected, parsed,
            "Round-trip failed: original=" + dt + ", parsed=" + parsed);
    }

    @Provide
    Arbitrary<LocalDateTime> localDateTimes() {
        return Combinators.combine(
            Arbitraries.integers().between(1970, 2099),
            Arbitraries.integers().between(1, 12),
            Arbitraries.integers().between(1, 28),
            Arbitraries.integers().between(0, 23),
            Arbitraries.integers().between(0, 59),
            Arbitraries.integers().between(0, 59)
        ).as((y, mo, d, h, mi, s) -> LocalDateTime.of(y, mo, d, h, mi, s));
    }

    // -----------------------------------------------------------------------
    // Property 12: JSON string escape is safe for arbitrary Unicode input
    // Feature: servlet-rest-api, Property 12: JSON string escape is safe for arbitrary Unicode input
    // -----------------------------------------------------------------------

    @Property(tries = 200)
    void jsonEscapeContainsNoUnescapedQuotesOrBackslashes(
            @ForAll("unicodeStrings") String s) {
        // Validates: Requirements 6.5
        String escaped = JsonBuilder.escape(s);

        // The escaped result must not contain an unescaped " (double-quote)
        // i.e., every " must be preceded by \
        for (int i = 0; i < escaped.length(); i++) {
            if (escaped.charAt(i) == '"') {
                assertTrue(i > 0 && escaped.charAt(i - 1) == '\\',
                    "Unescaped double-quote at position " + i + " in: " + escaped);
            }
            // A backslash must be followed by a valid escape character
            if (escaped.charAt(i) == '\\') {
                assertTrue(i + 1 < escaped.length(),
                    "Trailing backslash in: " + escaped);
                char next = escaped.charAt(i + 1);
                assertTrue(
                    next == '"' || next == '\\' || next == 'b' || next == 'f' ||
                    next == 'n' || next == 'r' || next == 't' || next == 'u',
                    "Invalid escape sequence \\" + next + " in: " + escaped
                );
                if (next != 'u') i++; // skip the next char since it's part of the escape
            }
        }

        // Wrapping in quotes must produce valid JSON (no parse error via simple check)
        String json = "\"" + escaped + "\"";
        assertNotNull(json);
        assertTrue(json.startsWith("\"") && json.endsWith("\""));
        for (int i = 1; i < json.length() - 1; i++) {
            char c = json.charAt(i);
            if (c < 0x20) {
                fail("Unescaped control character at index " + i + " in JSON string");
            }
        }
    }

    @Provide
    Arbitrary<String> unicodeStrings() {
        return Arbitraries.strings().withCharRange((char) 0x0000, (char) 0xFFFF)
                          .ofMinLength(0).ofMaxLength(100);
    }

    // -----------------------------------------------------------------------
    // Basic unit tests for obj / str / num helpers
    // -----------------------------------------------------------------------

    @Test
    void objWrapsMembers() {
        String result = JsonBuilder.obj(
            JsonBuilder.str("key", "value"),
            JsonBuilder.num("n", 42)
        );
        assertEquals("{\"key\":\"value\",\"n\":42}", result);
    }

    @Test
    void strEscapesQuotes() {
        String result = JsonBuilder.str("msg", "say \"hello\"");
        assertEquals("\"msg\":\"say \\\"hello\\\"\"", result);
    }

    @Test
    void uuidRoundTrip() {
        UUID id = UUID.randomUUID();
        assertEquals(id.toString(), JsonBuilder.uuid(id));
    }
}
