package net.oculus.shaderpack;

import java.util.Locale;
import java.util.Optional;

/**
 * Parsed OptiFine-style custom uniform or custom variable directive from
 * shaders.properties.
 */
public final class CustomUniformDirective {
    private final ValueType type;
    private final String name;
    private final String expression;

    public CustomUniformDirective(ValueType type, String name, String expression) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("name must not be empty");
        }
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("expression must not be empty");
        }

        this.type = type;
        this.name = name.trim();
        this.expression = expression.trim();
    }

    public ValueType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getExpression() {
        return expression;
    }

    public enum ValueType {
        BOOL,
        INT,
        FLOAT,
        VEC2,
        VEC3,
        VEC4;

        public static Optional<ValueType> fromPropertyToken(String token) {
            if (token == null) {
                return Optional.empty();
            }

            switch (token.trim().toLowerCase(Locale.ROOT)) {
                case "bool":
                    return Optional.of(BOOL);
                case "int":
                    return Optional.of(INT);
                case "float":
                    return Optional.of(FLOAT);
                case "vec2":
                    return Optional.of(VEC2);
                case "vec3":
                    return Optional.of(VEC3);
                case "vec4":
                    return Optional.of(VEC4);
                default:
                    return Optional.empty();
            }
        }
    }
}
