package net.oculus.pipeline.vertex;

import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexAttributeFormat;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Constructor;

/**
 * Additional {@link GlVertexAttributeFormat} definitions required by the Oculus vertex layout.
 */
public final class OculusGlVertexAttributeFormats {
    public static final GlVertexAttributeFormat BYTE = create(GL11.GL_BYTE, 1);

    private OculusGlVertexAttributeFormats() {
    }

    private static GlVertexAttributeFormat create(int glId, int size) {
        try {
            Constructor<GlVertexAttributeFormat> ctor = GlVertexAttributeFormat.class.getDeclaredConstructor(int.class, int.class);
            ctor.setAccessible(true);
            return ctor.newInstance(glId, size);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to create custom vertex attribute format", ex);
        }
    }
}
