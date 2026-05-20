package net.oculus.gl.texture;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public class DepthBufferFormatTest {
    @Test
    public void depthFormatsExposeTexImagePixelFormatAndTypeSeparately() {
        assertEquals(GL11.GL_DEPTH_COMPONENT, DepthBufferFormat.DEPTH.getGlPixelFormat());
        assertEquals(GL11.GL_UNSIGNED_SHORT, DepthBufferFormat.DEPTH.getGlPixelType());
        assertEquals(GL30.GL_DEPTH_STENCIL, DepthBufferFormat.DEPTH24_STENCIL8.getGlPixelFormat());
        assertEquals(GL30.GL_UNSIGNED_INT_24_8, DepthBufferFormat.DEPTH24_STENCIL8.getGlPixelType());
    }

    @Test
    public void legacyDepthAccessorsDelegateToTheNamedTexImageValues() {
        assertEquals(DepthBufferFormat.DEPTH32F.getGlPixelFormat(), DepthBufferFormat.DEPTH32F.getGlType());
        assertEquals(DepthBufferFormat.DEPTH32F.getGlPixelType(), DepthBufferFormat.DEPTH32F.getGlFormat());
    }
}
