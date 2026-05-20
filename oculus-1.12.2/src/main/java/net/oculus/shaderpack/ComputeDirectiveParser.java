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
            Oculus.LOGGER.error("Failed to process {}: value was not a valid ivec3 constructor", directive);
        }

        String[] parts = parseConstructorArgs(directive, "ivec3", 3);

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
            Oculus.LOGGER.error("Failed to process {}: value was not a valid vec2 constructor", directive);
        }

        String[] parts = parseConstructorArgs(directive, "vec2", 2);

        try {
            source.setWorkGroupRelative(new Vector2f(
                Float.parseFloat(parts[0]),
                Float.parseFloat(parts[1])
            ));
        } catch (NumberFormatException ex) {
            Oculus.LOGGER.error("Failed to parse {}", directive, ex);
        }
    }

    private static String[] parseConstructorArgs(ConstDirective directive, String constructor, int expected) {
        String args = directive.getValue().substring(constructor.length()).trim();
        if (!args.startsWith("(") || !args.endsWith(")")) {
            Oculus.LOGGER.error("Failed to process {}: value was not a valid {} constructor", directive, constructor);
        }

        String[] parts = args.substring(1, args.length() - 1).split(",");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }
        if (parts.length != expected) {
            Oculus.LOGGER.error("Failed to process {}: expected {} arguments to a {} constructor, got {}",
                directive, expected, constructor, parts.length);
        }
        return parts;
    }
}
