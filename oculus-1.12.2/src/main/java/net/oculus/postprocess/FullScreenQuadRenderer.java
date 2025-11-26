package net.oculus.postprocess;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/**
 * Renders a full-screen quad for post-processing passes.
 * This is used by composite and final passes to draw their output
 * to the screen or to other render targets.
 */
public final class FullScreenQuadRenderer {
    public static final FullScreenQuadRenderer INSTANCE = new FullScreenQuadRenderer();

    private FullScreenQuadRenderer() {
    }

    /**
     * Sets up OpenGL state for fullscreen rendering.
     * Call this before rendering multiple quads.
     */
    public void begin() {
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.ortho(-1.0, 1.0, -1.0, 1.0, -1.0, 1.0);
        
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
    }

    /**
     * Restores OpenGL state after fullscreen rendering.
     */
    public static void end() {
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.popMatrix();
        
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.popMatrix();
        
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
    }

    /**
     * Renders a fullscreen quad covering clip space [-1, 1].
     * The quad has UV coordinates from [0, 0] to [1, 1].
     */
    public void renderQuad() {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(-1.0, -1.0, 0.0).tex(0.0, 0.0).endVertex();
        buffer.pos(1.0, -1.0, 0.0).tex(1.0, 0.0).endVertex();
        buffer.pos(1.0, 1.0, 0.0).tex(1.0, 1.0).endVertex();
        buffer.pos(-1.0, 1.0, 0.0).tex(0.0, 1.0).endVertex();
        tessellator.draw();
    }

    /**
     * Convenience method to render a fullscreen quad with begin/end.
     */
    public void render() {
        begin();
        renderQuad();
        end();
    }
}
