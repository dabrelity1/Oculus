package net.oculus.shaderpack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parses {@code const} declarations inside shader sources.
 */
public final class ConstDirectiveParser {
    private ConstDirectiveParser() {
    }

    public static List<ConstDirective> findDirectives(String source) {
        List<ConstDirective> directives = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return directives;
        }

        String[] lines = source.split("\\R");
        for (String line : lines) {
            findDirectiveInLine(line).ifPresent(directives::add);
        }
        return directives;
    }

    public static Optional<ConstDirective> findDirectiveInLine(String line) {
        if (line == null || !line.contains("const") || !line.contains("=") || !line.contains(";")) {
            return Optional.empty();
        }

        line = line.trim();
        if (!line.startsWith("const")) {
            return Optional.empty();
        }

        line = line.substring("const".length());
        if (!startsWithWhitespace(line)) {
            return Optional.empty();
        }
        line = line.trim();

        ConstDirective.Type type;
        if (line.startsWith("int")) {
            type = ConstDirective.Type.INT;
            line = line.substring(3);
        } else if (line.startsWith("float")) {
            type = ConstDirective.Type.FLOAT;
            line = line.substring(5);
        } else if (line.startsWith("vec2")) {
            type = ConstDirective.Type.VEC2;
            line = line.substring(4);
        } else if (line.startsWith("ivec3")) {
            type = ConstDirective.Type.IVEC3;
            line = line.substring(5);
        } else if (line.startsWith("vec4")) {
            type = ConstDirective.Type.VEC4;
            line = line.substring(4);
        } else if (line.startsWith("bool")) {
            type = ConstDirective.Type.BOOL;
            line = line.substring(4);
        } else {
            return Optional.empty();
        }

        if (!startsWithWhitespace(line)) {
            return Optional.empty();
        }

        int equalsIndex = line.indexOf('=');
        if (equalsIndex == -1) {
            return Optional.empty();
        }

        String key = line.substring(0, equalsIndex).trim();
        if (!isWord(key)) {
            return Optional.empty();
        }

        String remaining = line.substring(equalsIndex + 1);
        int semicolonIndex = remaining.indexOf(';');
        if (semicolonIndex == -1) {
            return Optional.empty();
        }

        String value = remaining.substring(0, semicolonIndex).trim();
    return Optional.of(new ConstDirective(type, key, value));
    }

    private static boolean startsWithWhitespace(String text) {
        return !text.isEmpty() && Character.isWhitespace(text.charAt(0));
    }

    private static boolean isWord(String text) {
        if (text.isEmpty()) {
            return false;
        }
        for (char c : text.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return false;
            }
        }
        return true;
    }

    public static final class ConstDirective {
        public enum Type {
            INT,
            FLOAT,
            VEC2,
            IVEC3,
            VEC4,
            BOOL
        }

        private final Type type;
        private final String key;
        private final String value;

        ConstDirective(Type type, String key, String value) {
            this.type = type;
            this.key = key;
            this.value = value;
        }

        public Type getType() {
            return type;
        }

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return "ConstDirective { " + type + " " + key + " = " + value + "; }";
        }
    }
}
