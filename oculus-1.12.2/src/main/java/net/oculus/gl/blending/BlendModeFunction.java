package net.oculus.gl.blending;

import java.util.Optional;

import org.lwjgl.opengl.GL11;

import net.oculus.Oculus;

/**
 * Matches the OptiFine blend mode tokens used inside shaders.properties.
 */
public enum BlendModeFunction {
    ZERO(GL11.GL_ZERO),
    ONE(GL11.GL_ONE),
    SRC_COLOR(GL11.GL_SRC_COLOR),
    ONE_MINUS_SRC_COLOR(GL11.GL_ONE_MINUS_SRC_COLOR),
    DST_COLOR(GL11.GL_DST_COLOR),
    ONE_MINUS_DST_COLOR(GL11.GL_ONE_MINUS_DST_COLOR),
    SRC_ALPHA(GL11.GL_SRC_ALPHA),
    ONE_MINUS_SRC_ALPHA(GL11.GL_ONE_MINUS_SRC_ALPHA),
    DST_ALPHA(GL11.GL_DST_ALPHA),
    ONE_MINUS_DST_ALPHA(GL11.GL_ONE_MINUS_DST_ALPHA),
    SRC_ALPHA_SATURATE(GL11.GL_SRC_ALPHA_SATURATE);

    private final int glId;

    BlendModeFunction(int glId) {
        this.glId = glId;
    }

    public static Optional<BlendModeFunction> fromString(String name) {
        try {
            return Optional.of(BlendModeFunction.valueOf(name));
        } catch (IllegalArgumentException ex) {
            Oculus.LOGGER.warn("Invalid blend mode token '{}'", name);
            return Optional.empty();
        }
    }

    public int getGlId() {
        return glId;
    }
}
