package net.oculus.gl.blending;

import java.util.Optional;

import org.lwjgl.opengl.GL11;

/**
 * Enum mirror of the Iris alpha test functions. Parsing by name keeps shader
 * pack directives compatible with OptiFine semantics.
 */
public enum AlphaTestFunction {
    NEVER(GL11.GL_NEVER),
    LESS(GL11.GL_LESS),
    EQUAL(GL11.GL_EQUAL),
    LEQUAL(GL11.GL_LEQUAL),
    GREATER(GL11.GL_GREATER),
    NOTEQUAL(GL11.GL_NOTEQUAL),
    GEQUAL(GL11.GL_GEQUAL),
    ALWAYS(GL11.GL_ALWAYS);

    private final int glId;

    AlphaTestFunction(int glId) {
        this.glId = glId;
    }

    public static Optional<AlphaTestFunction> fromString(String name) {
        if ("GL_ALWAYS".equals(name)) {
            return Optional.of(ALWAYS);
        }

        try {
            return Optional.of(AlphaTestFunction.valueOf(name));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public int getGlId() {
        return glId;
    }
}
