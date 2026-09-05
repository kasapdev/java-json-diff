package dev.kasapdev.jsondiff;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal, dependency-free recursive-descent JSON parser.
 *
 * <p>Values are represented using plain Java types:
 * <ul>
 *   <li>JSON object -&gt; {@link LinkedHashMap}{@code <String, Object>} (insertion order preserved)</li>
 *   <li>JSON array -&gt; {@link ArrayList}{@code <Object>}</li>
 *   <li>JSON string -&gt; {@link String}</li>
 *   <li>JSON number -&gt; {@link Double}</li>
 *   <li>JSON true/false -&gt; {@link Boolean}</li>
 *   <li>JSON null -&gt; {@code null}</li>
 * </ul>
 */
public final class JsonParser {

    private final String src;
    private int pos;

    private JsonParser(String src) {
        this.src = src;
        this.pos = 0;
    }

    /**
     * Parses a JSON document from a string.
     *
     * @param json the JSON text
     * @return the parsed value (Map, List, String, Double, Boolean, or null)
     * @throws JsonParseException if the input is not valid JSON
     */
    public static Object parse(String json) {
        if (json == null) {
            throw new JsonParseException("Input must not be null");
        }
        JsonParser parser = new JsonParser(json);
        parser.skipWhitespace();
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (parser.pos != parser.src.length()) {
            throw new JsonParseException("Unexpected trailing content at position " + parser.pos);
        }
        return value;
    }

    private Object parseValue() {
        if (pos >= src.length()) {
            throw new JsonParseException("Unexpected end of input");
        }
        char c = src.charAt(pos);
        switch (c) {
            case '{':
                return parseObject();
            case '[':
                return parseArray();
            case '"':
                return parseString();
            case 't':
                expectLiteral("true");
                return Boolean.TRUE;
            case 'f':
                expectLiteral("false");
                return Boolean.FALSE;
            case 'n':
                expectLiteral("null");
                return null;
            default:
                if (c == '-' || (c >= '0' && c <= '9')) {
                    return parseNumber();
                }
                throw new JsonParseException("Unexpected character '" + c + "' at position " + pos);
        }
    }

    private Map<String, Object> parseObject() {
        Map<String, Object> result = new LinkedHashMap<>();
        expect('{');
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return result;
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw new JsonParseException("Expected string key at position " + pos);
            }
            String key = parseString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            Object value = parseValue();
            result.put(key, value);
            skipWhitespace();
            char c = peek();
            if (c == ',') {
                pos++;
                continue;
            } else if (c == '}') {
                pos++;
                break;
            } else {
                throw new JsonParseException("Expected ',' or '}' at position " + pos);
            }
        }
        return result;
    }

    private List<Object> parseArray() {
        List<Object> result = new ArrayList<>();
        expect('[');
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return result;
        }
        while (true) {
            skipWhitespace();
            Object value = parseValue();
            result.add(value);
            skipWhitespace();
            char c = peek();
            if (c == ',') {
                pos++;
                continue;
            } else if (c == ']') {
                pos++;
                break;
            } else {
                throw new JsonParseException("Expected ',' or ']' at position " + pos);
            }
        }
        return result;
    }

    private String parseString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= src.length()) {
                throw new JsonParseException("Unterminated string starting before position " + pos);
            }
            char c = src.charAt(pos++);
            if (c == '"') {
                break;
            }
            if (c == '\\') {
                if (pos >= src.length()) {
                    throw new JsonParseException("Unterminated escape sequence at position " + pos);
                }
                char esc = src.charAt(pos++);
                switch (esc) {
                    case '"':
                        sb.append('"');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    case '/':
                        sb.append('/');
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'u':
                        if (pos + 4 > src.length()) {
                            throw new JsonParseException("Truncated unicode escape at position " + pos);
                        }
                        String hex = src.substring(pos, pos + 4);
                        int codePoint;
                        try {
                            codePoint = Integer.parseInt(hex, 16);
                        } catch (NumberFormatException e) {
                            throw new JsonParseException("Invalid unicode escape '\\u" + hex + "' at position " + pos);
                        }
                        sb.append((char) codePoint);
                        pos += 4;
                        break;
                    default:
                        throw new JsonParseException("Invalid escape character '\\" + esc + "' at position " + (pos - 1));
                }
            } else if (c < 0x20) {
                throw new JsonParseException("Unescaped control character in string at position " + (pos - 1));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private Double parseNumber() {
        int start = pos;
        if (peek() == '-') {
            pos++;
        }
        if (pos >= src.length() || !Character.isDigit(src.charAt(pos))) {
            throw new JsonParseException("Invalid number at position " + start);
        }
        if (src.charAt(pos) == '0') {
            pos++;
        } else {
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                pos++;
            }
        }
        if (pos < src.length() && src.charAt(pos) == '.') {
            pos++;
            if (pos >= src.length() || !Character.isDigit(src.charAt(pos))) {
                throw new JsonParseException("Invalid number: expected digit after decimal point at position " + pos);
            }
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                pos++;
            }
        }
        if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
            pos++;
            if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                pos++;
            }
            if (pos >= src.length() || !Character.isDigit(src.charAt(pos))) {
                throw new JsonParseException("Invalid number: expected digit in exponent at position " + pos);
            }
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                pos++;
            }
        }
        String numStr = src.substring(start, pos);
        try {
            return Double.parseDouble(numStr);
        } catch (NumberFormatException e) {
            throw new JsonParseException("Invalid number '" + numStr + "' at position " + start);
        }
    }

    private void expectLiteral(String literal) {
        if (pos + literal.length() > src.length() || !src.regionMatches(pos, literal, 0, literal.length())) {
            throw new JsonParseException("Expected '" + literal + "' at position " + pos);
        }
        pos += literal.length();
    }

    private void expect(char c) {
        if (pos >= src.length() || src.charAt(pos) != c) {
            throw new JsonParseException("Expected '" + c + "' at position " + pos);
        }
        pos++;
    }

    private char peek() {
        if (pos >= src.length()) {
            throw new JsonParseException("Unexpected end of input at position " + pos);
        }
        return src.charAt(pos);
    }

    private void skipWhitespace() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else {
                break;
            }
        }
    }
}
