package dev.infrai.edtech;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Json {
    private Json() {}

    static Map<String, Object> object(String text) {
        Object value = new Parser(text).parse();
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("Expected JSON object");
        @SuppressWarnings("unchecked") Map<String, Object> result = (Map<String, Object>) map;
        return result;
    }

    static String write(Object value) {
        if (value == null) return "null";
        if (value instanceof String string) return "\"" + escape(string) + "\"";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            List<String> entries = new ArrayList<>();
            map.forEach((key, item) -> entries.add(write(String.valueOf(key)) + ":" + write(item)));
            return "{" + String.join(",", entries) + "}";
        }
        if (value instanceof Iterable<?> values) {
            List<String> items = new ArrayList<>();
            values.forEach(item -> items.add(write(item)));
            return "[" + String.join(",", items) + "]";
        }
        throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass());
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static final class Parser {
        private final String text;
        private int cursor;

        Parser(String text) { this.text = text; }

        Object parse() {
            Object value = value();
            whitespace();
            if (cursor != text.length()) throw new IllegalArgumentException("Trailing JSON content");
            return value;
        }

        private Object value() {
            whitespace();
            if (cursor >= text.length()) throw new IllegalArgumentException("Empty JSON response");
            return switch (text.charAt(cursor)) {
                case '{' -> objectValue();
                case '[' -> arrayValue();
                case '"' -> stringValue();
                case 't' -> literal("true", true);
                case 'f' -> literal("false", false);
                case 'n' -> literal("null", null);
                default -> numberValue();
            };
        }

        private Map<String, Object> objectValue() {
            cursor++;
            Map<String, Object> map = new LinkedHashMap<>();
            whitespace();
            if (take('}')) return map;
            do {
                whitespace();
                String key = stringValue();
                whitespace();
                require(':');
                map.put(key, value());
                whitespace();
            } while (take(','));
            require('}');
            return map;
        }

        private List<Object> arrayValue() {
            cursor++;
            List<Object> list = new ArrayList<>();
            whitespace();
            if (take(']')) return list;
            do {
                list.add(value());
                whitespace();
            } while (take(','));
            require(']');
            return list;
        }

        private String stringValue() {
            require('"');
            StringBuilder result = new StringBuilder();
            while (cursor < text.length()) {
                char current = text.charAt(cursor++);
                if (current == '"') return result.toString();
                if (current != '\\') {
                    result.append(current);
                    continue;
                }
                char escaped = text.charAt(cursor++);
                if (escaped == 'u') {
                    result.append((char) Integer.parseInt(text.substring(cursor, cursor + 4), 16));
                    cursor += 4;
                } else {
                    result.append(switch (escaped) {
                        case '"', '\\', '/' -> escaped;
                        case 'b' -> '\b';
                        case 'f' -> '\f';
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        default -> throw new IllegalArgumentException("Invalid JSON escape");
                    });
                }
            }
            throw new IllegalArgumentException("Unterminated JSON string");
        }

        private Object numberValue() {
            int start = cursor;
            while (cursor < text.length() && "-+0123456789.eE".indexOf(text.charAt(cursor)) >= 0) cursor++;
            String number = text.substring(start, cursor);
            return number.contains(".") || number.contains("e") || number.contains("E")
                    ? Double.parseDouble(number) : Long.parseLong(number);
        }

        private Object literal(String token, Object value) {
            if (!text.startsWith(token, cursor)) throw new IllegalArgumentException("Invalid JSON token");
            cursor += token.length();
            return value;
        }

        private void whitespace() {
            while (cursor < text.length() && Character.isWhitespace(text.charAt(cursor))) cursor++;
        }

        private boolean take(char expected) {
            if (cursor < text.length() && text.charAt(cursor) == expected) { cursor++; return true; }
            return false;
        }

        private void require(char expected) {
            if (!take(expected)) throw new IllegalArgumentException("Expected " + expected);
        }
    }
}
