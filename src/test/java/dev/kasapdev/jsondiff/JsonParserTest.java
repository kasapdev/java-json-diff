package dev.kasapdev.jsondiff;

import java.util.List;
import java.util.Map;

public final class JsonParserTest {

    public static void main(String[] args) {
        testPrimitives();
        testNestedObjectsAndArrays();
        testStringEscapesAndUnicode();
        testNumbers();
        testWhitespaceHandling();
        testMalformedInputThrows();
        testSurrogatePairUnicodeEscape();
        testLeadingZeroFollowedByDigitThrows();
        testDuplicateObjectKeysLastWins();

        TestKit.finish();
    }

    private static void testPrimitives() {
        TestKit.check("parses true", JsonParser.parse("true").equals(Boolean.TRUE));
        TestKit.check("parses false", JsonParser.parse("false").equals(Boolean.FALSE));
        TestKit.check("parses null", JsonParser.parse("null") == null);
        TestKit.check("parses empty object", ((Map<?, ?>) JsonParser.parse("{}")).isEmpty());
        TestKit.check("parses empty array", ((List<?>) JsonParser.parse("[]")).isEmpty());
        TestKit.check("parses plain string", JsonParser.parse("\"hello\"").equals("hello"));
    }

    @SuppressWarnings("unchecked")
    private static void testNestedObjectsAndArrays() {
        String json = "{\"user\":{\"name\":\"Ada\",\"tags\":[\"admin\",\"dev\",{\"nested\":true}]},\"count\":3}";
        Object parsed = JsonParser.parse(json);
        TestKit.check("top level is a map", parsed instanceof Map);
        Map<String, Object> root = (Map<String, Object>) parsed;
        Map<String, Object> user = (Map<String, Object>) root.get("user");
        TestKit.check("nested object field 'name'", "Ada".equals(user.get("name")));
        List<Object> tags = (List<Object>) user.get("tags");
        TestKit.check("nested array has 3 elements", tags.size() == 3);
        TestKit.check("array element 0 is 'admin'", "admin".equals(tags.get(0)));
        Map<String, Object> deepNested = (Map<String, Object>) tags.get(2);
        TestKit.check("deeply nested object value", Boolean.TRUE.equals(deepNested.get("nested")));
        TestKit.check("sibling numeric field preserved", root.get("count").equals(3.0));
        TestKit.check("insertion order preserved", new java.util.ArrayList<>(root.keySet()).equals(List.of("user", "count")));
    }

    private static void testStringEscapesAndUnicode() {
        String json = "\"line1\\nline2\\ttabbed \\\"quoted\\\" and \\u00e9\\u0041\"";
        String parsed = (String) JsonParser.parse(json);
        String expected = "line1\nline2\ttabbed \"quoted\" and \u00e9A";
        TestKit.check("string escapes decoded correctly", parsed.equals(expected));

        String backslashJson = "\"C:\\\\path\\\\to\\\\file\"";
        TestKit.check("backslash escape decoded", JsonParser.parse(backslashJson).equals("C:\\path\\to\\file"));

        String forwardSlashJson = "\"a\\/b\"";
        TestKit.check("forward slash escape decoded", JsonParser.parse(forwardSlashJson).equals("a/b"));

        String emptyString = "\"\"";
        TestKit.check("empty string parses", JsonParser.parse(emptyString).equals(""));
    }

    private static void testNumbers() {
        TestKit.check("parses integer", JsonParser.parse("42").equals(42.0));
        TestKit.check("parses negative integer", JsonParser.parse("-17").equals(-17.0));
        TestKit.check("parses decimal", JsonParser.parse("3.14").equals(3.14));
        TestKit.check("parses negative decimal", JsonParser.parse("-0.5").equals(-0.5));
        TestKit.check("parses exponent (lowercase e, positive)", JsonParser.parse("1e3").equals(1000.0));
        TestKit.check("parses exponent (uppercase E, explicit plus)", JsonParser.parse("1E+2").equals(100.0));
        TestKit.check("parses negative exponent", JsonParser.parse("2.5e-2").equals(0.025));
        TestKit.check("parses zero", JsonParser.parse("0").equals(0.0));
        TestKit.check("parses number with leading zero fraction", JsonParser.parse("0.001").equals(0.001));
    }

    private static void testWhitespaceHandling() {
        String json = "  {\n  \"a\" : 1,\n\t\"b\": [ 1 , 2 , 3 ]\n}  ";
        Object parsed = JsonParser.parse(json);
        TestKit.check("whitespace around tokens is ignored", parsed instanceof Map);
    }

    private static void testMalformedInputThrows() {
        TestKit.check("trailing comma in object throws", throwsParseException("{\"a\":1,}"));
        TestKit.check("trailing comma in array throws", throwsParseException("[1,2,]"));
        TestKit.check("unterminated string throws", throwsParseException("\"abc"));
        TestKit.check("unquoted key throws", throwsParseException("{a:1}"));
        TestKit.check("trailing garbage after valid value throws", throwsParseException("{}garbage"));
        TestKit.check("empty input throws", throwsParseException(""));
        TestKit.check("invalid literal throws", throwsParseException("tru"));
        TestKit.check("number with only minus throws", throwsParseException("-"));
    }

    private static void testSurrogatePairUnicodeEscape() {
        // U+1F600 (grinning face emoji), encoded as a UTF-16 surrogate pair via two unicode escape sequences.
        String json = "\"\\uD83D\\uDE00\"";
        String parsed = (String) JsonParser.parse(json);
        String expected = "😀";
        TestKit.check("surrogate pair unicode escapes combine into the correct full code point", parsed.equals(expected));
        TestKit.check("surrogate pair unicode escapes produce exactly one code point", parsed.codePointCount(0, parsed.length()) == 1);
    }

    private static void testLeadingZeroFollowedByDigitThrows() {
        TestKit.check("a leading zero followed by another digit is rejected", throwsParseException("01"));
        TestKit.check("a leading zero followed by another digit is rejected inside an array", throwsParseException("[01]"));
    }

    @SuppressWarnings("unchecked")
    private static void testDuplicateObjectKeysLastWins() {
        Map<String, Object> parsed = (Map<String, Object>) JsonParser.parse("{\"a\":1,\"a\":2}");
        TestKit.check("duplicate object keys: the last occurrence wins", parsed.get("a").equals(2.0));
        TestKit.check("duplicate object keys: only one entry is kept, not two", parsed.size() == 1);
    }

    private static boolean throwsParseException(String json) {
        try {
            JsonParser.parse(json);
            return false;
        } catch (JsonParseException e) {
            return true;
        }
    }
}
