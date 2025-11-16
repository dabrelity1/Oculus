package net.oculus.shaderpack;

import net.oculus.Oculus;
import net.oculus.shaderpack.ConstDirectiveParser.ConstDirective;
import net.oculus.vendored.joml.Vector2f;
import net.oculus.vendored.joml.Vector3i;

public final class ComputeDirectiveParser {
    private ComputeDirectiveParser() {
    }

    public static void setComputeWorkGroups(ComputeSource source, ConstDirective directive) {
        String value = directive.getValue();
        if (!value.startsWith("ivec3")) {
            Oculus.LOGGER.error("Failed to process {}: value was not ivec3", directive);
            return;
        }

        String args = stripConstructor(value, "ivec3");
        String[] parts = splitArgs(args, 3, directive);
        if (parts == null) {
            return;
        }

        try {
            source.setWorkGroups(new Vector3i(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2])
            ));
        } catch (NumberFormatException ex) {
            Oculus.LOGGER.error("Failed to parse {}", directive, ex);
        }
    }

    public static void setComputeWorkGroupsRelative(ComputeSource source, ConstDirective directive) {
        String value = directive.getValue();
        if (!value.startsWith("vec2")) {
            Oculus.LOGGER.error("Failed to process {}: value was not vec2", directive);
            return;
        }

        String args = stripConstructor(value, "vec2");
        String[] parts = splitArgs(args, 2, directive);
        if (parts == null) {
            return;
        }

        try {
            source.setWorkGroupRelative(new Vector2f(
                Float.parseFloat(parts[0]),
                Float.parseFloat(parts[1])
            ));
        } catch (NumberFormatException ex) {
            Oculus.LOGGER.error("Failed to parse {}", directive, ex);
        }
    }

    private static String stripConstructor(String value, String keyword) {
        String args = value.substring(keyword.length()).trim();
        if (!args.startsWith("(") || !args.endsWith(")")) {
            throw new IllegalArgumentException("Malformed constructor: " + value);
        }
        return args.substring(1, args.length() - 1);
    }

    private static String[] splitArgs(String args, int expected, ConstDirective directive) {
        String[] parts = args.split(",");
        if (parts.length != expected) {
            Oculus.LOGGER.error("Failed to process {}: expected {} args, got {}", directive, expected, parts.length);
            return null;
        }
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }
        return parts;
    }
}
