package net.oculus.shaderpack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import net.oculus.Oculus;
import net.oculus.shaderpack.ConstDirectiveParser.ConstDirective;
import net.oculus.shaderpack.ConstDirectiveParser.ConstDirective.Type;
import net.oculus.shaderpack.directives.DirectiveHolder;
import net.oculus.vendored.joml.Vector2f;
import net.oculus.vendored.joml.Vector3i;
import net.oculus.vendored.joml.Vector4f;

/**
 * Routes directives discovered inside shader sources to registered consumers.
 */
public final class DispatchingDirectiveHolder implements DirectiveHolder {
    private final Map<String, Consumer<Boolean>> booleanConstVariables = new HashMap<>();
    private final Map<String, Consumer<String>> stringConstVariables = new HashMap<>();
    private final Map<String, IntConsumer> intConstVariables = new HashMap<>();
    private final Map<String, Consumer<Float>> floatConstVariables = new HashMap<>();
    private final Map<String, Consumer<Vector2f>> vec2ConstVariables = new HashMap<>();
    private final Map<String, Consumer<Vector3i>> ivec3ConstVariables = new HashMap<>();
    private final Map<String, Consumer<Vector4f>> vec4ConstVariables = new HashMap<>();

    public void processDirective(ConstDirective directive) {
        Type type = directive.getType();
        String key = directive.getKey();
        String value = directive.getValue();

        switch (type) {
            case BOOL:
                Consumer<Boolean> boolConsumer = booleanConstVariables.get(key);
                if (boolConsumer != null) {
                    if ("true".equals(value)) {
                        boolConsumer.accept(true);
                    } else if ("false".equals(value)) {
                        boolConsumer.accept(false);
                    } else {
                        Oculus.LOGGER.error("Failed to process {}: '{}' is not boolean", directive, value);
                    }
                }
                break;
            case INT:
                Consumer<String> stringConsumer = stringConstVariables.get(key);
                if (stringConsumer != null) {
                    stringConsumer.accept(value);
                    break;
                }
                IntConsumer intConsumer = intConstVariables.get(key);
                if (intConsumer != null) {
                    try {
                        intConsumer.accept(Integer.parseInt(value));
                    } catch (NumberFormatException ex) {
                        Oculus.LOGGER.error("Failed to process {}", directive, ex);
                    }
                }
                break;
            case FLOAT:
                Consumer<Float> floatConsumer = floatConstVariables.get(key);
                if (floatConsumer != null) {
                    try {
                        floatConsumer.accept(Float.parseFloat(value));
                    } catch (NumberFormatException ex) {
                        Oculus.LOGGER.error("Failed to process {}", directive, ex);
                    }
                }
                break;
            case VEC2:
                Consumer<Vector2f> vec2Consumer = vec2ConstVariables.get(key);
                if (vec2Consumer != null) {
                    try {
                        vec2Consumer.accept(parseVec2(directive));
                    } catch (NumberFormatException ex) {
                        Oculus.LOGGER.error("Failed to process {}", directive, ex);
                    }
                }
                break;
            case IVEC3:
                Consumer<Vector3i> vec3Consumer = ivec3ConstVariables.get(key);
                if (vec3Consumer != null) {
                    try {
                        vec3Consumer.accept(parseIVec3(directive));
                    } catch (NumberFormatException ex) {
                        Oculus.LOGGER.error("Failed to process {}", directive, ex);
                    }
                }
                break;
            case VEC4:
                Consumer<Vector4f> vec4Consumer = vec4ConstVariables.get(key);
                if (vec4Consumer != null) {
                    try {
                        vec4Consumer.accept(parseVec4(directive));
                    } catch (NumberFormatException ex) {
                        Oculus.LOGGER.error("Failed to process {}", directive, ex);
                    }
                }
                break;
        }
    }

    private static Vector2f parseVec2(ConstDirective directive) {
        String[] parts = parseConstructorArgs(directive, "vec2", 2);
        return new Vector2f(Float.parseFloat(parts[0]), Float.parseFloat(parts[1]));
    }

    private static Vector3i parseIVec3(ConstDirective directive) {
        String[] parts = parseConstructorArgs(directive, "ivec3", 3);
        return new Vector3i(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    private static Vector4f parseVec4(ConstDirective directive) {
        String[] parts = parseConstructorArgs(directive, "vec4", 4);
        return new Vector4f(Float.parseFloat(parts[0]), Float.parseFloat(parts[1]),
            Float.parseFloat(parts[2]), Float.parseFloat(parts[3]));
    }

    private static String[] parseConstructorArgs(ConstDirective directive, String constructor, int expected) {
        String value = directive.getValue();
        if (!value.startsWith(constructor)) {
            Oculus.LOGGER.error("Failed to process {}: value was not a valid {} constructor", directive, constructor);
        }

        String args = value.substring(constructor.length()).trim();
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

    @Override
    public void acceptConstBooleanDirective(String name, Consumer<Boolean> consumer) {
        booleanConstVariables.put(name, consumer);
    }

    @Override
    public void acceptConstStringDirective(String name, Consumer<String> consumer) {
        stringConstVariables.put(name, consumer);
    }

    @Override
    public void acceptConstIntDirective(String name, IntConsumer consumer) {
        intConstVariables.put(name, consumer);
    }

    @Override
    public void acceptConstFloatDirective(String name, Consumer<Float> consumer) {
        floatConstVariables.put(name, consumer);
    }

    @Override
    public void acceptConstVec2Directive(String name, Consumer<Vector2f> consumer) {
        vec2ConstVariables.put(name, consumer);
    }

    @Override
    public void acceptConstIVec3Directive(String name, Consumer<Vector3i> consumer) {
        ivec3ConstVariables.put(name, consumer);
    }

    @Override
    public void acceptConstVec4Directive(String name, Consumer<Vector4f> consumer) {
        vec4ConstVariables.put(name, consumer);
    }
}
